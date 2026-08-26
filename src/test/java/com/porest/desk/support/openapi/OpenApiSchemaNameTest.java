package com.porest.desk.support.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenAPI 스키마 <b>이름 충돌</b> 감시.
 *
 * <p>springdoc 은 스키마를 <b>단순 클래스명</b>으로 등록한다(swagger-core 의
 * {@code TypeNameResolver#getNameOfClass} → {@code Class#getSimpleName}). 모듈 18개가 같은
 * 레이아웃이라 {@code XxxApiDto.Response} · {@code CreateRequest} 처럼 같은 이름이 흔한데,
 * 겹치면 뒤에 등록된 쪽이 앞을 <b>말없이 덮어쓴다</b>. 스펙은 계속 그럴듯해 보이고, 그걸 믿고
 * 짠 클라이언트만 틀린 필드를 보낸다 — 실제로 {@code RegisterRequest} 가 그랬다
 * (나무 크리덴셜 등록이 레거시 토스 본문 {@code clientId/clientSecret} 으로 문서화됐다).
 *
 * <p>그래서 개별 케이스를 나열하는 대신 <b>전수로</b> 본다. {@code springdoc.use-fqn=true} 로
 * 문서를 받으면 스키마 키가 FQN 이라 <b>실제로 스펙에 들어간 클래스 목록</b>이 그대로 나온다.
 * 그 클래스들의 기본 이름({@code @Schema(name=...)} 이 있으면 그 값, 없으면 단순명)을 다시
 * 계산해 유일한지 확인한다 — swagger 가 하는 계산과 같다.
 *
 * <p>새로 만든 DTO 가 기존 이름과 겹치면 여기서 깨진다. 고치는 법은 겹치는 쪽에
 * {@code @Schema(name = "...")} 로 이름을 갈라 주는 것이다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "springdoc.use-fqn=true")
@ActiveProfiles("test")
class OpenApiSchemaNameTest {

    @LocalServerPort int port;

    @Test
    @DisplayName("스펙에 실린 클래스들의 스키마 이름이 서로 겹치지 않는다")
    void schemaNamesAreUnique() throws Exception {
        JsonNode schemas = new ObjectMapper()
            .readTree(RestClient.create().get()
                .uri("http://localhost:" + port + "/v3/api-docs")
                .retrieve().body(String.class))
            .path("components").path("schemas");

        Map<String, List<String>> byName = new LinkedHashMap<>();
        for (Iterator<String> it = schemas.fieldNames(); it.hasNext(); ) {
            String key = it.next();
            Class<?> type = classOf(key);
            if (type == null) {
                continue; // 제네릭 래퍼(ApiResponseXxx 등) — 클래스가 아니라 조합된 이름이다
            }
            Schema declared = type.getDeclaredAnnotation(Schema.class);
            String name = declared != null && !declared.name().isBlank()
                ? declared.name()
                : type.getSimpleName();
            byName.computeIfAbsent(name, k -> new ArrayList<>()).add(type.getName());
        }

        assertThat(byName).allSatisfy((name, owners) ->
            assertThat(owners)
                .as("스키마 이름 '%s' 을 %s 가 나눠 쓴다 — 마지막 하나만 스펙에 남는다."
                    + " 겹치는 쪽에 @Schema(name = \"...\") 을 달아 이름을 갈라라", name, owners)
                .hasSize(1));
    }

    /**
     * FQN 스키마 키 → 클래스. 키는 {@code pkg.Outer.Inner} 라 중첩 구분자가 {@code .} 다.
     * 제네릭 래퍼 키({@code ...ApiResponseCom.porest...})는 클래스가 아니므로 null 을 준다.
     */
    private Class<?> classOf(String key) {
        String[] segs = key.split("\\.");
        int first = -1;
        for (int i = 0; i < segs.length; i++) {
            if (!segs[i].isEmpty() && Character.isUpperCase(segs[i].charAt(0))) {
                first = i;
                break;
            }
        }
        if (first < 0) {
            return null;
        }
        for (int i = first + 1; i < segs.length; i++) {
            if (segs[i].isEmpty() || !Character.isUpperCase(segs[i].charAt(0))) {
                return null; // 클래스명 뒤에 소문자 패키지가 또 나온다 = 조합된 래퍼 이름
            }
        }
        String binary = String.join(".", List.of(segs).subList(0, first + 1))
            + String.join("", List.of(segs).subList(first + 1, segs.length).stream()
                .map(s -> "$" + s).toList());
        try {
            return Class.forName(binary);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
