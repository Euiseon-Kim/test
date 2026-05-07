package com.example.ozr.controller;

import com.example.ozr.config.AppProperties;
import com.example.ozr.dto.CommitResponse;
import com.example.ozr.dto.ValidationResult;
import com.example.ozr.exception.BusinessException;
import com.example.ozr.model.AuditLog;
import com.example.ozr.model.User;
import com.example.ozr.repository.UserRepository;
import com.example.ozr.service.AuditService;
import com.example.ozr.service.GitLabService;
import com.example.ozr.service.OzrSanitizeService;
import com.example.ozr.service.OzrValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@RestController
@RequestMapping("/api/ozr")
@RequiredArgsConstructor
public class OzrController {

    private final OzrValidationService validationService;
    private final OzrSanitizeService sanitizeService;
    private final GitLabService gitLabService;
    private final AuditService auditService;
    private final AppProperties appProperties;
    private final UserRepository userRepository;

    /**
     * .ozr 파일 검증 API
     * POST /api/ozr/validate
     */
    @PostMapping(value = "/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ValidationResult> validate(
            @RequestParam String projectId,
            @RequestParam String filePath,
            @RequestParam MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {

        AppProperties.ProjectConfig project = resolveProject(projectId);
        ValidationResult result = validationService.validate(
                projectId, filePath, file, project.getDefaultBranch());
        return ResponseEntity.ok(result);
    }

    /**
     * .ozr 파일 정제 API - 정제본 다운로드
     * POST /api/ozr/sanitize
     */
    @PostMapping(value = "/sanitize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> sanitize(
            @RequestParam String projectId,
            @RequestParam String filePath,
            @RequestParam MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (!appProperties.isAllowedFile(projectId, filePath)) {
            throw new BusinessException("허용되지 않은 projectId 또는 filePath입니다.");
        }

        byte[] sanitized;
        try {
            sanitized = sanitizeService.sanitize(file.getBytes());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("파일 정제 처리에 실패했습니다.");
        }

        String filename = extractFilename(filePath);

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(sanitized);
    }

    /**
     * GitLab 원본 파일 다운로드
     * GET /api/ozr/download?projectId=&filePath=
     */
    @GetMapping("/download")
    public ResponseEntity<byte[]> download(
            @RequestParam String projectId,
            @RequestParam String filePath,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (!appProperties.isAllowedFile(projectId, filePath)) {
            throw new BusinessException("허용되지 않은 projectId 또는 filePath입니다.");
        }

        AppProperties.ProjectConfig project = resolveProject(projectId);
        byte[] content = gitLabService.getFileContent(projectId, filePath, project.getDefaultBranch());
        String filename = extractFilename(filePath);

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(content);
    }

    /**
     * GitLab 커밋 API
     * POST /api/ozr/commit
     */
    @PostMapping(value = "/commit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CommitResponse> commit(
            @RequestParam String projectId,
            @RequestParam String filePath,
            @RequestParam String changeReason,
            @RequestParam MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {

        // frontend의 requester 값을 신뢰하지 않고 JWT에서 추출한 userId 사용
        String userId = userDetails.getUsername();
        User user = userRepository.findByUserIdAndActiveTrue(userId)
                .orElseThrow(() -> new BusinessException("사용자 정보를 찾을 수 없습니다."));

        AppProperties.ProjectConfig project = resolveProject(projectId);
        String sourceBranch = project.getDefaultBranch();

        // commit 직전 반드시 validation 재수행
        ValidationResult validationResult = validationService.validate(
                projectId, filePath, file, sourceBranch);

        if (!validationResult.isCanCommit()) {
            saveAuditLog(user, projectId, project.getProjectPath(), filePath,
                    sourceBranch, null, null,
                    "FAIL", changeReason, false, "검증 실패로 커밋 거부");
            throw new BusinessException("파일 검증에 실패하여 커밋할 수 없습니다.");
        }

        // 브랜치명: biz/{userId}/{yyyyMMdd-HHmmss}
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String newBranch = "biz/" + userId + "/" + timestamp;

        String commitId;
        try {
            byte[] fileBytes = file.getBytes();

            gitLabService.createBranch(projectId, newBranch, sourceBranch);

            String commitMessage = buildCommitMessage(user, filePath, changeReason);
            commitId = gitLabService.commitFile(projectId, newBranch, filePath, fileBytes, commitMessage);

            log.info("커밋 완료: user={}, project={}, branch={}, commitId={}",
                    userId, projectId, newBranch, commitId);
        } catch (BusinessException e) {
            saveAuditLog(user, projectId, project.getProjectPath(), filePath,
                    sourceBranch, newBranch, null,
                    "PASS", changeReason, false, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("커밋 처리 중 예외 발생: user={}, project={}", userId, projectId, e);
            saveAuditLog(user, projectId, project.getProjectPath(), filePath,
                    sourceBranch, newBranch, null,
                    "PASS", changeReason, false, "커밋 처리 실패");
            throw new BusinessException("GitLab 커밋 처리에 실패했습니다.");
        }

        saveAuditLog(user, projectId, project.getProjectPath(), filePath,
                sourceBranch, newBranch, commitId,
                "PASS", changeReason, true, null);

        return ResponseEntity.ok(CommitResponse.builder()
                .success(true)
                .branch(newBranch)
                .commitId(commitId)
                .message("커밋이 완료되었습니다.")
                .build());
    }

    private AppProperties.ProjectConfig resolveProject(String projectId) {
        AppProperties.ProjectConfig project = appProperties.findProject(projectId);
        if (project == null) {
            throw new BusinessException("존재하지 않는 프로젝트입니다: " + projectId);
        }
        return project;
    }

    private String buildCommitMessage(User user, String filePath, String changeReason) {
        return "[BIZ-OZR] .ozr 파일 변경 요청\n\n" +
                "Requester: " + user.getName() + "\n" +
                "Requester ID: " + user.getUserId() + "\n" +
                "Department: " + user.getDepartment() + "\n" +
                "File: " + filePath + "\n" +
                "Reason: " + changeReason;
    }

    private void saveAuditLog(User user, String projectId, String projectPath,
                               String filePath, String sourceBranch, String createdBranch,
                               String commitId, String validationStatus, String changeReason,
                               boolean success, String errorMessage) {
        auditService.save(AuditLog.builder()
                .requesterId(user.getUserId())
                .requesterName(user.getName())
                .department(user.getDepartment())
                .projectId(projectId)
                .projectPath(projectPath)
                .filePath(filePath)
                .sourceBranch(sourceBranch)
                .createdBranch(createdBranch)
                .commitId(commitId)
                .validationStatus(validationStatus)
                .changeReason(changeReason)
                .timestamp(LocalDateTime.now())
                .success(success)
                .errorMessage(errorMessage)
                .build());
    }

    private String extractFilename(String filePath) {
        int slash = filePath.lastIndexOf('/');
        return slash >= 0 ? filePath.substring(slash + 1) : filePath;
    }
}
