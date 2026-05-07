package com.example.ozr.service;

import com.example.ozr.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Slf4j
@Service
@RequiredArgsConstructor
public class OzrSanitizeService {

    private static final byte[] UTF8_BOM_BYTES = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final int BOM_CODEPOINT = 0xFEFF;

    /**
     * 파일 바이트에서 유효하지 않은 문자를 제거한 정제본을 반환한다.
     * NULL, 제어문자(tab/LF/CR 제외), zero-width, XML1.0 invalid, 중간 BOM을 제거한다.
     */
    public byte[] sanitize(byte[] fileBytes) {
        String content;
        try {
            content = new String(fileBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException("UTF-8 디코딩에 실패하여 정제할 수 없습니다.");
        }

        // 첫 번째 문자가 BOM인지 확인 (선두 BOM은 유지)
        boolean hasLeadingBom = content.length() > 0 && content.charAt(0) == (char) BOM_CODEPOINT;
        int startIdx = hasLeadingBom ? 1 : 0;

        StringBuilder sb = new StringBuilder();
        if (hasLeadingBom) {
            sb.append((char) BOM_CODEPOINT);
        }

        for (int i = startIdx; i < content.length(); ) {
            int cp = content.codePointAt(i);
            int charCount = Character.charCount(cp);

            // NULL 문자 제거
            if (cp == 0x0000) {
                i += charCount;
                continue;
            }

            // 허용되지 않는 ASCII 제어 문자 제거 (tab=0x09, LF=0x0A, CR=0x0D는 허용)
            if ((cp >= 0x01 && cp <= 0x08) || cp == 0x0B || cp == 0x0C
                    || (cp >= 0x0E && cp <= 0x1F)) {
                i += charCount;
                continue;
            }

            // Zero-width 문자 제거 (U+200B, U+200C, U+200D)
            if (cp == 0x200B || cp == 0x200C || cp == 0x200D) {
                i += charCount;
                continue;
            }

            // 중간 BOM 제거 (U+FEFF, 선두 이후)
            if (cp == BOM_CODEPOINT) {
                i += charCount;
                continue;
            }

            // XML 1.0 invalid 문자 제거 (U+FFFE, U+FFFF, surrogate)
            if (cp == 0xFFFE || cp == 0xFFFF || (cp >= 0xD800 && cp <= 0xDFFF)) {
                i += charCount;
                continue;
            }

            sb.appendCodePoint(cp);
            i += charCount;
        }

        byte[] sanitized = sb.toString().getBytes(StandardCharsets.UTF_8);

        // 정제 후 XML 파싱 재검증
        validateXmlAfterSanitize(sanitized);

        return sanitized;
    }

    private void validateXmlAfterSanitize(byte[] bytes) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            byte[] parseTarget = bytes;
            if (bytes.length >= 3
                    && bytes[0] == UTF8_BOM_BYTES[0]
                    && bytes[1] == UTF8_BOM_BYTES[1]
                    && bytes[2] == UTF8_BOM_BYTES[2]) {
                parseTarget = Arrays.copyOfRange(bytes, 3, bytes.length);
            }

            factory.newDocumentBuilder().parse(new ByteArrayInputStream(parseTarget));
        } catch (SAXParseException e) {
            throw new BusinessException(
                    "정제 후에도 XML 파싱 오류가 발생합니다. 파일을 직접 수정해야 합니다.");
        } catch (Exception e) {
            throw new BusinessException("정제 후 XML 검증에 실패했습니다.");
        }
    }
}
