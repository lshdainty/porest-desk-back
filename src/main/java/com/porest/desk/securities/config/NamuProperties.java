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

    /**
     * 시세 캐시 유지 시간 (초).
     *
     * <p>나무엔 종목 다건 시세 API 가 없어 <b>종목마다 1콜</b>이 나간다. 자산 화면은 10초마다
     * 폴링하고 목록·상세가 각각 조회하므로, 캐시가 없으면 보유 30종목인 사용자 한 명이
     * 초당 3콜을 지속적으로 낸다 — 나무 유량 제한(429)에 걸린다.
     */
    private int quoteCacheTtlSeconds = 20;

    /**
     * 시세 다건 조회의 전체 시간 예산 (ms).
     *
     * <p>직렬 호출이라 종목이 많고 업스트림이 느리면 요청 하나가 분 단위로 늘어져 톰캣
     * 스레드를 물고 있는다. 예산을 넘기면 <b>거기까지 받은 것만</b> 돌려준다 — 받은 것은
     * 캐시에 남으므로 다음 폴링이 나머지를 이어 받아 몇 번 안에 다 찬다.
     */
    private long priceBatchBudgetMs = 4000;

    /** 연동에 필요한 필수 설정이 주입되었는지. 미설정 시 호출 전에 명확히 거절하기 위한 가드. */
    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank();
    }
}
