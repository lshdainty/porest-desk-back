package com.porest.desk.calendar.client;

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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** 폴백 소스(holidays-kr 정적 JSON) 클라이언트 테스트. */
class HolidaysKrClientTest {

    private static final String BASE_URL = "https://holidays.hyunbin.page";

    private MockRestServiceServer server;
    private HolidaysKrClient sut;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);

        HolidayProperties properties = new HolidayProperties();
        properties.getFallback().setBaseUrl(BASE_URL);

        sut = new HolidaysKrClient(restTemplate, properties);
    }

    @Test
    @DisplayName("출처는 HOLIDAYS_KR 다")
    void source() {
        assertThat(sut.source()).isEqualTo(HolidaySource.HOLIDAYS_KR);
    }

    @Test
    @DisplayName("날짜별 이름 배열을 정규화해 파싱한다")
    void fetchParsesJson() {
        String json = """
                {"2026-01-01":["1월 1일"],
                 "2026-03-02":["대체공휴일(3ㆍ1절)"],
                 "2026-07-17":["제헌절"]}""";
        server.expect(requestTo(BASE_URL + "/2026.json"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        List<ExternalHoliday> result = sut.fetch(2026);

        assertThat(result).extracting(ExternalHoliday::holidayName)
                .containsExactly("신정", "대체공휴일(삼일절)", "제헌절");
        assertThat(result.get(1).holidayType()).isEqualTo(HolidayType.SUBSTITUTE);
        server.verify();
    }

    @Test
    @DisplayName("같은 날 두 공휴일이 겹치면 배열로 오는데 각각 만든다")
    void fetchExpandsMultipleNamesOnSameDate() {
        String json = """
                {"2025-05-05":["어린이날","부처님 오신 날"]}""";
        server.expect(requestTo(BASE_URL + "/2025.json"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        List<ExternalHoliday> result = sut.fetch(2025);

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(h -> assertThat(h.holidayDate()).isEqualTo(LocalDate.of(2025, 5, 5)));
        assertThat(result).extracting(ExternalHoliday::holidayName)
                .containsExactly("어린이날", "석가탄신일");
    }

    @Test
    @DisplayName("커버하지 않는 연도(404)는 예외 없이 빈 목록을 돌려준다")
    void fetchReturnsEmptyOnNotFound() {
        server.expect(requestTo(BASE_URL + "/2010.json")).andRespond(withResourceNotFound());

        assertThat(sut.fetch(2010)).isEmpty();
    }

    @Test
    @DisplayName("날짜 형식이 깨진 항목은 건너뛴다")
    void fetchSkipsBrokenDate() {
        String json = """
                {"2026-13-99":["깨진날짜"],"2026-06-06":["현충일"]}""";
        server.expect(requestTo(BASE_URL + "/2026.json"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        assertThat(sut.fetch(2026)).extracting(ExternalHoliday::holidayName).containsExactly("현충일");
    }

    @Test
    @DisplayName("404 외의 HTTP 오류는 예외로 올린다")
    void fetchWrapsServerError() {
        server.expect(requestTo(BASE_URL + "/2026.json")).andRespond(withServerError());

        assertThatThrownBy(() -> sut.fetch(2026))
                .isInstanceOf(HolidayProviderException.class)
                .hasMessageContaining("호출 실패");
    }

    @Test
    @DisplayName("JSON 이 아니면 파싱 실패 예외를 던진다")
    void fetchThrowsOnBrokenJson() {
        server.expect(requestTo(BASE_URL + "/2026.json"))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> sut.fetch(2026))
                .isInstanceOf(HolidayProviderException.class)
                .hasMessageContaining("파싱 실패");
    }
}
