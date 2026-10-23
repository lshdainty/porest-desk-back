package io.groo.damda.toss.service;

import io.groo.damda.securities.client.BrokerTokenManager;
import io.groo.damda.securities.client.BrokerTokenManagers;
import io.groo.damda.securities.service.dto.InvestorTradingDay;
import io.groo.damda.securities.type.SecuritiesBroker;
import io.groo.damda.stock.service.StockMasterResolver;
import io.groo.damda.toss.client.TossApiClient;
import io.groo.damda.toss.config.TossApiClientConfig;
import io.groo.damda.toss.config.TossProperties;
import io.groo.damda.toss.dto.TossAccountDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 13b-0 — 토스 새 경로 셋을 <b>실제 JSON 으로</b> 태운다(가짜 상류 · 실제 키 아님). 서비스 시험이
 * {@code TossApiClient} 를 목으로 바꿔 레코드를 직접 돌려주면 문서 칸 이름과 {@code result} 봉투,
 * {@code individual: null} 역직렬화가 잠기지 않는다. 운영과 같은 RestTemplate 설정을 쓴다.
 */
class TossNewEndpointsJsonTest {

    private static final String BASE = "https://openapi.toss.test";
    private static final long USER = 1L;

    private MockRestServiceServer server;
    private TossQueryServiceImpl sut;

    @BeforeEach
    void setUp() {
        BrokerTokenManagers managers = mock(BrokerTokenManagers.class);
        BrokerTokenManager manager = mock(BrokerTokenManager.class);
        given(managers.of(SecuritiesBroker.TOSS)).willReturn(manager);
        HttpHeaders auth = new HttpHeaders();
        auth.setBearerAuth("tok");
        given(manager.authHeaders(USER)).willReturn(auth);

        TossProperties props = new TossProperties();
        props.setBaseUrl(BASE);
        RestTemplate rt = new TossApiClientConfig().tossRestTemplate(props);
        server = MockRestServiceServer.createServer(rt);
        StockMasterResolver resolver = mock(StockMasterResolver.class);
        given(resolver.resolve((io.groo.damda.stock.type.StockMarket) null, "005930")).willReturn(Optional.empty());
        sut = new TossQueryServiceImpl(new TossApiClient(rt, managers, props), resolver);
    }

    @Test
    @DisplayName("investor-trading — records[].individual/foreigner/institution.netBuyVolume · 당일 잠정의 individual: null")
    void investorTrading() {
        server.expect(requestTo(BASE + "/api/v1/stocks/005930/investor-trading?count=2"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""
                {"result":{"nextUntil":null,"records":[
                  {"date":"2026-10-23","updatedAt":"2026-10-23T10:00:00+09:00","individual":null,
                   "foreigner":{"buyVolume":"100","sellVolume":"400","netBuyVolume":"-300"},
                   "institution":{"buyVolume":"500","sellVolume":"400","netBuyVolume":"100",
                                  "breakdown":null},
                   "otherCorporation":null},
                  {"date":"2026-10-22","updatedAt":"2026-10-22T20:00:00+09:00",
                   "individual":{"buyVolume":"900","sellVolume":"700","netBuyVolume":"200"},
                   "foreigner":{"buyVolume":"1","sellVolume":"51","netBuyVolume":"-50"},
                   "institution":{"buyVolume":"0","sellVolume":"0","netBuyVolume":"0",
                                  "breakdown":{"x":1}},
                   "otherCorporation":{"buyVolume":"1","sellVolume":"1"}}]}}
                """, MediaType.APPLICATION_JSON));

        List<InvestorTradingDay> days = sut.getInvestorTrading(USER, "005930", 2);

        assertThat(days).containsExactly(
            new InvestorTradingDay("2026-10-23", null, "-300", "100"),
            new InvestorTradingDay("2026-10-22", "200", "-50", "0"));
        server.verify();
    }

    @Test
    @DisplayName("buying-power — X-Tossinvest-Account 헤더 · currency 쿼리 · result.cashBuyingPower")
    void buyingPower() {
        server.expect(requestTo(BASE + "/api/v1/buying-power?currency=USD"))
            .andExpect(header("X-Tossinvest-Account", "77"))
            .andRespond(withSuccess("{\"result\":{\"currency\":\"USD\",\"cashBuyingPower\":\"1234.56\"}}",
                MediaType.APPLICATION_JSON));

        assertThat(sut.getBuyingPower(USER, 77L, "USD"))
            .isEqualTo(new TossAccountDto.BuyingPower("USD", "1234.56"));
        server.verify();
    }

    @Test
    @DisplayName("commissions — result 배열 · 해외는 startDate null · 무기한은 endDate null")
    void commissions() {
        server.expect(requestTo(BASE + "/api/v1/commissions"))
            .andExpect(header("X-Tossinvest-Account", "77"))
            .andRespond(withSuccess("""
                {"result":[
                  {"marketCountry":"KR","commissionRate":"0.00015","startDate":"2026-01-01","endDate":"2026-06-30"},
                  {"marketCountry":"US","commissionRate":"0.0025","startDate":null,"endDate":null}]}
                """, MediaType.APPLICATION_JSON));

        assertThat(sut.getCommissions(USER, 77L)).containsExactly(
            new TossAccountDto.Commission("KR", "0.00015", "2026-01-01", "2026-06-30"),
            new TossAccountDto.Commission("US", "0.0025", null, null));
        server.verify();
    }
}
