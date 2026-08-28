package com.porest.desk.namu.service;

import com.porest.desk.namu.client.NamuApiClient;
import com.porest.desk.securities.client.BrokerTokenManager;
import com.porest.desk.securities.client.BrokerTokenManagers;
import com.porest.desk.securities.config.NamuApiClientConfig;
import com.porest.desk.securities.config.NamuProperties;
import com.porest.desk.securities.type.NamuEnvironment;
import com.porest.desk.securities.type.SecuritiesBroker;
import com.porest.desk.stock.repository.StockMasterRepository;
import com.porest.desk.stock.service.StockMasterResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 환율 조회가 <b>업스트림을 몇 번 부르는지</b>를 못 박는다.
 *
 * <h2>왜 횟수를 세는 테스트인가</h2>
 * 이 코드가 고치는 사고가 "값이 틀렸다" 가 아니라 "<b>너무 많이 불렀다</b>" 였기 때문이다.
 * 환율은 한 번 물을 때 계좌목록 + 해외잔고(+ 폴백 시 해외현재가)로 최대 3콜이 100ms 안에
 * 몰려 나가는데 캐시가 없어 화면이 부를 때마다 그대로 나갔고, 나무가 뒤 두 개를 429 로
 * 거절했다 — dev 실측 2026-08-28, {@code rsp_cd=IGW42902} "APP 호출 거래건수를
 * 초과하였습니다", 두 경로가 30~40ms 간격으로 짝지어 8건.
 *
 * <p>값만 검사하는 테스트로는 이 회귀를 못 잡는다. 호출이 두 배가 되어도 결과는 같기
 * 때문이다. 그래서 {@link MockRestServiceServer} 로 <b>나간 요청 수</b>를 고정한다 —
 * 다음 리팩터링에서 조용히 늘면 여기서 깨진다.
 *
 * <p>기대를 <b>순서 없이</b> 받도록 만든 것도 그래서다({@code ignoreExpectOrder}).
 * 순서가 아니라 <b>횟수</b>가 이 테스트의 주장이고, 기대에 없는 요청이 나가면
 * {@code MockRestServiceServer} 가 그 자리에서 실패시킨다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NamuFxRateCacheTest {

    private static final String BASE = "https://api.nhplug.com:8443";
    private static final String ACCOUNT_URL = BASE + "/n2/acctinfo";
    private static final String BALANCE_URL = BASE + "/gbstock/inquiry/v1/balance";
    private static final String QUOTE_URL = BASE + "/gbstock/quote/v1/current";

    private static final long USER = 1L;
    private static final long OTHER_USER = 2L;

    /** 실계좌(acct_type=01). 나무 계좌번호는 11자리다. */
    private static final String LIVE_ACCT = "33333333301";

    @Mock private BrokerTokenManagers tokenManagers;
    @Mock private BrokerTokenManager tokenManager;
    @Mock private StockMasterRepository stockMasterRepository;

    private MockRestServiceServer server;
    private NamuProperties properties;
    private NamuQueryServiceImpl sut;

    @BeforeEach
    void setUp() {
        given(tokenManagers.of(SecuritiesBroker.NAMU)).willReturn(tokenManager);
        HttpHeaders auth = new HttpHeaders();
        auth.setBearerAuth("tok");
        given(tokenManager.authHeaders(USER)).willReturn(auth);
        given(tokenManager.authHeaders(OTHER_USER)).willReturn(auth);

        properties = new NamuProperties();
        properties.setBaseUrl(BASE);
        properties.setEnvironment(NamuEnvironment.LIVE);

        // 운영과 같은 RestTemplate 을 쓴다 — 컨버터 설정 차이로 테스트만 통과하는 일이 없게.
        RestTemplate restTemplate = new NamuApiClientConfig().namuRestTemplate(properties);
        server = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
        sut = new NamuQueryServiceImpl(new NamuApiClient(restTemplate, tokenManagers, properties),
            new StockMasterResolver(stockMasterRepository), properties);
    }

    // ---- 픽스처 ------------------------------------------------------------

    private static final String BALANCE_WITH_USD = """
        {"rsp_cd":"00000","rsp_msg":"ok",
         "Output_0":{"fc_eal_amt":"1500.00","fc_eal_pls_amt":"300.00","pft_rt":"25.00"},
         "Output_1":[{"iem_cd":"AAPL","iem_nm":"애플","cns_bse_bnc_qty":"5",
                      "fc_avg_phs_pr":"180.00","fc_sec_end_pr":"185.70",
                      "fc_eal_amt":"928.50","fc_eal_pls_amt":"28.50",
                      "cur_cd":"USD","tdt_sby_bse_xcg_rt":"1383.50"}]}
        """;

    private static String quoteJson(String currencyUnit, String currencyPrc) {
        return """
            {"rsp_cd":"00000","rsp_msg":"ok",
             "Output_0":{"iem_cd":"AAPL","kor_name":"애플","trdprc":"185.70",
                         "base_prc":"183.20","netchng_cls":"2","netchng":"2.50",
                         "pctchng":"1.36","currency_unit":"%s","currency_prc":"%s"}}
            """.formatted(currencyUnit, currencyPrc);
    }

    /** 계좌 1건(실계좌)을 {@code count} 번 돌려준다. */
    private void expectAccountLookup(ExpectedCount count) {
        server.expect(count, requestTo(ACCOUNT_URL))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""
                {"rsp_cd":"00000","rsp_msg":"ok",
                 "Output_0":[{"acct_no":"%s","acct_type":"01"}]}
                """.formatted(LIVE_ACCT), MediaType.APPLICATION_JSON));
    }

    /** 계좌 0건 — 나무 연동은 했지만 해외 계좌가 없는 사용자. */
    private void expectEmptyAccountLookup(ExpectedCount count) {
        server.expect(count, requestTo(ACCOUNT_URL))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""
                {"rsp_cd":"00000","rsp_msg":"ok","Output_0":[]}
                """, MediaType.APPLICATION_JSON));
    }

    private void expectBalance(ExpectedCount count, String body) {
        server.expect(count, requestTo(BALANCE_URL))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    /** 나무가 실제로 돌려주는 429 본문 그대로. */
    private void expectBalanceRateLimited(ExpectedCount count) {
        server.expect(count, requestTo(BALANCE_URL))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .body("""
                    {"rsp_cd":"IGW42902","rsp_msg":"APP 호출 거래건수를 초과하였습니다."}
                    """)
                .contentType(MediaType.APPLICATION_JSON));
    }

    private void expectQuote(ExpectedCount count, String body) {
        server.expect(count, requestTo(QUOTE_URL))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    @Nested
    @DisplayName("캐시")
    class Cache {

        @Test
        @DisplayName("연속으로 5번 물어도 업스트림엔 1번만 나간다 — 화면 폴링이 상류로 새지 않는다")
        void repeatedCallsHitUpstreamOnce() {
            expectAccountLookup(ExpectedCount.once());
            expectBalance(ExpectedCount.once(), BALANCE_WITH_USD);

            for (int i = 0; i < 5; i++) {
                assertThat(sut.getFxRate(USER, "USD")).isEqualByComparingTo(new BigDecimal("1383.50"));
            }

            // 기대는 각각 1회다. 2회째가 나갔다면 "No further requests expected" 로 이미 터졌다.
            server.verify();
        }

        @Test
        @DisplayName("TTL 이 지나면 다시 부른다 — 값을 영영 붙들지 않는다")
        void refetchesAfterTtl() throws InterruptedException {
            properties.setFxCacheTtlSeconds(1);
            expectAccountLookup(ExpectedCount.times(2));
            expectBalance(ExpectedCount.times(2), BALANCE_WITH_USD);

            assertThat(sut.getFxRate(USER, "USD")).isEqualByComparingTo(new BigDecimal("1383.50"));
            assertThat(sut.getFxRate(USER, "USD")).isEqualByComparingTo(new BigDecimal("1383.50"));

            Thread.sleep(1_100);

            assertThat(sut.getFxRate(USER, "USD")).isEqualByComparingTo(new BigDecimal("1383.50"));
            server.verify();
        }

        @Test
        @DisplayName("캐시를 끄면(TTL 0) 매번 나간다 — 로컬 디버깅용 탈출구가 살아 있다")
        void ttlZeroDisablesCache() {
            properties.setFxCacheTtlSeconds(0);
            expectAccountLookup(ExpectedCount.times(3));
            expectBalance(ExpectedCount.times(3), BALANCE_WITH_USD);

            for (int i = 0; i < 3; i++) {
                assertThat(sut.getFxRate(USER, "USD")).isEqualByComparingTo(new BigDecimal("1383.50"));
            }
            server.verify();
        }

        @Test
        @DisplayName("못 구한 것도 캐시한다 — 실패를 안 담으면 캐시가 아무 일도 안 한다")
        void cachesFailureToo() {
            // 계좌가 없어 1순위를 접고, 폴백 종목의 통화가 달라 2순위도 접는다 → null.
            expectEmptyAccountLookup(ExpectedCount.once());
            expectQuote(ExpectedCount.once(), quoteJson("JPY", "891.50"));

            for (int i = 0; i < 4; i++) {
                assertThat(sut.getFxRate(USER, "USD")).isNull();
            }
            server.verify();
        }

        @Test
        @DisplayName("시세 폴백 값은 사용자끼리 나눠 쓴다 — 계좌를 안 타는 시장 환율이라 누가 물어도 같다")
        void quoteFallbackIsSharedAcrossUsers() {
            // 계좌 조회는 사용자별이라 두 번 나가지만, 시세는 한 번이면 족하다.
            expectEmptyAccountLookup(ExpectedCount.times(2));
            expectQuote(ExpectedCount.once(), quoteJson("USD", "1381.20"));

            assertThat(sut.getFxRate(USER, "USD")).isEqualByComparingTo(new BigDecimal("1381.20"));
            assertThat(sut.getFxRate(OTHER_USER, "USD")).isEqualByComparingTo(new BigDecimal("1381.20"));
            server.verify();
        }

        @Test
        @DisplayName("잔고 환율은 사용자끼리 안 나눈다 — 그 계좌에 적용된 값이라 남에게 쓰면 화면과 어긋난다")
        void balanceRateIsNotSharedAcrossUsers() {
            expectAccountLookup(ExpectedCount.times(2));
            expectBalance(ExpectedCount.times(2), BALANCE_WITH_USD);

            assertThat(sut.getFxRate(USER, "USD")).isEqualByComparingTo(new BigDecimal("1383.50"));
            assertThat(sut.getFxRate(OTHER_USER, "USD")).isEqualByComparingTo(new BigDecimal("1383.50"));
            server.verify();
        }
    }

    @Nested
    @DisplayName("429")
    class RateLimited {

        @Test
        @DisplayName("1순위가 429 면 폴백을 타지 않는다 — 같은 초에 429 를 한 번 더 맞던 회귀")
        void doesNotFallBackWhenRateLimited() {
            expectAccountLookup(ExpectedCount.once());
            expectBalanceRateLimited(ExpectedCount.once());
            // 시세 기대를 일부러 걸지 않는다. 폴백이 나가면 기대에 없는 요청이라 여기서 터진다.

            assertThat(sut.getFxRate(USER, "USD")).isNull();
            server.verify();
        }

        @Test
        @DisplayName("429 를 맞으면 백오프 동안 아무것도 안 나간다 — 실패했다고 매번 다시 치지 않는다")
        void backsOffAfterRateLimit() {
            expectAccountLookup(ExpectedCount.once());
            expectBalanceRateLimited(ExpectedCount.once());

            for (int i = 0; i < 3; i++) {
                assertThat(sut.getFxRate(USER, "USD")).isNull();
            }
            server.verify();
        }

        @Test
        @DisplayName("429 백오프는 일반 실패 캐시와 따로 논다 — 뜻이 다르니 쉬는 시간도 다르다")
        void rateLimitBackoffIsIndependentOfFailureTtl() throws InterruptedException {
            // 일반 실패는 1초 만에 풀리지만 429 백오프는 아직 안 풀린 상태를 만든다.
            properties.setFxFailureCacheTtlSeconds(1);
            properties.setFxRateLimitBackoffSeconds(600);
            expectAccountLookup(ExpectedCount.once());
            expectBalanceRateLimited(ExpectedCount.once());

            assertThat(sut.getFxRate(USER, "USD")).isNull();
            Thread.sleep(1_100);

            // 일반 실패 TTL 이 지났어도 429 백오프가 살아 있어 다시 나가지 않는다.
            assertThat(sut.getFxRate(USER, "USD")).isNull();
            server.verify();
        }

        @Test
        @DisplayName("502 는 429 와 다르다 — 폴백을 그대로 탄다(PR #280 동작 유지)")
        void stillFallsBackOnPlainUpstreamFailure() {
            expectAccountLookup(ExpectedCount.once());
            server.expect(ExpectedCount.once(), requestTo(BALANCE_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));
            expectQuote(ExpectedCount.once(), quoteJson("USD", "1385.75"));

            assertThat(sut.getFxRate(USER, "USD")).isEqualByComparingTo(new BigDecimal("1385.75"));
            server.verify();
        }

        @Test
        @DisplayName("폴백이 429 를 맞아도 백오프한다 — 2순위에서 걸린 것도 같은 신호다")
        void backsOffWhenFallbackIsRateLimited() {
            expectEmptyAccountLookup(ExpectedCount.once());
            server.expect(ExpectedCount.once(), requestTo(QUOTE_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

            for (int i = 0; i < 3; i++) {
                assertThat(sut.getFxRate(USER, "USD")).isNull();
            }
            server.verify();
        }

        @Test
        @DisplayName("429 실패는 사용자끼리 안 나눈다 — 인증정보가 사용자별 키라 남을 대신 벌주지 않는다")
        void rateLimitBackoffIsNotSharedAcrossUsers() {
            expectEmptyAccountLookup(ExpectedCount.times(2));
            server.expect(ExpectedCount.once(), requestTo(QUOTE_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
            expectQuote(ExpectedCount.once(), quoteJson("USD", "1379.00"));

            assertThat(sut.getFxRate(USER, "USD")).isNull();
            assertThat(sut.getFxRate(OTHER_USER, "USD")).isEqualByComparingTo(new BigDecimal("1379.00"));
            server.verify();
        }
    }

    @Nested
    @DisplayName("미지원 통화")
    class Unsupported {

        @Test
        @DisplayName("미국 외 통화는 캐시를 타기도 전에 접힌다 — 상류에 나가지 않는다")
        void nonUsdNeverTouchesUpstream() {
            for (int i = 0; i < 3; i++) {
                assertThat(sut.getFxRate(USER, "JPY")).isNull();
            }
            server.verify();
        }
    }
}
