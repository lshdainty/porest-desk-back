package com.porest.desk.namu.service;

import com.porest.core.exception.InvalidValueException;
import com.porest.desk.namu.client.NamuApiClient;
import com.porest.desk.namu.dto.NamuAccountDto;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 해외 잔고 <b>요청 본문</b>을 고정한다 — 실제 JSON 을 태워서.
 *
 * <p>다른 서비스 테스트는 {@link NamuApiClient} 를 목으로 두는데, 그러면 이 버그를 못 잡는다.
 * {@code fc_sec_trd_nat_cd} 가 {@code ""} 였을 때 <b>목은 아무 불평도 하지 않았고</b> 테스트는
 * 전부 초록이었다. 실제로는 나무가 에러 대신 0건을 돌려줘 해외 잔고가 통째로 비어 보였다
 * (dev 실측 2026-08-26). 그래서 여기서는 서비스 → 클라이언트 → RestTemplate 을 실제로 잇고
 * <b>바이트로 나가는 값</b>을 본다.
 *
 * <p>{@code ""} 로 되돌리면 이 테스트가 깨진다. 그게 목적이다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NamuGbBalanceRequestTest {

    private static final String BASE = "https://api.nhplug.com:8443";
    private static final long USER = 1L;
    /** 실계좌(acct_type=01). 나무 계좌번호는 11자리다. */
    private static final String LIVE_ACCT = "33333333301";

    @Mock private BrokerTokenManagers tokenManagers;
    @Mock private BrokerTokenManager tokenManager;
    @Mock private StockMasterRepository stockMasterRepository;

    private MockRestServiceServer server;
    private NamuQueryServiceImpl sut;

    @BeforeEach
    void setUp() {
        given(tokenManagers.of(SecuritiesBroker.NAMU)).willReturn(tokenManager);
        HttpHeaders auth = new HttpHeaders();
        auth.setBearerAuth("tok");
        given(tokenManager.authHeaders(USER)).willReturn(auth);

        NamuProperties properties = new NamuProperties();
        properties.setBaseUrl(BASE);
        properties.setEnvironment(NamuEnvironment.LIVE);

        // 운영과 같은 RestTemplate 을 쓴다 — 컨버터 설정 차이로 테스트만 통과하는 일이 없게.
        RestTemplate restTemplate = new NamuApiClientConfig().namuRestTemplate(properties);
        server = MockRestServiceServer.createServer(restTemplate);
        sut = new NamuQueryServiceImpl(new NamuApiClient(restTemplate, tokenManagers, properties),
            new StockMasterResolver(stockMasterRepository), properties);
    }

    /** 잔고 조회는 계좌 목록을 먼저 부른다. 실계좌 1건만 준다. */
    private void expectAccountLookup() {
        server.expect(requestTo(BASE + "/n2/acctinfo"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""
                {"rsp_cd":"00000","rsp_msg":"ok",
                 "Output_0":[{"acct_no":"%s","acct_type":"01"}]}
                """.formatted(LIVE_ACCT), MediaType.APPLICATION_JSON));
    }

    private static final String GB_BALANCE_JSON = """
        {"rsp_cd":"00000","rsp_msg":"ok",
         "Output_0":{"fc_eal_amt":"1500.00","fc_eal_pls_amt":"300.00","pft_rt":"25.00"},
         "Output_1":[{"iem_cd":"AAPL","iem_nm":"애플","oss_iem_eng_nm":"APPLE INC",
                      "cns_bse_bnc_qty":"5","fc_avg_phs_pr":"180.00","fc_sec_end_pr":"185.70",
                      "fc_eal_amt":"928.50","fc_eal_pls_amt":"28.50",
                      "cur_cd":"USD","tdt_sby_bse_xcg_rt":"1383.50"}]}
        """;

    @Test
    @DisplayName("해외 잔고 요청에 fc_sec_trd_nat_cd=200(미국)이 실린다 — 빈 문자열은 0건을 부른다")
    void sendsUnitedStatesNationCode() {
        expectAccountLookup();
        server.expect(requestTo(BASE + "/gbstock/inquiry/v1/balance"))
            .andExpect(method(HttpMethod.POST))
            // 이 한 줄이 이 PR 의 전부다. "" 로 되돌아가면 여기서 깨진다.
            .andExpect(jsonPath("$.Input_0.fc_sec_trd_nat_cd").value("200"))
            .andExpect(jsonPath("$.Input_0.act_no").value(LIVE_ACCT))
            .andExpect(jsonPath("$.Input_0.cur_cd").value("USD"))
            .andExpect(jsonPath("$.Input_0.qut_iqr_dit_cd").value("1"))
            .andExpect(jsonPath("$.Input_0.xns_dit_cd").value("1"))
            .andRespond(withSuccess(GB_BALANCE_JSON, MediaType.APPLICATION_JSON));

        NamuAccountDto.Holdings holdings = sut.getHoldings(USER, null, "USD");

        assertThat(holdings.items()).singleElement()
            .satisfies(h -> assertThat(h.symbol()).isEqualTo("AAPL"));
        assertThat(holdings.currency()).isEqualTo("USD");
        server.verify();
    }

    @Test
    @DisplayName("환율도 같은 요청을 쓴다 — 국가코드가 살아나면 환율이 함께 살아난다")
    void fxRateRidesTheSameRequest() {
        expectAccountLookup();
        server.expect(requestTo(BASE + "/gbstock/inquiry/v1/balance"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.Input_0.fc_sec_trd_nat_cd").value("200"))
            .andRespond(withSuccess(GB_BALANCE_JSON, MediaType.APPLICATION_JSON));

        // 환율은 요약이 아니라 종목 행(tdt_sby_bse_xcg_rt)에서만 나온다. 잔고가 0건이면
        // 환율도 없다 — 그래서 이 버그는 해외 잔고와 환율을 동시에 죽이고 있었다.
        assertThat(sut.getFxRate(USER, "USD")).isEqualByComparingTo(new BigDecimal("1383.50"));
        server.verify();
    }

    @Test
    @DisplayName("미국 외 통화는 400 으로 거절한다 — 업스트림에 나가지도 않는다")
    void rejectsNonUsdCurrencyBeforeCallingUpstream() {
        expectAccountLookup();

        assertThatThrownBy(() -> sut.getHoldings(USER, null, "JPY"))
            .isInstanceOf(InvalidValueException.class);

        // 잔고 요청은 한 건도 안 나갔다 — 계좌 조회만 나갔다.
        server.verify();
    }

    @Test
    @DisplayName("미국 외 통화 환율은 던지지 않고 null 로 접는다 — 자산 평가를 멈추면 안 된다")
    void foldsNonUsdFxRateToNull() {
        // 계좌 조회조차 안 나간다 — 통화를 먼저 본다.
        assertThat(sut.getFxRate(USER, "JPY")).isNull();
        server.verify();
    }
}
