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
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 환율 폴백을 <b>실제 JSON 을 태워서</b> 고정한다.
 *
 * <p>예전엔 해외 잔고가 환율의 유일한 경로라고 믿었다. 그래서 <b>해외 계좌가 없는 사용자는
 * 환율을 영영 못 구했고</b> 화면이 외화 평가를 통째로 접었다. 나무 공식 스펙상
 * {@code /gbstock/quote/v1/current} 의 {@code Output_0} 에 {@code currency_prc} 가 있고,
 * 그 요청은 {@code iem_cd} 하나만 받는다 — 계좌도 보유도 필요 없다.
 *
 * <p>{@link NamuApiClient} 를 목으로 두면 이런 버그를 못 잡는다. 목은 "안 나간 요청" 에도
 * 아무 불평을 안 하기 때문이다. 그래서 여기서는 서비스 → 클라이언트 → RestTemplate 을 실제로
 * 잇고 <b>바이트로 나가는 요청</b>과 <b>돌아오는 JSON</b>을 본다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NamuFxRateFallbackTest {

    private static final String BASE = "https://api.nhplug.com:8443";
    private static final long USER = 1L;
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
        stubToken();
        properties = new NamuProperties();
        properties.setBaseUrl(BASE);
        properties.setEnvironment(NamuEnvironment.LIVE);

        // 운영과 같은 RestTemplate 을 쓴다 — 컨버터 설정 차이로 테스트만 통과하는 일이 없게.
        RestTemplate restTemplate = new NamuApiClientConfig().namuRestTemplate(properties);
        server = MockRestServiceServer.createServer(restTemplate);
        sut = new NamuQueryServiceImpl(new NamuApiClient(restTemplate, tokenManagers, properties),
            new StockMasterResolver(stockMasterRepository), properties);
    }

    private void stubToken() {
        given(tokenManagers.of(SecuritiesBroker.NAMU)).willReturn(tokenManager);
        HttpHeaders auth = new HttpHeaders();
        auth.setBearerAuth("tok");
        given(tokenManager.authHeaders(USER)).willReturn(auth);
    }

    // ---- 픽스처 ------------------------------------------------------------

    /** 계좌 1건(실계좌). */
    private void expectAccountLookup() {
        server.expect(requestTo(BASE + "/n2/acctinfo"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""
                {"rsp_cd":"00000","rsp_msg":"ok",
                 "Output_0":[{"acct_no":"%s","acct_type":"01"}]}
                """.formatted(LIVE_ACCT), MediaType.APPLICATION_JSON));
    }

    /** 계좌 0건 — 나무 연동은 했지만 해외 계좌가 없는 사용자. */
    private void expectEmptyAccountLookup() {
        server.expect(requestTo(BASE + "/n2/acctinfo"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""
                {"rsp_cd":"00000","rsp_msg":"ok","Output_0":[]}
                """, MediaType.APPLICATION_JSON));
    }

    /** USD 보유 종목이 있는 잔고 — 환율이 종목 행에 실려 온다. */
    private static final String BALANCE_WITH_USD = """
        {"rsp_cd":"00000","rsp_msg":"ok",
         "Output_0":{"fc_eal_amt":"1500.00","fc_eal_pls_amt":"300.00","pft_rt":"25.00"},
         "Output_1":[{"iem_cd":"AAPL","iem_nm":"애플","cns_bse_bnc_qty":"5",
                      "fc_avg_phs_pr":"180.00","fc_sec_end_pr":"185.70",
                      "fc_eal_amt":"928.50","fc_eal_pls_amt":"28.50",
                      "cur_cd":"USD","tdt_sby_bse_xcg_rt":"1383.50"}]}
        """;

    /** 보유 종목이 0건인 잔고 — 계좌는 있는데 환율을 실어 올 행이 없다. */
    private static final String BALANCE_EMPTY = """
        {"rsp_cd":"00000","rsp_msg":"ok",
         "Output_0":{"fc_eal_amt":"0","fc_eal_pls_amt":"0","pft_rt":"0"},
         "Output_1":[]}
        """;

    /** 해외 현재가 — 실제 스펙 필드명 그대로. currency_prc 가 환율이다. */
    private static String quoteJson(String currencyUnit, String currencyPrc) {
        return """
            {"rsp_cd":"00000","rsp_msg":"ok",
             "Output_0":{"iem_cd":"AAPL","kor_name":"애플","trdprc":"185.70",
                         "base_prc":"183.20","netchng_cls":"2","netchng":"2.50",
                         "pctchng":"1.36","currency_unit":"%s","currency_prc":"%s"}}
            """.formatted(currencyUnit, currencyPrc);
    }

    private void expectBalance(String body) {
        server.expect(requestTo(BASE + "/gbstock/inquiry/v1/balance"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    // ---- 1순위: 해외 잔고 ---------------------------------------------------

    @Test
    @DisplayName("잔고가 환율을 주면 시세는 부르지 않는다 — 계좌 평가에 실제 적용된 값이 이긴다")
    void balanceWinsAndDoesNotCallQuote() {
        expectAccountLookup();
        expectBalance(BALANCE_WITH_USD);

        assertThat(sut.getFxRate(USER, "USD")).isEqualByComparingTo(new BigDecimal("1383.50"));

        // 기대한 요청이 전부 나갔고 그 외엔 안 나갔다 — 시세 폴백이 안 붙었다는 뜻이다.
        server.verify();
    }

    // ---- 2순위: 해외 현재가 (이 PR 의 핵심) ---------------------------------

    @Test
    @DisplayName("계좌가 없어도 환율을 구한다 — 예전엔 여기서 null 이었다")
    void fallsBackToQuoteWhenNoAccount() {
        expectEmptyAccountLookup();
        server.expect(requestTo(BASE + "/gbstock/quote/v1/current"))
            .andExpect(method(HttpMethod.POST))
            // 요청은 종목코드 하나뿐이다 — 계좌를 안 태운다는 게 이 폴백의 전부다.
            .andExpect(jsonPath("$.Input_0.iem_cd").value("AAPL"))
            .andExpect(jsonPath("$.Input_0.act_no").doesNotExist())
            .andRespond(withSuccess(quoteJson("USD", "1381.20"), MediaType.APPLICATION_JSON));

        assertThat(sut.getFxRate(USER, "USD")).isEqualByComparingTo(new BigDecimal("1381.20"));
        server.verify();
    }

    @Test
    @DisplayName("계좌는 있는데 USD 보유가 0건이면 시세로 넘어간다")
    void fallsBackToQuoteWhenBalanceHasNoUsdHolding() {
        expectAccountLookup();
        expectBalance(BALANCE_EMPTY);
        server.expect(requestTo(BASE + "/gbstock/quote/v1/current"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(quoteJson("USD", "1379.00"), MediaType.APPLICATION_JSON));

        assertThat(sut.getFxRate(USER, "USD")).isEqualByComparingTo(new BigDecimal("1379.00"));
        server.verify();
    }

    @Test
    @DisplayName("잔고가 업스트림 오류로 터져도 시세로 넘어간다 — 환율 하나 때문에 평가를 멈추지 않는다")
    void fallsBackToQuoteWhenBalanceFails() {
        expectAccountLookup();
        server.expect(requestTo(BASE + "/gbstock/inquiry/v1/balance"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        server.expect(requestTo(BASE + "/gbstock/quote/v1/current"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(quoteJson("USD", "1385.75"), MediaType.APPLICATION_JSON));

        assertThat(sut.getFxRate(USER, "USD")).isEqualByComparingTo(new BigDecimal("1385.75"));
        server.verify();
    }

    // ---- 폴백이 조용히 틀리지 않게 --------------------------------------------

    @Test
    @DisplayName("폴백 종목의 통화가 USD 가 아니면 쓰지 않는다 — 그럴듯한 숫자가 화면에 나가면 아무도 못 잡는다")
    void rejectsQuoteWhenCurrencyDiffers() {
        expectEmptyAccountLookup();
        server.expect(requestTo(BASE + "/gbstock/quote/v1/current"))
            .andExpect(method(HttpMethod.POST))
            // 티커 재사용·거래소 이전으로 엔화 종목이 걸리면 891.5 를 USD 환율로 쓰게 된다.
            .andRespond(withSuccess(quoteJson("JPY", "891.50"), MediaType.APPLICATION_JSON));

        assertThat(sut.getFxRate(USER, "USD")).isNull();
        server.verify();
    }

    @Test
    @DisplayName("currency_prc 가 비면 null — 0 이나 빈 값을 환율로 쓰지 않는다")
    void rejectsQuoteWhenRateFieldIsBlank() {
        expectEmptyAccountLookup();
        server.expect(requestTo(BASE + "/gbstock/quote/v1/current"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(quoteJson("USD", ""), MediaType.APPLICATION_JSON));

        assertThat(sut.getFxRate(USER, "USD")).isNull();
        server.verify();
    }

    @Test
    @DisplayName("폴백 종목을 비우면 시세를 아예 안 부른다 — 설정으로 끌 수 있다")
    void skipsQuoteWhenProbeSymbolIsBlank() {
        properties.setFxProbeSymbol("  ");
        expectEmptyAccountLookup();

        assertThat(sut.getFxRate(USER, "USD")).isNull();

        // 계좌 조회만 나갔다 — 시세 요청은 한 건도 안 나갔다.
        server.verify();
    }

    @Test
    @DisplayName("설정한 폴백 종목이 그대로 요청에 실린다")
    void sendsConfiguredProbeSymbol() {
        properties.setFxProbeSymbol("TSLA");
        expectEmptyAccountLookup();
        server.expect(requestTo(BASE + "/gbstock/quote/v1/current"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.Input_0.iem_cd").value("TSLA"))
            .andRespond(withSuccess(quoteJson("USD", "1382.00"), MediaType.APPLICATION_JSON));

        assertThat(sut.getFxRate(USER, "USD")).isEqualByComparingTo(new BigDecimal("1382.00"));
        server.verify();
    }

    // ---- 미지원 통화는 전과 같다 ---------------------------------------------

    @Test
    @DisplayName("미국 외 통화는 업스트림에 나가지도 않는다 — 폴백이 생겨도 그대로다")
    void nonUsdStillShortCircuits() {
        assertThat(sut.getFxRate(USER, "JPY")).isNull();
        server.verify();
    }
}
