package com.porest.desk.common.config.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로그 마스킹 검증 — 토스 클라이언트 키/비밀번호/토큰이 평문으로 남지 않아야 한다.
 */
class RequestResponseLoggingFilterTest {

    private final RequestResponseLoggingFilter sut = new RequestResponseLoggingFilter();

    @Test
    @DisplayName("토스 크리덴셜 JSON 의 clientSecret·clientId 가 마스킹된다")
    void masks_toss_credentials() {
        String body = "{\"clientId\":\"tsck_live_ArfgoFbLDafecJFMjelTAeB\","
                + "\"clientSecret\":\"tssk_live_ebkuEpOmb5T6XtZ538o1xZtrDs10Zu3acztY8n5yF3a\"}";

        String masked = sut.maskSensitiveData(body);

        assertThat(masked).doesNotContain("tssk_live_");
        assertThat(masked).doesNotContain("tsck_live_");
        assertThat(masked).contains("\"clientSecret\":\"***\"");
        assertThat(masked).contains("\"clientId\":\"***\"");
    }

    @Test
    @DisplayName("기존 password·user_pw 마스킹은 유지된다")
    void masks_password() {
        String body = "{\"id\":\"alice\",\"password\":\"p@ssw0rd!\",\"user_pw\":\"secret123\"}";

        String masked = sut.maskSensitiveData(body);

        assertThat(masked).contains("\"password\":\"***\"");
        assertThat(masked).contains("\"user_pw\":\"***\"");
        assertThat(masked).doesNotContain("p@ssw0rd!");
        assertThat(masked).doesNotContain("secret123");
        // 비민감 필드는 보존
        assertThat(masked).contains("\"id\":\"alice\"");
    }

    @Test
    @DisplayName("비밀번호 변경 필드(currentPassword·newPassword·confirmPassword)가 마스킹된다")
    void masks_password_change_fields() {
        String body = "{\"currentPassword\":\"1q2w3e$R\",\"newPassword\":\"qwer!@#$\","
                + "\"confirmPassword\":\"qwer!@#$\"}";

        String masked = sut.maskSensitiveData(body);

        assertThat(masked).contains("\"currentPassword\":\"***\"");
        assertThat(masked).contains("\"newPassword\":\"***\"");
        assertThat(masked).contains("\"confirmPassword\":\"***\"");
        assertThat(masked).doesNotContain("1q2w3e$R");
        assertThat(masked).doesNotContain("qwer!@#$");
    }

    @Test
    @DisplayName("OAuth 토큰류(access_token·refreshToken)가 마스킹된다")
    void masks_tokens() {
        String body = "{\"access_token\":\"abc.def.ghi\",\"refreshToken\":\"rrr-111\"}";

        String masked = sut.maskSensitiveData(body);

        assertThat(masked).contains("\"access_token\":\"***\"");
        assertThat(masked).contains("\"refreshToken\":\"***\"");
        assertThat(masked).doesNotContain("abc.def.ghi");
        assertThat(masked).doesNotContain("rrr-111");
    }

    @Test
    @DisplayName("대소문자가 달라도 키를 인식해 마스킹한다")
    void masks_case_insensitive() {
        String body = "{\"ClientSecret\":\"LEAK\",\"PASSWORD\":\"LEAK2\"}";

        String masked = sut.maskSensitiveData(body);

        // 값만 ***로 치환되고 키 case·구조는 보존되어야 한다(삭제/오염이 아님)
        assertThat(masked).isEqualTo("{\"ClientSecret\":\"***\",\"PASSWORD\":\"***\"}");
    }

    @Test
    @DisplayName("쿼리스트링/폼 형태의 민감값도 마스킹한다 (선두 파라미터 포함)")
    void masks_query_string() {
        String query = "password=abc&client_secret=xyz&page=1";

        String masked = sut.maskSensitiveData(query);

        assertThat(masked).isEqualTo("password=***&client_secret=***&page=1");
    }

    @Test
    @DisplayName("값에 이스케이프된 따옴표가 있어도 전체 값을 마스킹한다(부분 노출 방지)")
    void masks_value_with_escaped_quote() {
        String body = "{\"password\":\"a\\\"b\",\"keep\":\"x\"}";

        String masked = sut.maskSensitiveData(body);

        assertThat(masked).contains("\"password\":\"***\"");
        assertThat(masked).contains("\"keep\":\"x\"");
        // 이스케이프 뒤 조각(b)이 평문으로 남지 않아야 한다
        assertThat(masked).doesNotContain("b\"");
    }

    @Test
    @DisplayName("유사 키(clientSecret)와 standalone secret 키가 독립적으로 마스킹된다")
    void does_not_corrupt_on_substring_key() {
        String body = "{\"clientSecret\":\"AAA\",\"secret\":\"BBB\"}";

        String masked = sut.maskSensitiveData(body);

        assertThat(masked).isEqualTo("{\"clientSecret\":\"***\",\"secret\":\"***\"}");
    }

    @Test
    @DisplayName("이미 마스킹된 본문을 다시 마스킹해도 동일하다(멱등)")
    void masking_is_idempotent() {
        String once = sut.maskSensitiveData("{\"clientSecret\":\"LEAK\",\"id\":1}");

        assertThat(sut.maskSensitiveData(once)).isEqualTo(once);
    }

    @Test
    @DisplayName("민감 필드가 없으면 본문을 그대로 둔다")
    void keeps_non_sensitive_body() {
        String body = "{\"name\":\"홍길동\",\"amount\":1000}";

        assertThat(sut.maskSensitiveData(body)).isEqualTo(body);
    }

    @Test
    @DisplayName("null·빈 문자열은 그대로 반환한다")
    void handles_null_and_empty() {
        assertThat(sut.maskSensitiveData(null)).isNull();
        assertThat(sut.maskSensitiveData("")).isEmpty();
    }
}
