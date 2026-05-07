package com.example.ozr.service;

import com.example.ozr.config.AppProperties;
import com.example.ozr.dto.ValidationIssue;
import com.example.ozr.dto.ValidationResult;
import com.example.ozr.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OzrValidationService {

    private final AppProperties appProperties;
    private final GitLabService gitLabService;

    // Zero-width 문자 코드포인트
    private static final int[] ZERO_WIDTH_CODEPOINTS = {0x200B, 0x200C, 0x200D};

    // UTF-8 BOM 바이트 시퀀스
    private static final byte[] UTF8_BOM_BYTES = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    // BOM 유니코드 코드포인트 (U+FEFF)
    private static final int BOM_CODEPOINT = 0xFEFF;

    public ValidationResult validate(String projectId, String filePath,
                                     MultipartFile file, String defaultBranch) {
        List<ValidationIssue> issues = new ArrayList<>();

        // 1. 확장자 검증
        if (!filePath.toLowerCase().endsWith(".ozr")) {
            issues.add(ValidationIssue.builder()
                    .type("INVALID_EXTENSION")
                    .message("파일 확장자가 .ozr이 아닙니다.")
                    .count(1)
                    .build());
        }

        // 2. Allowlist 검증 (path traversal 방지 포함)
        if (!appProperties.isAllowedFile(projectId, filePath)) {
            throw new BusinessException("허용되지 않은 projectId 또는 filePath입니다.");
        }

        // 3. 파일 크기 검증
        long fileSize = file.getSize();
        if (fileSize > appProperties.getMaxFileSizeBytes()) {
            issues.add(ValidationIssue.builder()
                    .type("FILE_TOO_LARGE")
                    .message(String.format("파일 크기(%,d bytes)가 허용 한도(%,d bytes)를 초과합니다.",
                            fileSize, appProperties.getMaxFileSizeBytes()))
                    .count(1)
                    .build());
            return buildFinalResult(issues, false);
        }

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (Exception e) {
            throw new BusinessException("파일 읽기에 실패했습니다.");
        }

        // 4. UTF-8 디코딩 검증
        String content;
        try {
            content = decodeUtf8Strict(fileBytes);
        } catch (CharacterCodingException e) {
            issues.add(ValidationIssue.builder()
                    .type("UTF8_DECODE_ERROR")
                    .message("UTF-8 디코딩에 실패했습니다. 파일 인코딩을 확인하세요.")
                    .count(1)
                    .build());
            return buildFinalResult(issues, false);
        }

        // 5~10. 문자 수준 검증
        checkCharacters(content, issues);

        // 11. XML 파싱 검증 (문자 오류가 없을 때만)
        boolean hasCharIssues = issues.stream().anyMatch(i ->
                i.getType().equals("NULL_CHARACTER") ||
                i.getType().equals("INVALID_CONTROL_CHARACTER") ||
                i.getType().equals("XML10_INVALID_CHARACTER"));
        if (!hasCharIssues) {
            checkXmlParsing(fileBytes, issues);
        }

        // 12. GitLab 원본과 hash 비교
        boolean changed = compareWithGitLab(projectId, filePath, defaultBranch, fileBytes, issues);

        return buildFinalResult(issues, changed);
    }

    private String decodeUtf8Strict(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    private void checkCharacters(String content, List<ValidationIssue> issues) {
        int nullCount = 0, controlCount = 0, zeroWidthCount = 0, xml10InvalidCount = 0, bomInMiddleCount = 0;
        Integer nullLine = null, controlLine = null, zeroWidthLine = null, xml10InvalidLine = null, bomInMiddleLine = null;

        // 첫 번째 문자가 BOM이면 선두 BOM으로 허용
        int startIdx = (content.length() > 0 && content.charAt(0) == (char) BOM_CODEPOINT) ? 1 : 0;

        int lineNum = 1;
        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            int cp = Character.codePointAt(content, i);

            if (ch == '\n') {
                lineNum++;
                continue;
            }

            // NULL 문자 (U+0000)
            if (cp == 0x0000) {
                nullCount++;
                if (nullLine == null) nullLine = lineNum;
            }

            // ASCII 제어 문자 (tab=0x09, LF=0x0A, CR=0x0D 제외)
            if ((cp >= 0x01 && cp <= 0x08) || cp == 0x0B || cp == 0x0C
                    || (cp >= 0x0E && cp <= 0x1F)) {
                controlCount++;
                if (controlLine == null) controlLine = lineNum;
            }

            // Zero-width 문자 (U+200B, U+200C, U+200D)
            if (cp == 0x200B || cp == 0x200C || cp == 0x200D) {
                zeroWidthCount++;
                if (zeroWidthLine == null) zeroWidthLine = lineNum;
            }

            // BOM 문서 중간 존재 여부 (U+FEFF, 첫 번째 이후)
            if (cp == BOM_CODEPOINT && i >= startIdx) {
                bomInMiddleCount++;
                if (bomInMiddleLine == null) bomInMiddleLine = lineNum;
            }

            // XML 1.0 invalid: #xFFFE, #xFFFF, surrogate pair range #xD800-#xDFFF
            if (cp == 0xFFFE || cp == 0xFFFF || (cp >= 0xD800 && cp <= 0xDFFF)) {
                xml10InvalidCount++;
                if (xml10InvalidLine == null) xml10InvalidLine = lineNum;
            }
        }

        if (nullCount > 0) {
            issues.add(ValidationIssue.builder()
                    .type("NULL_CHARACTER")
                    .message("NULL 문자(U+0000)가 포함되어 있습니다.")
                    .count(nullCount).line(nullLine).build());
        }
        if (controlCount > 0) {
            issues.add(ValidationIssue.builder()
                    .type("INVALID_CONTROL_CHARACTER")
                    .message("허용되지 않은 ASCII 제어 문자(tab/LF/CR 제외)가 포함되어 있습니다.")
                    .count(controlCount).line(controlLine).build());
        }
        if (zeroWidthCount > 0) {
            issues.add(ValidationIssue.builder()
                    .type("ZERO_WIDTH_CHARACTER")
                    .message("제로-폭 문자(U+200B/200C/200D)가 포함되어 있습니다.")
                    .count(zeroWidthCount).line(zeroWidthLine).build());
        }
        if (bomInMiddleCount > 0) {
            issues.add(ValidationIssue.builder()
                    .type("BOM_IN_MIDDLE")
                    .message("문서 중간에 BOM(U+FEFF)이 포함되어 있습니다.")
                    .count(bomInMiddleCount).line(bomInMiddleLine).build());
        }
        if (xml10InvalidCount > 0) {
            issues.add(ValidationIssue.builder()
                    .type("XML10_INVALID_CHARACTER")
                    .message("XML 1.0 규격에서 허용되지 않는 문자(U+FFFE, U+FFFF, 서로게이트 등)가 포함되어 있습니다.")
                    .count(xml10InvalidCount).line(xml10InvalidLine).build());
        }
    }

    private void checkXmlParsing(byte[] fileBytes, List<ValidationIssue> issues) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // XXE 방지
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            // BOM이 있으면 제거 후 파싱
            byte[] parseTarget = fileBytes;
            if (fileBytes.length >= 3
                    && fileBytes[0] == UTF8_BOM_BYTES[0]
                    && fileBytes[1] == UTF8_BOM_BYTES[1]
                    && fileBytes[2] == UTF8_BOM_BYTES[2]) {
                parseTarget = Arrays.copyOfRange(fileBytes, 3, fileBytes.length);
            }

            factory.newDocumentBuilder().parse(new ByteArrayInputStream(parseTarget));
        } catch (SAXParseException e) {
            issues.add(ValidationIssue.builder()
                    .type("XML_PARSE_ERROR")
                    .message("XML 파싱 오류: " + sanitizeExceptionMessage(e.getMessage()))
                    .count(1)
                    .line(e.getLineNumber() > 0 ? e.getLineNumber() : null)
                    .build());
        } catch (Exception e) {
            issues.add(ValidationIssue.builder()
                    .type("XML_PARSE_ERROR")
                    .message("XML 파싱에 실패했습니다.")
                    .count(1)
                    .build());
        }
    }

    private boolean compareWithGitLab(String projectId, String filePath, String defaultBranch,
                                      byte[] fileBytes, List<ValidationIssue> issues) {
        try {
            byte[] originalBytes = gitLabService.getFileContent(projectId, filePath, defaultBranch);
            boolean changed = !Arrays.equals(sha256(fileBytes), sha256(originalBytes));
            if (!changed) {
                issues.add(ValidationIssue.builder()
                        .type("NO_CHANGE")
                        .message("업로드된 파일이 GitLab 원본 파일과 동일합니다. 변경 사항이 없습니다.")
                        .count(1)
                        .build());
            }
            return changed;
        } catch (BusinessException e) {
            log.warn("GitLab 원본 파일 조회 불가 - hash 비교 생략: {}", e.getMessage());
            return true;
        }
    }

    private ValidationResult buildFinalResult(List<ValidationIssue> issues, boolean changed) {
        boolean hasBlockingIssues = issues.stream()
                .anyMatch(i -> !i.getType().equals("NO_CHANGE"));
        boolean sanitizedAvailable = issues.stream()
                .anyMatch(i -> isSanitizable(i.getType()));

        String status = (!hasBlockingIssues && changed) ? "PASS" : "FAIL";
        boolean canCommit = "PASS".equals(status);

        return ValidationResult.builder()
                .status(status)
                .canCommit(canCommit)
                .issues(issues)
                .changed(changed)
                .sanitizedAvailable(sanitizedAvailable)
                .build();
    }

    private boolean isSanitizable(String type) {
        return switch (type) {
            case "NULL_CHARACTER",
                 "INVALID_CONTROL_CHARACTER",
                 "ZERO_WIDTH_CHARACTER",
                 "XML10_INVALID_CHARACTER",
                 "BOM_IN_MIDDLE" -> true;
            default -> false;
        };
    }

    private byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 해시 계산 실패", e);
        }
    }

    private String sanitizeExceptionMessage(String msg) {
        if (msg == null) return "알 수 없는 오류";
        // 예외 메시지에 민감 정보가 포함될 수 있으므로 길이 제한
        return msg.length() > 200 ? msg.substring(0, 200) : msg;
    }
}
