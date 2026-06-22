package com.porest.desk.toss.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 토스증권 Open API 연동 설정<br>
 * {@code app.toss.*} 프로퍼티를 바인딩한다. 인증정보(client_id/client_secret)는 서버 공용 키가 아니라
 * 사용자가 등록한 본인 키(암호화 저장)를 사용하므로 여기엔 base URL·타임아웃만 둔다.
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

    /** 연결 타임아웃 (ms) */
    private int connectTimeout = 5000;

    /** 응답 타임아웃 (ms) */
    private int readTimeout = 10000;

    /**
     * 연동에 필요한 필수 설정(base URL)이 주입되었는지 여부.
     * 미설정 시 토큰 발급을 시도하지 않고 명확한 에러를 던지기 위한 가드.
     */
    public boolean isConfigured() {
        return hasText(baseUrl);
    }

    private static boolean hasText(String v) {
        return v != null && !v.isBlank();
    }
}
