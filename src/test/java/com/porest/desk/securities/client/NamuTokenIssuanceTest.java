package com.porest.desk.securities.client;

import com.porest.core.exception.ExternalServiceException;
import com.porest.core.type.YNType;
import com.porest.desk.common.crypto.AesGcmCipher;
import com.porest.desk.namu.client.NamuApiClient;
import com.porest.desk.namu.client.dto.NamuEnvelope;
import com.porest.desk.namu.dto.NamuMarketDto;
import com.porest.desk.securities.config.NamuApiClientConfig;
import com.porest.desk.securities.config.NamuProperties;
import com.porest.desk.securities.domain.UserSecuritiesCredential;
import com.porest.desk.securities.repository.UserSecuritiesCredentialRepository;
import com.porest.desk.securities.type.SecuritiesBroker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

/**
 * <b>토큰 발급이 실제로 몇 번 나가는가</b> — 이 작업의 목적이 곧 이 숫자다.
 *
 * <p>나무증권은 액세스 토큰을 재발급할 때마다 사용자 휴대폰으로 알림톡을 보낸다. 나무로부터
 * "계속 재발급되지 않도록 조치해 달라" 는 안내를 받고 만든 테스트라, 여기서 고정하는 것은
 * 동작이 아니라 <b>발급 호출 횟수</b>다. 횟수를 못 박지 않으면 다음 리팩터링에서 조용히 늘어난다.
 *
 * <p>목으로 자바 객체를 만들어 넣지 않는다 — 발급도 조회도 {@link MockRestServiceServer} 로
 * <b>실제 JSON</b>을 태운다(PR #254 교훈). 그래서 토큰 엔드포인트로 나간 HTTP 요청 수를
 * 그대로 셀 수 있다.
 *
 * <p>템플릿이 둘인 것도 그대로 재현한다 — 조회는 환경 도메인, 발급은 <b>항상 운영</b> 도메인이다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NamuTokenIssuanceTest {

    private static final long USER = 1L;
    private static final String API_BASE = "https://moapi.nhplug.test";
    private static final String AUTH_BASE = "https://api.nhplug.test";
    private static final String PRICE_PATH = "/krstock/quote/v1/currentPrice";
    private static final String API_KEY = "APPKEY-AAA";
    private static final String API_SECRET = "S3CRET-VALUE";

    private static final String PRICE_OK = """
        {"rsp_cd":"00000","rsp_msg":"조회가 완료되었습니다.",
         "Output_0":{"iem_cd":"005930","stck_prpr":"70000"}}
        """;

    private static final ParameterizedTypeReference<NamuEnvelope<NamuMarketDto.KrPrice>> KR_PRICE =
        new ParameterizedTypeReference<>() {
        };

    @Mock private UserSecuritiesCredentialRepository credentialRepository;
    @Mock private AesGcmCipher cipher;

    private BrokerTokenStore store;
    private NamuTokenManager tokenManager;
    private NamuApiClient client;
    private MockRestServiceServer authServer;
    private MockRestServiceServer apiServer;
    private AtomicInteger tokenCalls;

    @BeforeEach
    void setUp() {
        NamuProperties properties = new NamuProperties();
        properties.setBaseUrl(API_BASE);
        properties.setAuthBaseUrl(AUTH_BASE);

        NamuApiClientConfig config = new NamuApiClientConfig();
        RestTemplate apiTemplate = config.namuRestTemplate(properties);
        RestTemplate authTemplate = config.namuAuthRestTemplate(properties);
        apiServer = MockRestServiceServer.bindTo(apiTemplate).build();
        authServer = MockRestServiceServer.bindTo(authTemplate).ignoreExpectOrder(true).build();

        tokenCalls = new AtomicInteger();
        store = new InMemoryBrokerTokenStore();

        given(cipher.decrypt("keyEnc")).willReturn(API_KEY);
        given(cipher.decrypt("secretEnc")).willReturn(API_SECRET);
        given(credentialRepository.findByUserRowIdAndBrokerAndIsDeletedAndIsVerified(
            USER, SecuritiesBroker.NAMU, YNType.N, YNType.Y))
            .willReturn(Optional.of(UserSecuritiesCredential.verified(
                USER, SecuritiesBroker.NAMU, "keyEnc", "secretEnc", LocalDateTime.now())));

        tokenManager = new NamuTokenManager(credentialRepository, cipher, store, authTemplate);
        client = new NamuApiClient(apiTemplate, new BrokerTokenManagers(List.of(tokenManager)), properties);

        authServer.expect(ExpectedCount.manyTimes(), tokenRequest()).andRespond(issueToken());
    }

    /** 발급은 쿼리 파라미터를 달고 나가므로 경로로만 건다. */
    private static RequestMatcher tokenRequest() {
        return request -> assertThat(request.getURI().toString()).startsWith(AUTH_BASE + "/oauth2/token");
    }

    /** 발급 응답. 호출될 때마다 토큰 문자열이 달라져 "몇 번째 토큰" 인지 눈에 보인다. */
    private ResponseCreator issueToken() {
        return request -> {
            int n = tokenCalls.incrementAndGet();
            return withSuccess("{\"access_token\":\"tok-" + n + "\",\"token_type\":\"Bearer\","
                + "\"expires_in\":86400}", MediaType.APPLICATION_JSON).createResponse(request);
        };
    }

    private void expectPrice(ExpectedCount count, ResponseCreator response) {
        apiServer.expect(count, requestTo(API_BASE + PRICE_PATH)).andRespond(response);
    }

    private String price() {
        return client.postObject(USER, PRICE_PATH, Map.of("iem_cd", "005930"), KR_PRICE).price();
    }

    @Test
    @DisplayName("연속 20번 조회해도 발급은 1회다 — 캐시가 살아 있으면 업스트림 토큰 호출이 없다")
    void cachedTokenIsReusedAcrossCalls() {
        expectPrice(ExpectedCount.times(20), withSuccess(PRICE_OK, MediaType.APPLICATION_JSON));

        for (int i = 0; i < 20; i++) {
            assertThat(price()).isEqualTo("70000");
        }

        assertThat(tokenCalls).hasValue(1);
        apiServer.verify();
    }

    @Test
    @DisplayName("429(호출 한도 초과)를 20번 맞아도 발급은 1회다 — 429 는 토큰 문제가 아니다")
    void rateLimitNeverReissues() {
        expectPrice(ExpectedCount.times(20), withStatus(HttpStatus.TOO_MANY_REQUESTS)
            .body("{\"rsp_cd\":\"IGW42902\",\"rsp_msg\":\"유량 제한을 초과하였습니다.\"}")
            .contentType(MediaType.APPLICATION_JSON));

        for (int i = 0; i < 20; i++) {
            assertThatThrownBy(NamuTokenIssuanceTest.this::price).isInstanceOf(ExternalServiceException.class);
        }

        assertThat(tokenCalls).hasValue(1);
        assertThat(store.get(SecuritiesBroker.NAMU, USER)).contains("tok-1");
    }

    @Test
    @DisplayName("업무 오류(rsp_cd=11165)를 20번 맞아도 발급은 1회다 — 200 으로 온 실패에 토큰을 버리지 않는다")
    void businessErrorNeverReissues() {
        expectPrice(ExpectedCount.times(20), withSuccess(
            "{\"rsp_cd\":\"11165\",\"rsp_msg\":\"계좌번호를 잘못 입력하셨습니다.\",\"Output_0\":null}",
            MediaType.APPLICATION_JSON));

        for (int i = 0; i < 20; i++) {
            assertThatThrownBy(NamuTokenIssuanceTest.this::price).isInstanceOf(ExternalServiceException.class);
        }

        assertThat(tokenCalls).hasValue(1);
        assertThat(store.get(SecuritiesBroker.NAMU, USER)).contains("tok-1");
    }

    @Test
    @DisplayName("네트워크 오류가 반복돼도 발급은 1회이고 토큰은 살아 있다 — 타임아웃은 토큰 문제가 아니다")
    void networkErrorNeverReissues() {
        expectPrice(ExpectedCount.times(10), request -> {
            throw new java.net.SocketTimeoutException("Read timed out");
        });

        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(NamuTokenIssuanceTest.this::price).isInstanceOf(ExternalServiceException.class);
        }

        assertThat(tokenCalls).hasValue(1);
        assertThat(store.get(SecuritiesBroker.NAMU, USER)).contains("tok-1");
    }

    @Test
    @DisplayName("401 이면 새 토큰으로 1회만 재시도한다 — 재시도 요청이 새 토큰을 달고 나간다")
    void unauthorizedReissuesOnceAndRetries() {
        // 어제 발급받아 캐시에 남아 있던 토큰이 폐기된 상황.
        store.put(SecuritiesBroker.NAMU, USER, "yesterday", 86400L);
        apiServer.expect(requestTo(API_BASE + PRICE_PATH))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer yesterday"))
            .andRespond(withUnauthorizedRequest());
        apiServer.expect(requestTo(API_BASE + PRICE_PATH))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer tok-1"))
            .andRespond(withSuccess(PRICE_OK, MediaType.APPLICATION_JSON));

        assertThat(price()).isEqualTo("70000");

        assertThat(tokenCalls).hasValue(1);
        apiServer.verify();
    }

    @Test
    @DisplayName("401 이 지속돼도 발급은 1회다 — 종목 30개 폴링에 31회 나가던 증폭을 쿨다운이 막는다")
    void persistentUnauthorizedDoesNotAmplify() {
        store.put(SecuritiesBroker.NAMU, USER, "yesterday", 86400L);
        // 첫 건은 재발급 + 재시도로 2회, 나머지 29건은 1회씩.
        expectPrice(ExpectedCount.times(31), withUnauthorizedRequest());

        for (int i = 0; i < 30; i++) {
            assertThatThrownBy(NamuTokenIssuanceTest.this::price).isInstanceOf(ExternalServiceException.class);
        }

        // 예전 구조라면 종목 수 + 1 = 31회, 10초마다 그만큼 알림톡이 나갔다.
        assertThat(tokenCalls).hasValue(1);
        apiServer.verify();
    }

    @Test
    @DisplayName("키 등록 + 첫 조회로 발급은 1회다 — 검증 발급을 버리지 않는다(예전엔 2회)")
    void registerThenCallIssuesOnce() {
        expectPrice(ExpectedCount.once(), withSuccess(PRICE_OK, MediaType.APPLICATION_JSON));

        tokenManager.verifyAndCache(USER, API_KEY, API_SECRET);
        assertThat(price()).isEqualTo("70000");

        assertThat(tokenCalls).hasValue(1);
        apiServer.verify();
    }

    @Test
    @DisplayName("발급 요청은 항상 운영 도메인으로 나간다 — 조회가 모의투자여도 인증은 운영이다")
    void tokenIsAlwaysIssuedFromLiveDomain() {
        authServer.reset();
        authServer.expect(ExpectedCount.once(), tokenRequest())
            .andExpect(request -> assertThat(request.getURI().getQuery()).contains("scope=oob"))
            .andRespond(issueToken());

        assertThat(tokenManager.getAccessToken(USER)).isEqualTo("tok-1");

        authServer.verify();
    }
}
