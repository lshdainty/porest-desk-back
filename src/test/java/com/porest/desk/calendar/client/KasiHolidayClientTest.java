package com.porest.desk.calendar.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.porest.desk.calendar.client.dto.ExternalHoliday;
import com.porest.desk.calendar.config.HolidayProperties;
import com.porest.desk.calendar.exception.HolidayProviderException;
import com.porest.desk.calendar.type.HolidaySource;
import com.porest.desk.calendar.type.HolidayType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.containsString;

/**
 * 특일정보 API 클라이언트 응답 처리 테스트.
 *
 * <p>공공데이터포털 특유의 응답 변형(단건이면 객체, 0건이면 items 가 빈 문자열, 인증 실패 시 XML)을
 * 모두 흡수하는지 확인한다. 이 중 하나라도 놓치면 매일 도는 동기화가 통째로 실패한다.
 */
class KasiHolidayClientTest {

    private static final String BASE_URL = "https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private HolidayProperties properties;
    private KasiHolidayClient sut;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);

        properties = new HolidayProperties();
        properties.getKasi().setBaseUrl(BASE_URL);
        properties.getKasi().setServiceKey("test-service-key");

        sut = new KasiHolidayClient(restTemplate, properties, new ObjectMapper());
    }

    private String body(String items, int totalCount) {
        return """
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},
                "body":{"items":%s,"numOfRows":100,"pageNo":1,"totalCount":%d}}}
                """.formatted(items, totalCount);
    }

    @Test
    @DisplayName("출처는 KASI 다")
    void source() {
        assertThat(sut.source()).isEqualTo(HolidaySource.KASI);
    }

    @Test
    @DisplayName("공휴일 목록을 정규화된 이름·유형으로 파싱한다")
    void fetchParsesItems() {
        String items = """
                {"item":[
                  {"dateKind":"01","dateName":"1월1일","isHoliday":"Y","locdate":20260101,"seq":1},
                  {"dateKind":"01","dateName":"대체공휴일(3ㆍ1절)","isHoliday":"Y","locdate":20260302,"seq":1},
                  {"dateKind":"01","dateName":"기독탄신일","isHoliday":"Y","locdate":20261225,"seq":1}
                ]}""";
        server.expect(requestTo(containsString("solYear=2026")))
                .andRespond(withSuccess(body(items, 3), MediaType.APPLICATION_JSON));

        List<ExternalHoliday> result = sut.fetch(2026);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).holidayName()).isEqualTo("신정");
        assertThat(result.get(0).holidayType()).isEqualTo(HolidayType.PUBLIC);
        assertThat(result.get(1).holidayName()).isEqualTo("대체공휴일(삼일절)");
        assertThat(result.get(1).holidayType()).isEqualTo(HolidayType.SUBSTITUTE);
        assertThat(result.get(2).holidayName()).isEqualTo("크리스마스");
        assertThat(result.get(2).holidayDate()).hasToString("2026-12-25");
        server.verify();
    }

    @Test
    @DisplayName("공휴일이 1건이면 item 이 배열이 아닌 객체로 와도 파싱한다")
    void fetchParsesSingleItemObject() {
        String items = """
                {"item":{"dateKind":"01","dateName":"어린이날","isHoliday":"Y","locdate":20260505,"seq":1}}""";
        server.expect(requestTo(containsString("solYear=2026")))
                .andRespond(withSuccess(body(items, 1), MediaType.APPLICATION_JSON));

        List<ExternalHoliday> result = sut.fetch(2026);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).holidayName()).isEqualTo("어린이날");
    }

    @Test
    @DisplayName("데이터가 없는 연도는 items 가 빈 문자열로 와도 빈 목록을 돌려준다")
    void fetchReturnsEmptyWhenNoData() {
        server.expect(requestTo(containsString("solYear=1900")))
                .andRespond(withSuccess(body("\"\"", 0), MediaType.APPLICATION_JSON));

        assertThat(sut.fetch(1900)).isEmpty();
    }

    @Test
    @DisplayName("isHoliday=N 항목은 쉬는 날이 아니므로 제외한다")
    void fetchFiltersNonHoliday() {
        String items = """
                {"item":[
                  {"dateName":"어린이날","isHoliday":"Y","locdate":20260505},
                  {"dateName":"국군의 날","isHoliday":"N","locdate":20261001}
                ]}""";
        server.expect(requestTo(containsString("solYear=2026")))
                .andRespond(withSuccess(body(items, 2), MediaType.APPLICATION_JSON));

        assertThat(sut.fetch(2026)).extracting(ExternalHoliday::holidayName).containsExactly("어린이날");
    }

    @Test
    @DisplayName("날짜·이름이 비었거나 형식이 깨진 항목은 건너뛰고 나머지를 살린다")
    void fetchSkipsBrokenItems() {
        String items = """
                {"item":[
                  {"dateName":"","isHoliday":"Y","locdate":20260101},
                  {"dateName":"어린이날","isHoliday":"Y","locdate":"20-26-05-05"},
                  {"dateName":"현충일","isHoliday":"Y","locdate":20260606}
                ]}""";
        server.expect(requestTo(containsString("solYear=2026")))
                .andRespond(withSuccess(body(items, 3), MediaType.APPLICATION_JSON));

        assertThat(sut.fetch(2026)).extracting(ExternalHoliday::holidayName).containsExactly("현충일");
    }

    @Test
    @DisplayName("resultCode 가 00 이 아니면 예외를 던져 폴백으로 넘긴다")
    void fetchThrowsOnErrorResultCode() {
        String errorBody = """
                {"response":{"header":{"resultCode":"30","resultMsg":"SERVICE_KEY_IS_NOT_REGISTERED_ERROR"}}}""";
        server.expect(requestTo(containsString("solYear=2026")))
                .andRespond(withSuccess(errorBody, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> sut.fetch(2026))
                .isInstanceOf(HolidayProviderException.class)
                .hasMessageContaining("resultCode=30");
    }

    @Test
    @DisplayName("인증 실패 시 JSON 요청이어도 XML 이 오는데, 파싱 실패 대신 명확한 예외를 던진다")
    void fetchThrowsOnXmlResponse() {
        String xml = """
                <OpenAPI_ServiceResponse><cmmMsgHeader>
                <returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg>
                </cmmMsgHeader></OpenAPI_ServiceResponse>""";
        server.expect(requestTo(containsString("solYear=2026")))
                .andRespond(withSuccess(xml, MediaType.APPLICATION_XML));

        assertThatThrownBy(() -> sut.fetch(2026))
                .isInstanceOf(HolidayProviderException.class)
                .hasMessageContaining("JSON 이 아닌 응답");
    }

    @Test
    @DisplayName("HTTP 오류는 HolidayProviderException 으로 감싼다")
    void fetchWrapsHttpError() {
        server.expect(requestTo(containsString("solYear=2026"))).andRespond(withServerError());

        assertThatThrownBy(() -> sut.fetch(2026))
                .isInstanceOf(HolidayProviderException.class)
                .hasMessageContaining("호출 실패");
    }

    @Test
    @DisplayName("인증키가 없으면 호출하지 않고 예외를 던진다")
    void fetchThrowsWhenKeyMissing() {
        properties.getKasi().setServiceKey("  ");

        assertThatThrownBy(() -> sut.fetch(2026))
                .isInstanceOf(HolidayProviderException.class)
                .hasMessageContaining("인증키");
        server.verify(); // 호출 자체가 없어야 한다
    }

    @Test
    @DisplayName("인코딩 인증키를 넣어도 이중 인코딩하지 않는다")
    void fetchDoesNotDoubleEncodeServiceKey() {
        // 포털이 주는 인코딩 키(a+b/c==) 형태. 그대로 붙이면 %252B 로 재인코딩돼 인증에 실패한다.
        properties.getKasi().setServiceKey("a%2Bb%2Fc%3D%3D");
        server.expect(queryParam("serviceKey", "a%2Bb%2Fc%3D%3D"))
                .andRespond(withSuccess(body("\"\"", 0), MediaType.APPLICATION_JSON));

        sut.fetch(2026);

        server.verify();
    }

    @Test
    @DisplayName("디코딩 인증키를 넣으면 한 번만 인코딩해서 보낸다")
    void fetchEncodesDecodedServiceKey() {
        properties.getKasi().setServiceKey("a+b/c==");
        server.expect(queryParam("serviceKey", "a%2Bb%2Fc%3D%3D"))
                .andRespond(withSuccess(body("\"\"", 0), MediaType.APPLICATION_JSON));

        sut.fetch(2026);

        server.verify();
    }
}
