package io.groo.damda.toss.client;

import io.groo.core.exception.ExternalServiceException;
import io.groo.damda.common.exception.DeskErrorCode;
import io.groo.damda.securities.client.BrokerTokenManager;
import io.groo.damda.securities.client.BrokerTokenManagers;
import io.groo.damda.securities.type.SecuritiesBroker;
import io.groo.damda.toss.client.dto.TossEnvelope;
import io.groo.damda.toss.config.TossApiClientConfig;
import io.groo.damda.toss.config.TossProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 업스트림 오류 → 에러 코드 변환. 401·403(토큰 만료 · 키 무효)은 {@code SECURITIES_AUTH_EXPIRED},
 * 일시 장애(5xx · 타임아웃 등)는 {@code SECURITIES_API_ERROR} 로 구분되어야 한다.
 */
@DisplayName("TossApiClient 업스트림 오류 변환")
class TossApiClientTest {

    private static final String BASE = "https://toss.test";
    private static final String PATH = "/api/v1/market-info";
    private static final long USER = 1L;
    private static final ParameterizedTypeReference<TossEnvelope<String>> TYPE = new ParameterizedTypeReference<>() {};

    private BrokerTokenManager manager;
    private MockRestServiceServer server;
    private TossApiClient sut;

    @BeforeEach
    void setUp() {
        BrokerTokenManagers managers = mock(BrokerTokenManagers.class);
        manager = mock(BrokerTokenManager.class);
        given(managers.of(SecuritiesBroker.TOSS)).willReturn(manager);
        HttpHeaders auth = new HttpHeaders();
        auth.setBearerAuth("tok");
        given(manager.authHeaders(USER)).willReturn(auth);

        TossProperties props = new TossProperties();
        props.setBaseUrl(BASE);
        RestTemplate rt = new TossApiClientConfig().tossRestTemplate(props);
        server = MockRestServiceServer.createServer(rt);
        sut = new TossApiClient(rt, managers, props);
    }

    private Throwable call() {
        return catchThrowable(() -> sut.get(USER, PATH, null, TYPE));
    }

    private static void assertCode(Throwable t, DeskErrorCode expected) {
        assertThat(t).isInstanceOf(ExternalServiceException.class);
        assertThat(((ExternalServiceException) t).getErrorCode()).isEqualTo(expected);
    }

    @Test
    @DisplayName("401 이 재발급 재시도 뒤에도 401 이면 인증 만료 코드다")
    void unauthorizedAfterRetryIsAuthExpired() {
        server.expect(requestTo(BASE + PATH)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(BASE + PATH)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertCode(call(), DeskErrorCode.SECURITIES_AUTH_EXPIRED);
        verify(manager, times(1)).invalidate(USER);   // 재시도는 한 번뿐
        server.verify();
    }

    @Test
    @DisplayName("401 뒤 재시도가 성공하면 오류가 아니다")
    void unauthorizedThenSuccess() {
        server.expect(requestTo(BASE + PATH)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(BASE + PATH)).andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"result\":\"ok\"}", MediaType.APPLICATION_JSON));

        assertThat(sut.get(USER, PATH, null, TYPE)).isEqualTo("ok");
        server.verify();
    }

    @Test
    @DisplayName("403 은 재시도 없이 바로 인증 만료 코드다")
    void forbiddenIsAuthExpired() {
        server.expect(requestTo(BASE + PATH)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertCode(call(), DeskErrorCode.SECURITIES_AUTH_EXPIRED);
        verify(manager, never()).invalidate(USER);
        server.verify();
    }

    @Test
    @DisplayName("본문에 토스 에러 코드가 있어도 상태가 우선이다 — 401 본문이 무엇이든 만료, 500 본문이 무엇이든 API 오류")
    void statusWinsOverBodyCode() {
        String body = "{\"error\":{\"requestId\":\"r1\",\"code\":\"order-not-found\",\"message\":\"x\"}}";
        server.expect(requestTo(BASE + PATH)).andRespond(withStatus(HttpStatus.FORBIDDEN)
            .contentType(MediaType.APPLICATION_JSON).body(body));
        assertCode(call(), DeskErrorCode.SECURITIES_AUTH_EXPIRED);

        server.reset();
        server.expect(requestTo(BASE + PATH)).andRespond(withServerError()
            .contentType(MediaType.APPLICATION_JSON).body("{\"error\":{\"code\":\"unauthorized\",\"message\":\"x\"}}"));
        assertCode(call(), DeskErrorCode.SECURITIES_API_ERROR);
    }

    @Test
    @DisplayName("본문이 깨져 있어도 401·403 은 인증 만료 코드다")
    void unparsableBodyStillMapsByStatus() {
        server.expect(requestTo(BASE + PATH)).andRespond(withStatus(HttpStatus.FORBIDDEN)
            .contentType(MediaType.TEXT_PLAIN).body("not json"));

        assertCode(call(), DeskErrorCode.SECURITIES_AUTH_EXPIRED);
    }

    @Test
    @DisplayName("5xx 는 기존 그대로 API 오류 코드다")
    void serverErrorStaysApiError() {
        server.expect(requestTo(BASE + PATH)).andRespond(withServerError());

        assertCode(call(), DeskErrorCode.SECURITIES_API_ERROR);
        verify(manager, never()).invalidate(USER);
    }

    @Test
    @DisplayName("429 · 400 같은 그 밖의 4xx 도 기존 그대로 API 오류 코드다")
    void otherClientErrorsStayApiError() {
        server.expect(requestTo(BASE + PATH)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertCode(call(), DeskErrorCode.SECURITIES_API_ERROR);

        server.reset();
        server.expect(requestTo(BASE + PATH)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        assertCode(call(), DeskErrorCode.SECURITIES_API_ERROR);
    }

    @Test
    @DisplayName("연결 실패 · 타임아웃(RestClientException)은 기존 그대로 API 오류 코드다")
    void ioErrorStaysApiError() {
        server.expect(requestTo(BASE + PATH))
            .andRespond(request -> { throw new java.io.IOException("timeout"); });

        assertCode(call(), DeskErrorCode.SECURITIES_API_ERROR);
    }

    @Test
    @DisplayName("getForUser · getPath 도 같은 변환을 탄다 — 공통 지점은 execute")
    void otherEntryPointsShareTheMapping() {
        server.expect(requestTo(BASE + "/api/v1/accounts")).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertCode(catchThrowable(() -> sut.getForUser(USER, "/api/v1/accounts", null, 7L, TYPE)),
            DeskErrorCode.SECURITIES_AUTH_EXPIRED);

        server.reset();
        server.expect(requestTo(BASE + "/api/v1/stocks/005930/warnings")).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertCode(catchThrowable(() -> sut.getPath(USER, "/api/v1/stocks/{symbol}/warnings",
            java.util.Map.of("symbol", "005930"), TYPE)), DeskErrorCode.SECURITIES_AUTH_EXPIRED);
    }
}
