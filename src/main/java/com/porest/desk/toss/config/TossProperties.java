package com.porest.desk.toss.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 토스증권 Open API 연동 설정<br>
 * {@code app.toss.*} 프로퍼티를 바인딩한다. 인증정보(clientId/clientSecret)는
 * 환경변수(.env)로 주입하며 소스코드/저장소에 노출하지 않는다.
 *
 * @see <a href="https://developers.tossinvest.com/docs">토스증권 Open API</a>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.toss")
public class TossProperties {

    /** 토스증권 Open API base URL (예: https://openapi.tossinvest.com) */
    private String baseUrl;

    /** OAuth2 Client Credentials - 발급받은 클라이언트 ID */
    private String clientId;

    /** OAuth2 Client Credentials - 발급받은 클라이언트 시크릿 (서버 측에서만 사용) */
    private String clientSecret;

    /** 연결 타임아웃 (ms) */
    private int connectTimeout = 5000;

    /** 응답 타임아웃 (ms) */
    private int readTimeout = 10000;

    /**
     * 연동에 필요한 필수 설정이 모두 주입되었는지 여부.
     * 인증정보가 비어 있으면 토큰 발급을 시도하지 않고 명확한 에러를 던지기 위한 가드.
     */
    public boolean isConfigured() {
        return hasText(baseUrl) && hasText(clientId) && hasText(clientSecret);
    }

    private static boolean hasText(String v) {
        return v != null && !v.isBlank();
    }
}
