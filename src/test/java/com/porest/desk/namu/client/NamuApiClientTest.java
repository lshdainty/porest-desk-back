package com.porest.desk.namu.client;

import com.porest.core.exception.ExternalServiceException;
import com.porest.desk.namu.client.dto.NamuEnvelope;
import com.porest.desk.namu.dto.NamuMarketDto;
import com.porest.desk.securities.client.BrokerTokenManager;
import com.porest.desk.securities.client.BrokerTokenManagers;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * 나무증권 저수준 호출 — <b>HTTP 200 으로도 실패한다</b>는 점을 지킨다.
 *
 * <p>토스는 상태코드가 곧 성공 여부지만 나무는 {@code rsp_cd} 를 봐야 한다.
 * 이 검사가 빠지면 실패 응답이 "조회 결과 없음" 으로 둔갑해 화면이 조용히 빈다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NamuApiClientTest {

    private static final ParameterizedTypeReference<NamuEnvelope<NamuMarketDto.KrPrice>> TYPE =
        new ParameterizedTypeReference<>() {
        };

    @Mock private RestTemplate namuRestTemplate;
    @Mock private BrokerTokenManagers tokenManagers;
    @Mock private BrokerTokenManager tokenManager;

    private NamuApiClient sut;

    @BeforeEach
    void setUp() {
        given(tokenManagers.of(SecuritiesBroker.NAMU)).willReturn(tokenManager);
        given(tokenManager.authHeaders(1L)).willReturn(new HttpHeaders());

        NamuProperties properties = new NamuProperties();
        properties.setBaseUrl("https://api.nhplug.com:8443");
        sut = new NamuApiClient(namuRestTemplate, tokenManagers, properties);
    }

    private void respond(NamuEnvelope<NamuMarketDto.KrPrice> body) {
        given(namuRestTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(TYPE)))
            .willReturn(ResponseEntity.ok(body));
    }

    @Test
    @DisplayName("rsp_cd 00000 이면 Output_0 을 꺼내 준다")
    void unwrapsOutputOnSuccess() {
        respond(new NamuEnvelope<>("00000", "조회가 완료되었습니다.",
            List.of(new NamuMarketDto.KrPrice("70000", "2", "500", "0.72"))));

        List<NamuMarketDto.KrPrice> out = sut.post(1L, "/krstock/quote/v1/currentPrice",
            Map.of("iem_cd", "005930"), TYPE);

        assertThat(out).singleElement()
            .extracting(NamuMarketDto.KrPrice::price).isEqualTo("70000");
    }

    @Test
    @DisplayName("200 이어도 rsp_cd 가 00000 이 아니면 실패다 — 이 검사가 빠지면 조용히 빈 화면이 된다")
    void failsWhenResponseCodeIsNotSuccess() {
        respond(new NamuEnvelope<>("40010", "권한이 없습니다.", List.of()));

        assertThatThrownBy(() -> sut.post(1L, "/krstock/quote/v1/currentPrice",
                Map.of("iem_cd", "005930"), TYPE))
            .isInstanceOf(ExternalServiceException.class);
    }

    @Test
    @DisplayName("정상인데 결과가 0건인 것은 실패가 아니다 — 조회 결과 없음일 뿐")
    void emptyOutputOnSuccessIsNotFailure() {
        respond(new NamuEnvelope<>("00000", "조회가 완료되었습니다.", List.of()));

        assertThat(sut.post(1L, "/krstock/quote/v1/currentPrice", Map.of("iem_cd", "000000"), TYPE))
            .isEmpty();
    }

    @Test
    @DisplayName("Output_0 이 아예 없어도 NPE 대신 빈 목록")
    void nullOutputIsEmpty() {
        respond(new NamuEnvelope<>("00000", "ok", null));

        assertThat(sut.post(1L, "/krstock/quote/v1/currentPrice", Map.of("iem_cd", "005930"), TYPE))
            .isEmpty();
    }

    @Test
    @DisplayName("base URL 이 비면 호출 전에 거절한다")
    void rejectsWhenNotConfigured() {
        NamuApiClient unconfigured = new NamuApiClient(namuRestTemplate, tokenManagers, new NamuProperties());

        assertThatThrownBy(() -> unconfigured.post(1L, "/x", Map.of(), TYPE))
            .isInstanceOf(ExternalServiceException.class);
    }

    @Test
    @DisplayName("요청은 Input_0 봉투에 담겨 POST 로 나간다")
    void wrapsRequestInInputEnvelope() {
        respond(new NamuEnvelope<>("00000", "ok", List.of()));

        sut.post(1L, "/krstock/quote/v1/currentPrice", Map.of("iem_cd", "005930"), TYPE);

        org.mockito.ArgumentCaptor<HttpEntity> captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        org.mockito.Mockito.verify(namuRestTemplate)
            .exchange(any(String.class), eq(HttpMethod.POST), captor.capture(), eq(TYPE));
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) captor.getValue().getBody();
        assertThat(body).containsOnlyKeys("Input_0");
    }
}
