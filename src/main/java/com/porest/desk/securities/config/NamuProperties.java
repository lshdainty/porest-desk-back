package com.porest.desk.securities.config;

import com.porest.desk.securities.type.NamuEnvironment;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 나무증권(NH PLUG) Open API 연동 설정. {@code app.namu.*} 를 바인딩한다.
 *
 * <p>인증정보는 서버 공용 키가 아니라 사용자가 등록한 본인 키(암호화 저장)를 쓰므로
 * 여기엔 환경·URL·타임아웃만 둔다.
 *
 * <p><b>URL 이 둘인 이유.</b> 예전 주석은 "모의투자 도메인은 두지 않았다 — 토큰 발급이 운영
 * 전용이고 조회만 하니 모의로 갈 이유가 없다" 였는데, 뒤 절반이 틀렸다. <b>잔고 조회는 계좌를
 * 타고, 계좌는 구분({@code acct_type})에 맞는 도메인에서만 유효하다</b> — 개발에서 실계좌
 * 잔고를 긁지 않으려면 모의투자 도메인이 필요하다. 반면 {@code /oauth2/token} 은 스펙상
 * 모의투자 미제공이라 <b>환경과 무관하게 항상 운영</b>에서 받는다(받은 토큰은 양쪽 공용).
 * 그래서 조회용({@link #getBaseUrl()})과 토큰용({@link #getAuthBaseUrl()})을 나눠 둔다.
 * 하나로 합치면 환경을 모의로 돌리는 순간 인증부터 죽는다.
 *
 * @see NamuEnvironment
 * @see <a href="https://www.nhplug.com/llms.txt">NH PLUG OpenAPI 요약</a>
 */
@Slf4j
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.namu")
public class NamuProperties {

    /**
     * 연동 환경. <b>도메인과 계좌구분을 함께 결정하는 단일 출처다</b> — 계좌구분을 따로
     * 설정하지 않는 이유는 {@link NamuEnvironment} 참고.
     */
    private NamuEnvironment environment = NamuEnvironment.LIVE;

    /**
     * 조회 base URL 오버라이드. 비우면 {@link #environment} 의 기본 도메인을 쓴다 —
     * 평소엔 비워 두고 환경만 바꾼다. 스텁·프록시를 물릴 때만 채운다.
     */
    private String baseUrl;

    /**
     * 토큰 발급 base URL. <b>항상 운영이다</b> — 모의투자는 발급을 제공하지 않는다.
     * 환경변수로 뚫지 않는 이유: 누군가 환경을 모의로 맞추면서 이것까지 같이 바꿔 인증을 깬다.
     */
    private String authBaseUrl = NamuEnvironment.LIVE.getDefaultBaseUrl();

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
     * 캔들 캐시 유지 시간 (초).
     *
     * <p>차트의 기간 탭(1주·1개월·3개월·1년)은 <b>전부 같은 일봉 첫 페이지</b>를 부른다.
     * 탭을 빠르게 누르면 같은 요청이 연달아 나가고, 화면의 실시간 폴링(분봉 15초 ·
     * 일봉 60초)이 거기 겹친다. 나무는 캔들도 종목당 1콜이라 그대로 두면 429 에 닿는다.
     *
     * <p>시세와 같은 20초인 이유 — 폴링 주기(15초)보다 길어야 한 주기에 한 번만 상류로
     * 나간다. 0 이하로 두면 캐시를 끈다(로컬 디버깅용).
     */
    private int candleCacheTtlSeconds = 20;

    /**
     * 환율 폴백에 쓸 <b>미국 상장 종목코드</b>.
     *
     * <p>나무 환율의 1순위는 해외 잔고의 당일매매기준환율인데, 그건 <b>계좌와 USD 보유 종목이
     * 둘 다 있어야</b> 나온다. 그래서 해외 계좌가 없는 사용자는 환율을 아예 못 구했고, 화면은
     * 외화 평가를 통째로 접었다.
     *
     * <p>해외 현재가({@code /gbstock/quote/v1/current})는 {@code currency_prc}(원화 환산 환율)를
     * 함께 주고 <b>요청에 종목코드 하나만</b> 필요하다 — 계좌도 보유도 필요 없다. 그 폴백이
     * 물어볼 종목을 여기서 정한다.
     *
     * <p>기본값이 애플인 이유는 나무 공식 스펙이 {@code iem_cd} 예시로 쓰는 종목이기 때문이다
     * ("예시&gt; 미국주식 APPLE인 경우, AAPL"). 상장폐지·티커 변경 같은 일이 생기면 배포 없이
     * 이 값만 바꿔 끼우면 된다. 비우면 폴백을 끈다.
     */
    private String fxProbeSymbol = "AAPL";

    /**
     * 시세 다건 조회의 전체 시간 예산 (ms).
     *
     * <p>직렬 호출이라 종목이 많고 업스트림이 느리면 요청 하나가 분 단위로 늘어져 톰캣
     * 스레드를 물고 있는다. 예산을 넘기면 <b>거기까지 받은 것만</b> 돌려준다 — 받은 것은
     * 캐시에 남으므로 다음 폴링이 나머지를 이어 받아 몇 번 안에 다 찬다.
     */
    private long priceBatchBudgetMs = 4000;

    /**
     * 조회에 실제로 쓸 base URL. 오버라이드가 비면 환경 기본값.
     *
     * <p>환경까지 비면 null 이다 — {@code NAMU_ENVIRONMENT=} 를 빈 값으로 주면 enum 이 null 로
     * 바인딩되므로 실제로 생길 수 있는 상태다. 기동 검사가 먼저 막지만, 그걸 안 거친 객체
     * (단위 테스트 등)를 위해 여기서도 터지지 않게 둔다.
     */
    public String getBaseUrl() {
        if (baseUrl != null && !baseUrl.isBlank()) {
            return baseUrl;
        }
        return environment == null ? null : environment.getDefaultBaseUrl();
    }

    /** 연동에 필요한 필수 설정이 주입되었는지. 미설정 시 호출 전에 명확히 거절하기 위한 가드. */
    public boolean isConfigured() {
        String url = getBaseUrl();
        return url != null && !url.isBlank();
    }

    /**
     * 환경과 도메인이 어긋나면 기동을 막는다.
     *
     * <p>어긋난 조합({@code LIVE} + moapi, {@code MOCK} + api)은 <b>기동에서는 아무 티가 안 나고</b>
     * 잔고 조회에서만 계좌번호 오류로 터진다. 그 사고를 한 번 겪어서 여기서 잡는다 —
     * 이 레포에 이미 있는 기동 완결성 검사({@code SchemaConsistencyCheck} ·
     * {@code SecuritiesPriceProviders})와 같은 자리다.
     *
     * <p>나무 호스트가 아니면(로컬 스텁·프록시) 판정할 근거가 없으므로 경고만 남기고 통과시킨다.
     */
    @PostConstruct
    void verifyEnvironmentMatchesBaseUrl() {
        if (environment == null) {
            throw new IllegalStateException(
                "나무증권 연동 환경(app.namu.environment)이 비었다 - LIVE 또는 MOCK 을 지정해야 한다. "
                    + "빈 값으로 두면 도메인도 유효 계좌구분도 정할 수 없다.");
        }
        String url = getBaseUrl();
        NamuEnvironment fromUrl = NamuEnvironment.ofBaseUrl(url);
        if (fromUrl == null) {
            log.warn("나무증권 base-url 이 나무 도메인이 아니다 - 환경 정합성을 확인할 수 없다: environment={}, baseUrl={}",
                environment, url);
        } else if (fromUrl != environment) {
            throw new IllegalStateException(
                "나무증권 환경과 base-url 이 어긋났다 - environment=" + environment
                    + " 인데 base-url 은 " + fromUrl + " 도메인(" + NamuEnvironment.host(url) + ")이다. "
                    + "잔고 조회가 계좌번호 오류로 실패한다. app.namu.base-url 을 비우고 "
                    + "app.namu.environment 만 지정하는 것을 권한다.");
        }

        if (NamuEnvironment.ofBaseUrl(authBaseUrl) != NamuEnvironment.LIVE) {
            log.warn("나무증권 토큰 발급 URL 이 운영 도메인이 아니다 - 모의투자는 발급을 제공하지 않는다: authBaseUrl={}",
                authBaseUrl);
        }

        log.info("나무증권 연동 환경 {} - 조회 {}, 토큰 {}, 계좌구분 {}",
            environment, NamuEnvironment.host(url), NamuEnvironment.host(authBaseUrl),
            environment.getAccountTypes());
    }
}
