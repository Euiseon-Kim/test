package com.example.ozr.service;

import com.example.ozr.config.AppProperties;
import com.example.ozr.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("unchecked")
public class GitLabService {

    private final AppProperties appProperties;
    private final RestTemplate restTemplate;

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        // 토큰은 헤더에만 사용하고 절대 로그에 출력하지 않음
        headers.set("PRIVATE-TOKEN", appProperties.getGitlab().getBotToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /**
     * GitLab에서 파일 내용을 조회한다.
     */
    public byte[] getFileContent(String projectId, String filePath, String branch) {
        String encodedPath = URLEncoder.encode(filePath, StandardCharsets.UTF_8);
        String url = appProperties.getGitlab().getApiUrl()
                + "/projects/" + projectId
                + "/repository/files/" + encodedPath
                + "?ref=" + branch;

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers()), Map.class);
            String base64Content = (String) response.getBody().get("content");
            return Base64.getDecoder().decode(base64Content.replaceAll("\\s+", ""));
        } catch (HttpClientErrorException.NotFound e) {
            throw new BusinessException("GitLab에서 파일을 찾을 수 없습니다: " + filePath);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("GitLab 파일 조회 실패: projectId={}, filePath={}", projectId, filePath);
            throw new BusinessException("GitLab 파일 조회에 실패했습니다.");
        }
    }

    /**
     * GitLab에 새 브랜치를 생성한다.
     */
    public void createBranch(String projectId, String branchName, String ref) {
        String url = appProperties.getGitlab().getApiUrl()
                + "/projects/" + projectId + "/repository/branches";

        Map<String, String> body = new HashMap<>();
        body.put("branch", branchName);
        body.put("ref", ref);

        try {
            restTemplate.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(body, headers()), Map.class);
        } catch (Exception e) {
            log.error("GitLab 브랜치 생성 실패: projectId={}, branch={}", projectId, branchName);
            throw new BusinessException("GitLab 브랜치 생성에 실패했습니다.");
        }
    }

    /**
     * GitLab Commits API로 파일을 update 커밋한다. base64 encoding 사용.
     * 파일이 없으면 create, 있으면 update로 자동 판단한다.
     */
    public String commitFile(String projectId, String branchName, String filePath,
                             byte[] content, String commitMessage) {
        String url = appProperties.getGitlab().getApiUrl()
                + "/projects/" + projectId + "/repository/commits";

        String base64Content = Base64.getEncoder().encodeToString(content);
        String action = fileExists(projectId, filePath, branchName) ? "update" : "create";

        Map<String, Object> actionMap = new HashMap<>();
        actionMap.put("action", action);
        actionMap.put("file_path", filePath);
        actionMap.put("content", base64Content);
        actionMap.put("encoding", "base64");

        Map<String, Object> body = new HashMap<>();
        body.put("branch", branchName);
        body.put("commit_message", commitMessage);
        body.put("actions", List.of(actionMap));

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers()), Map.class);
            return (String) response.getBody().get("id");
        } catch (Exception e) {
            log.error("GitLab 파일 커밋 실패: projectId={}, branch={}, filePath={}",
                    projectId, branchName, filePath);
            throw new BusinessException("GitLab 파일 커밋에 실패했습니다.");
        }
    }

    private boolean fileExists(String projectId, String filePath, String branch) {
        try {
            getFileContent(projectId, filePath, branch);
            return true;
        } catch (BusinessException e) {
            return false;
        }
    }

    /**
     * GitLab 연결 및 프로젝트 접근 권한을 검증한다.
     */
    public Map<String, Object> getProject(String projectId) {
        String url = appProperties.getGitlab().getApiUrl() + "/projects/" + projectId;
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers()), Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("GitLab 프로젝트 조회 실패: projectId={}", projectId);
            throw new BusinessException("GitLab 연결에 실패했습니다. 설정을 확인하세요.");
        }
    }
}
