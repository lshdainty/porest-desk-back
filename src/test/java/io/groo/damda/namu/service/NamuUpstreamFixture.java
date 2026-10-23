package io.groo.damda.namu.service;

import io.groo.damda.namu.client.NamuApiClient;
import io.groo.damda.securities.client.BrokerTokenManager;
import io.groo.damda.securities.client.BrokerTokenManagers;
import io.groo.damda.securities.config.NamuApiClientConfig;
import io.groo.damda.securities.config.NamuProperties;
import io.groo.damda.securities.type.SecuritiesBroker;
import io.groo.damda.stock.client.dto.InstrumentRecord;
import io.groo.damda.stock.domain.StockMaster;
import io.groo.damda.stock.repository.StockMasterRepository;
import io.groo.damda.stock.service.StockMasterResolver;
import io.groo.damda.stock.type.MasterSource;
import io.groo.damda.stock.type.StockMarket;
import io.groo.damda.stock.type.StockSecurityType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 13b-0 시험이 쓰는 <b>가짜 상류</b> — 실제 증권사 키로 부르지 않는다. 운영과 같은 RestTemplate 에
 * {@link MockRestServiceServer} 를 붙이고, 진짜 {@link NamuApiClient} 와 {@link StockMasterResolver}
 * (저장소만 목)를 쓴다. 그래서 실제 JSON 이 Jackson 을 지난다.
 */
final class NamuUpstreamFixture {

    static final String BASE = "https://api.nhplug.com:8443";

    final MockRestServiceServer server;
    final NamuApiClient client;
    final NamuProperties properties = new NamuProperties();
    final StockMasterRepository stockMasterRepository = mock(StockMasterRepository.class);
    final StockMasterResolver resolver = new StockMasterResolver(stockMasterRepository);

    NamuUpstreamFixture(long... users) {
        BrokerTokenManagers tokenManagers = mock(BrokerTokenManagers.class);
        BrokerTokenManager tokenManager = mock(BrokerTokenManager.class);
        given(tokenManagers.of(SecuritiesBroker.NAMU)).willReturn(tokenManager);
        for (long user : users) {
            HttpHeaders auth = new HttpHeaders();
            auth.setBearerAuth("tok-" + user);
            given(tokenManager.authHeaders(user)).willReturn(auth);
        }
        properties.setBaseUrl(BASE);
        RestTemplate restTemplate = new NamuApiClientConfig().namuRestTemplate(properties);
        server = MockRestServiceServer.createServer(restTemplate);
        client = new NamuApiClient(restTemplate, tokenManagers, properties);
    }

    ResponseActions expect(String path) {
        return server.expect(requestTo(BASE + path)).andExpect(method(HttpMethod.POST));
    }

    static void respond(ResponseActions actions, String json) {
        actions.andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    void givenMaster(String symbol, StockMarket market, StockSecurityType type, String currency) {
        StockMaster stock = StockMaster.create(MasterSource.KIS, InstrumentRecord.kis(
            market, symbol, null, null, symbol, symbol, type, currency));
        given(stockMasterRepository.findAllActiveBySymbol(symbol)).willReturn(List.of(stock));
    }
}
