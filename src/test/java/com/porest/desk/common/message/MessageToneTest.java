package com.porest.desk.common.message;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 서버가 사용자에게 내보내는 한국어 문구는 앱·웹과 같은 말투(`~어요`)를 쓴다(QA 2026-09-03 #72).
 *
 * <p>이 규칙은 <b>사람이 지킬 수 없다</b>. 문구가 두 곳에 흩어져 있고(메시지 번들 · DTO 검증
 * 애노테이션의 하드코딩 message), 새 문구는 대개 주변을 복사해서 만든다 — 종전에도 "올바른
 * 날짜가 아니에요" 한 줄만 새 말투였고 옆줄은 전부 "…입력할 수 있습니다" 였다. 그래서 규칙을
 * 문서가 아니라 테스트로 둔다.
 *
 * <p>거꾸로 <b>영어 번들에 한국어가 새는 것</b>도 같이 막는다. 기본 번들(messages.properties)은
 * ko 가 아닌 로케일이 보는 자리인데 한국어 값 5개가 섞여 있었다 — 아무도 안 봐서 오래 남았다.
 */
class MessageToneTest {

    /** 격식체 종결. 이게 남아 있으면 앱·웹 문구와 말투가 갈린다. */
    private static final Pattern FORMAL = Pattern.compile("습니다|합니다|입니다|하십시오|하시오");

    private static final Pattern HANGUL = Pattern.compile("[가-힣]");

    /** DTO 검증 애노테이션의 하드코딩 문구: {@code message = "..."}. */
    private static final Pattern ANNOTATION_MESSAGE = Pattern.compile("message = \"([^\"]*)\"");

    private static final Path MESSAGE_DIR = Path.of("src/main/resources/message");
    private static final Path JAVA_DIR = Path.of("src/main/java");

    @Test
    @DisplayName("messages_ko 의 모든 문구가 `~어요` 말투다")
    void koreanBundleUsesProductTone() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String line : Files.readAllLines(MESSAGE_DIR.resolve("messages_ko.properties"), StandardCharsets.UTF_8)) {
            String value = valueOf(line);
            if (value != null && FORMAL.matcher(value).find()) offenders.add(line);
        }

        assertThat(offenders)
                .as("격식체(습니다·합니다·입니다)가 남았다 — 앱·웹은 `~어요` 로 말한다")
                .isEmpty();
    }

    @Test
    @DisplayName("DTO 검증 애노테이션의 문구도 같은 말투다")
    void validationAnnotationsUseProductTone() throws IOException {
        assertThat(JAVA_DIR).as("테스트 작업 디렉토리가 프로젝트 루트여야 한다").isDirectory();

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(JAVA_DIR)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher m = ANNOTATION_MESSAGE.matcher(Files.readString(f, StandardCharsets.UTF_8));
                while (m.find()) {
                    String value = m.group(1);
                    if (HANGUL.matcher(value).find() && FORMAL.matcher(value).find()) {
                        offenders.add(JAVA_DIR.relativize(f) + " → " + value);
                    }
                }
            }
        }

        assertThat(offenders).as("DTO 검증 문구에 격식체가 남았다").isEmpty();
    }

    @Test
    @DisplayName("영어 번들에는 한국어 값이 없다 — ko 아닌 로케일이 보는 자리다")
    void englishBundlesHoldNoKorean() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String name : List.of("messages.properties", "messages_en.properties")) {
            for (String line : Files.readAllLines(MESSAGE_DIR.resolve(name), StandardCharsets.UTF_8)) {
                String value = valueOf(line);
                if (value != null && HANGUL.matcher(value).find()) offenders.add(name + " → " + line);
            }
        }

        assertThat(offenders).as("영어 번들에 한국어가 샜다").isEmpty();
    }

    /** 주석·빈 줄은 검사 대상이 아니다. 값이 아니면 null. */
    private static String valueOf(String line) {
        String t = line.strip();
        if (t.isEmpty() || t.startsWith("#") || !t.contains("=")) return null;
        return t.substring(t.indexOf('=') + 1);
    }
}
