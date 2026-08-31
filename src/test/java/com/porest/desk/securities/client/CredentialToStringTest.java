package com.porest.desk.securities.client;

import com.porest.desk.securities.controller.LegacyTossCredentialApiController;
import com.porest.desk.securities.controller.dto.SecuritiesCredentialApiDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 크리덴셜 record 의 toString 이 평문을 노출하지 않는지 고정한다.
 *
 * <p>지금 이 record 들을 로그에 찍는 코드는 없지만, record 자동 toString 은 모든 컴포넌트를
 * 그대로 이어 붙이므로 {@code log.info("{}", request)} 한 줄이면 평문 크리덴셜이 로그에
 * 남는 구조였다. record 쪽에서 막아 두면 호출부가 어떻게 생겨나든 안전하다.
 */
class CredentialToStringTest {

    @Test
    void 크리덴셜_record_의_toString_은_평문을_노출하지_않는다() {
        String key = "PLAIN-KEY-1234567890";
        String secret = "PLAIN-SECRET-1234567890";

        assertThat(new SecuritiesCredentialApiDto.RegisterRequest(key, secret).toString())
            .doesNotContain(key)
            .doesNotContain(secret);
        assertThat(new LegacyTossCredentialApiController.RegisterRequest(key, secret).toString())
            .doesNotContain(key)
            .doesNotContain(secret);
        assertThat(new AbstractBrokerTokenManager.ApiCredential(key, secret).toString())
            .doesNotContain(key)
            .doesNotContain(secret);
    }
}
