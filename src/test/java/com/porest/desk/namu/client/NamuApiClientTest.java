package com.porest.desk.namu.client;

import com.porest.core.exception.ExternalServiceException;
import com.porest.desk.namu.client.dto.NamuEnvelope;
import com.porest.desk.namu.client.dto.NamuListEnvelope;
import com.porest.desk.namu.client.dto.NamuPagedEnvelope;
import com.porest.desk.namu.dto.NamuAccountDto;
import com.porest.desk.namu.dto.NamuMarketDto;
import com.porest.desk.securities.client.BrokerTokenManager;
import com.porest.desk.securities.client.BrokerTokenManagers;
import com.porest.desk.securities.config.NamuApiClientConfig;
import com.porest.desk.securities.config.NamuProperties;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

/**
 * 나무 저수준 호출 — <b>실제 JSON 을 태워서</b> 본다.
 *
 * <p>이전 테스트는 RestTemplate 을 목으로 두고 record 를 자바에서 직접 만들어 넣었다.
 * 그래서 Jackson 이 개입할 자리가 없었고, {@code Output_0} 을 배열로 잘못 선언해 시세·잔고가
 * 전부 역직렬화 실패하던 것을 <b>한 건도 못 잡았다.</b> 그 사고를 다시 겪지 않으려고
 * MockRestServiceServer 로 진짜 응답 본문을 흘려 보낸다.
 *
 * <p>NH 스펙은 {@code Output_0} 이 API 마다 객체이거나 배열이다. 우리가 쓰는 응답 모양
 * 세 가지(객체 / 배열 / 요약+목록)를 모두 고정한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NamuApiClientTest {

    private static final String BASE = "https://api.nhplug.com:8443";

    private static final ParameterizedTypeReference<NamuEnvelope<NamuMarketDto.KrPrice>> KR_PRICE =
        new ParameterizedTypeReference<>() {
        };
    private static final ParameterizedTypeReference<NamuListEnvelope<NamuAccountDto.Account>> ACCOUNTS =
        new ParameterizedTypeReference<>() {
        };
    private static final ParameterizedTypeReference<
        NamuPagedEnvelope<NamuAccountDto.GbBalanceSummary, NamuAccountDto.GbHolding>> GB_BALANCE =
        new ParameterizedTypeReference<>() {
        };

    @Mock private BrokerTokenManagers tokenManagers;
    @Mock private BrokerTokenManager tokenManager;

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private NamuApiClient sut;

    @BeforeEach
    void setUp() {
        given(tokenManagers.of(SecuritiesBroker.NAMU)).willReturn(tokenManager);
        HttpHeaders auth = new HttpHeaders();
        auth.setBearerAuth("tok");
        auth.set("x-client-id", "KEY");
        auth.set("x-client-secret", "SECRET");
        given(tokenManager.authHeaders(1L)).willReturn(auth);
        given(tokenManager.invalidateOnUnauthorized(1L)).willReturn(true);

        NamuProperties properties = new NamuProperties();
        properties.setBaseUrl(BASE);
        // 운영과 같은 RestTemplate 을 쓴다 — 컨버터·UriTemplateHandler 설정 차이로
        // 테스트만 통과하는 일이 없게.
        restTemplate = new NamuApiClientConfig().namuRestTemplate(properties);
        server = MockRestServiceServer.createServer(restTemplate);
        sut = new NamuApiClient(restTemplate, tokenManagers, properties);
    }

    private void expect(String path, String responseJson) {
        server.expect(requestTo(BASE + path))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("Output_0 이 객체인 시세 응답을 그대로 읽는다 — 배열로 선언했던 탓에 전부 실패하던 자리")
    void objectOutputIsDeserialized() {
        expect("/krstock/quote/v1/currentPrice", """
            {"rsp_cd":"00000","rsp_msg":"조회가 완료되었습니다.",
             "Output_0":{"iem_cd":"005930","stck_prpr":"70000",
                         "prdy_vrss_sign":"2","prdy_vrss":"500","prdy_ctrt":"0.72"}}
            """);

        NamuMarketDto.KrPrice p = sut.postObject(1L, "/krstock/quote/v1/currentPrice",
            Map.of("market_cd", "KRX", "iem_cd", "005930"), KR_PRICE);

        assertThat(p).isNotNull();
        assertThat(p.price()).isEqualTo("70000");
        assertThat(p.changeRate()).isEqualTo("0.72");
        server.verify();
    }

    @Test
    @DisplayName("Output_0 이 배열인 계좌목록도 읽는다 — 우리가 쓰는 것 중 유일한 배열")
    void arrayOutputIsDeserialized() {
        expect("/n2/acctinfo", """
            {"rsp_cd":"00000","rsp_msg":"ok","cust_no":"1234",
             "Output_0":[{"acct_no":"12345678-01","acct_type":"01"},
                         {"acct_no":"12345678-02","acct_type":"22"}]}
            """);

        List<NamuAccountDto.Account> accounts = sut.postList(1L, "/n2/acctinfo", Map.of(), ACCOUNTS);

        assertThat(accounts).hasSize(2);
        assertThat(accounts.get(0).accountNo()).isEqualTo("12345678-01");
        server.verify();
    }

    @Test
    @DisplayName("요약(객체) + 목록(배열)이 함께 오는 잔고 응답을 읽는다. 환율은 종목 행에 있다")
    void pagedOutputIsDeserialized() {
        expect("/gbstock/inquiry/v1/balance", """
            {"rsp_cd":"00000","rsp_msg":"ok",
             "Output_0":{"tot_aet_amt":"2000000","fc_eal_amt":"1500.00",
                         "fc_eal_pls_amt":"300.00","pft_rt":"25.00"},
             "Output_1":[{"iem_cd":"AAPL","iem_nm":"애플","oss_iem_eng_nm":"APPLE INC",
                          "cns_bse_bnc_qty":"5","fc_avg_phs_pr":"180.00",
                          "fc_sec_end_pr":"185.70","fc_eal_amt":"928.50",
                          "fc_eal_pls_amt":"28.50","krw_eal_amt":"1284000",
                          "cur_cd":"USD","tdt_sby_bse_xcg_rt":"1383.50"}]}
            """);

        NamuPagedEnvelope<NamuAccountDto.GbBalanceSummary, NamuAccountDto.GbHolding> res =
            sut.exchange(1L, "/gbstock/inquiry/v1/balance", Map.of("act_no", "12345678-01"), GB_BALANCE);

        assertThat(res.summary()).isNotNull();
        assertThat(res.summary().evalAmountSum()).isEqualTo("1500.00");
        assertThat(res.items()).singleElement().satisfies(h -> {
            assertThat(h.symbol()).isEqualTo("AAPL");
            // 환율·통화는 요약이 아니라 종목 행에 있다 — 종목마다 통화가 달라서다.
            assertThat(h.currency()).isEqualTo("USD");
            assertThat(h.baseExchangeRate()).isEqualTo("1383.50");
        });
        server.verify();
    }

    @Test
    @DisplayName("200 이어도 rsp_cd 가 00000 이 아니면 실패다 — 이 검사가 빠지면 조용히 빈 화면이 된다")
    void failsWhenResponseCodeIsNotSuccess() {
        expect("/krstock/quote/v1/currentPrice",
            "{\"rsp_cd\":\"40010\",\"rsp_msg\":\"권한이 없습니다.\",\"Output_0\":null}");

        assertThatThrownBy(() -> sut.postObject(1L, "/krstock/quote/v1/currentPrice",
                Map.of("iem_cd", "005930"), KR_PRICE))
            .isInstanceOf(ExternalServiceException.class);
    }

    @Test
    @DisplayName("정상인데 Output_0 이 null 인 것은 실패가 아니다 — 조회 결과 없음일 뿐")
    void nullOutputOnSuccessIsNotFailure() {
        expect("/krstock/quote/v1/currentPrice",
            "{\"rsp_cd\":\"00000\",\"rsp_msg\":\"ok\",\"Output_0\":null}");

        assertThat(sut.postObject(1L, "/krstock/quote/v1/currentPrice",
            Map.of("iem_cd", "000000"), KR_PRICE)).isNull();
    }

    @Test
    @DisplayName("모르는 필드가 늘어도 깨지지 않는다 — 나무가 응답 필드를 추가할 수 있다")
    void unknownFieldsAreTolerated() {
        expect("/krstock/quote/v1/currentPrice", """
            {"rsp_cd":"00000","rsp_msg":"ok","some_new_field":"x",
             "Output_0":{"iem_cd":"005930","stck_prpr":"70000","brand_new":"y"}}
            """);

        assertThat(sut.postObject(1L, "/krstock/quote/v1/currentPrice",
            Map.of("iem_cd", "005930"), KR_PRICE).price()).isEqualTo("70000");
    }

    @Test
    @DisplayName("요청은 Input_0 봉투에 담겨 POST 로 나가고 인증 헤더가 붙는다")
    void wrapsRequestInInputEnvelope() {
        server.expect(requestTo(BASE + "/krstock/quote/v1/currentPrice"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.Input_0.iem_cd").value("005930"))
            .andExpect(jsonPath("$.iem_cd").doesNotExist())
            .andRespond(withSuccess("{\"rsp_cd\":\"00000\",\"rsp_msg\":\"ok\",\"Output_0\":null}",
                MediaType.APPLICATION_JSON));

        sut.postObject(1L, "/krstock/quote/v1/currentPrice", Map.of("iem_cd", "005930"), KR_PRICE);
        server.verify();
    }

    @Test
    @DisplayName("401 이면 토큰을 비우고 1회만 재시도한다 — 무한루프가 되면 안 된다")
    void retriesOnceOnUnauthorized() {
        server.expect(requestTo(BASE + "/krstock/quote/v1/currentPrice"))
            .andRespond(withUnauthorizedRequest());
        server.expect(requestTo(BASE + "/krstock/quote/v1/currentPrice"))
            .andRespond(withSuccess("{\"rsp_cd\":\"00000\",\"rsp_msg\":\"ok\","
                + "\"Output_0\":{\"stck_prpr\":\"70000\"}}", MediaType.APPLICATION_JSON));

        assertThat(sut.postObject(1L, "/krstock/quote/v1/currentPrice",
            Map.of("iem_cd", "005930"), KR_PRICE).price()).isEqualTo("70000");

        org.mockito.Mockito.verify(tokenManager).invalidateOnUnauthorized(1L);
        server.verify();
    }

    @Test
    @DisplayName("토큰 담당자가 재발급을 거절하면 재시도하지 않는다 — 방금 발급한 토큰이면 또 401 일 뿐이다")
    void doesNotRetryWhenReissueRefused() {
        given(tokenManager.invalidateOnUnauthorized(1L)).willReturn(false);
        server.expect(requestTo(BASE + "/krstock/quote/v1/currentPrice"))
            .andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> sut.postObject(1L, "/krstock/quote/v1/currentPrice",
                Map.of("iem_cd", "005930"), KR_PRICE))
            .isInstanceOf(ExternalServiceException.class);

        // 호출은 한 번뿐 — 재시도가 나갔으면 남은 기대가 없어 여기서 터진다.
        server.verify();
    }

    @Test
    @DisplayName("429(호출 한도 초과)에는 토큰을 건드리지 않는다 — 한도는 토큰 문제가 아니다")
    void rateLimitDoesNotTouchToken() {
        server.expect(requestTo(BASE + "/krstock/quote/v1/currentPrice"))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .body("{\"rsp_cd\":\"IGW42902\",\"rsp_msg\":\"유량 제한\"}")
                .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> sut.postObject(1L, "/krstock/quote/v1/currentPrice",
                Map.of("iem_cd", "005930"), KR_PRICE))
            .isInstanceOf(ExternalServiceException.class);

        org.mockito.Mockito.verify(tokenManager, org.mockito.Mockito.never()).invalidateOnUnauthorized(1L);
        org.mockito.Mockito.verify(tokenManager, org.mockito.Mockito.never()).invalidate(1L);
        server.verify();
    }

    @Test
    @DisplayName("업무 오류(rsp_cd)에도 토큰을 건드리지 않는다 — 200 으로 온 실패는 토큰 문제가 아니다")
    void businessErrorDoesNotTouchToken() {
        expect("/krstock/inquiry/v1/balance",
            "{\"rsp_cd\":\"11165\",\"rsp_msg\":\"계좌번호를 잘못 입력하셨습니다.\",\"Output_0\":null}");

        assertThatThrownBy(() -> sut.postObject(1L, "/krstock/inquiry/v1/balance",
                Map.of("act_no", "12345678-01"), KR_PRICE))
            .isInstanceOf(ExternalServiceException.class);

        org.mockito.Mockito.verify(tokenManager, org.mockito.Mockito.never()).invalidateOnUnauthorized(1L);
        org.mockito.Mockito.verify(tokenManager, org.mockito.Mockito.never()).invalidate(1L);
    }

    @Test
    @DisplayName("네트워크 오류에도 토큰을 건드리지 않는다 — 타임아웃은 토큰 문제가 아니다")
    void networkErrorDoesNotTouchToken() {
        server.expect(requestTo(BASE + "/krstock/quote/v1/currentPrice"))
            .andRespond(request -> {
                throw new java.net.SocketTimeoutException("Read timed out");
            });

        assertThatThrownBy(() -> sut.postObject(1L, "/krstock/quote/v1/currentPrice",
                Map.of("iem_cd", "005930"), KR_PRICE))
            .isInstanceOf(ExternalServiceException.class);

        org.mockito.Mockito.verify(tokenManager, org.mockito.Mockito.never()).invalidateOnUnauthorized(1L);
        org.mockito.Mockito.verify(tokenManager, org.mockito.Mockito.never()).invalidate(1L);
    }

    @Test
    @DisplayName("00166(잔고·자산현황)은 성공이다 — 공식 SDK 가 성공으로 두는 코드")
    void balanceSuccessCodeIsAccepted() {
        expect("/gbstock/inquiry/v1/balance", """
            {"rsp_cd":"00166","rsp_msg":"정상적으로 조회되었습니다.",
             "Output_0":{"tot_aet_amt":"2000000","fc_eal_amt":"1500.00",
                         "fc_eal_pls_amt":"300.00","pft_rt":"25.00"},
             "Output_1":[{"iem_cd":"AAPL","cns_bse_bnc_qty":"5","cur_cd":"USD",
                          "tdt_sby_bse_xcg_rt":"1383.50"}]}
            """);

        NamuPagedEnvelope<NamuAccountDto.GbBalanceSummary, NamuAccountDto.GbHolding> res =
            sut.exchange(1L, "/gbstock/inquiry/v1/balance", Map.of("act_no", "12345678-01"), GB_BALANCE);

        assertThat(res.summary().evalAmountSum()).isEqualTo("1500.00");
        assertThat(res.items()).hasSize(1);
    }

    @Test
    @DisplayName("13578(조회 내역 없음)은 성공이다 — 보유 종목이 없는 정상 상태가 에러로 뜨면 안 된다")
    void emptyResultCodeIsAccepted() {
        expect("/gbstock/inquiry/v1/balance",
            "{\"rsp_cd\":\"13578\",\"rsp_msg\":\"조회할 내역이 없습니다.\",\"Output_0\":null,\"Output_1\":null}");

        NamuPagedEnvelope<NamuAccountDto.GbBalanceSummary, NamuAccountDto.GbHolding> res =
            sut.exchange(1L, "/gbstock/inquiry/v1/balance", Map.of("act_no", "12345678-01"), GB_BALANCE);

        // 페이로드가 비어도 파싱이 안전해야 한다 — 요약 null, 목록은 빈 리스트.
        assertThat(res.summary()).isNull();
        assertThat(res.items()).isEmpty();
    }

    @Test
    @DisplayName("목록에 없는 코드라도 rsp_msg 가 완료면 성공이다 — OR 조건(AND 아님)")
    void unknownCodeWithCompletedMessageIsAccepted() {
        expect("/krstock/quote/v1/currentPrice",
            "{\"rsp_cd\":\"00999\",\"rsp_msg\":\"조회가 완료되었습니다.\","
                + "\"Output_0\":{\"stck_prpr\":\"70000\"}}");

        assertThat(sut.postObject(1L, "/krstock/quote/v1/currentPrice",
            Map.of("iem_cd", "005930"), KR_PRICE).price()).isEqualTo("70000");
    }

    @Test
    @DisplayName("성공 코드도 아니고 완료도 아니면 실패다 — 넓힌 판정이 실패를 성공으로 읽으면 안 된다")
    void unknownCodeWithoutCompletedMessageFails() {
        expect("/krstock/quote/v1/currentPrice",
            "{\"rsp_cd\":\"00165\",\"rsp_msg\":\"조회가 계속됩니다.\",\"Output_0\":null}");

        assertThatThrownBy(() -> sut.postObject(1L, "/krstock/quote/v1/currentPrice",
                Map.of("iem_cd", "005930"), KR_PRICE))
            .isInstanceOf(ExternalServiceException.class);
    }

    @Test
    @DisplayName("서버 오류는 증권사 API 오류로 바뀐다 — 업스트림 본문이 그대로 새지 않는다")
    void serverErrorIsTranslated() {
        server.expect(requestTo(BASE + "/krstock/quote/v1/currentPrice"))
            .andRespond(withServerError().body("{\"detail\":\"internal\"}"));

        assertThatThrownBy(() -> sut.postObject(1L, "/krstock/quote/v1/currentPrice",
                Map.of("iem_cd", "005930"), KR_PRICE))
            .isInstanceOf(ExternalServiceException.class)
            .hasMessageNotContaining("internal");
    }

    @Test
    @DisplayName("base URL 이 비면 호출 전에 거절한다")
    void rejectsWhenNotConfigured() {
        // base URL 은 이제 환경(LIVE·MOCK)에서 나오므로 URL 만 비워서는 미설정이 안 된다.
        // 진짜 미설정은 환경까지 빈 상태다 — NAMU_ENVIRONMENT 를 빈 값으로 주면 enum 이 null 로
        // 바인딩돼 실제로 생길 수 있다(기동 검사가 먼저 막지만 클라이언트도 스스로 지킨다).
        NamuProperties blank = new NamuProperties();
        blank.setEnvironment(null);
        NamuApiClient unconfigured = new NamuApiClient(restTemplate, tokenManagers, blank);

        assertThatThrownBy(() -> unconfigured.postObject(1L, "/x", Map.of(), KR_PRICE))
            .isInstanceOf(ExternalServiceException.class);
    }
}
