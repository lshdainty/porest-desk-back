package com.porest.desk.securities.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 나무증권(NH PLUG) Open API 연동 설정. {@code app.namu.*} 를 바인딩한다.
 *
 * <p>인증정보는 서버 공용 키가 아니라 사용자가 등록한 본인 키(암호화 저장)를 쓰므로
 * 여기엔 base URL·타임아웃만 둔다.
 *
 * <p>모의투자 도메인({@code moapi.nhplug.com})은 두지 않았다 — 토큰 발급 자체가 운영
 * 도메인 전용이고, porest 는 주문 없이 조회만 하므로 모의로 갈 이유가 없다.
 *
 * @see <a href="https://www.nhplug.com/llms.txt">NH PLUG OpenAPI 요약</a>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.namu")
public class NamuProperties {

    /** NH PLUG Open API base URL. 포트(8443)까지 포함해야 한다. */
    private String baseUrl;

    /** 연결 타임아웃 (ms) */
    private int connectTimeout = 5000;

    /** 응답 타임아웃 (ms) */
    private int readTimeout = 10000;

    /** 연동에 필요한 필수 설정이 주입되었는지. 미설정 시 호출 전에 명확히 거절하기 위한 가드. */
    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank();
    }
}
