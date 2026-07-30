package com.porest.desk.calendar.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.porest.desk.calendar.client.dto.ExternalHoliday;
import com.porest.desk.calendar.config.HolidayProperties;
import com.porest.desk.calendar.exception.HolidayProviderException;
import com.porest.desk.calendar.type.HolidaySource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * holidays-kr 정적 JSON 클라이언트. 특일정보 API 장애 시 폴백으로 쓴다.
 *
 * <p>우주항공청 월력요항을 가공한 MIT 라이선스 데이터로, {@code {baseUrl}/{year}.json} 이
 * {@code {"2026-01-01": ["1월 1일"], ...}} 형태를 내려준다. 커버하지 않는 연도는 404 다.
 *
 * @see <a href="https://github.com/hyunbinseo/holidays-kr">hyunbinseo/holidays-kr</a>
 */
@Slf4j
@Component
@Order(2)
@ConditionalOnProperty(prefix = "app.holiday.fallback", name = "enabled", havingValue = "true", matchIfMissing = true)
public class HolidaysKrClient implements HolidayProvider {

    private final RestTemplate restTemplate;
    private final HolidayProperties properties;
    private final ObjectMapper objectMapper;

    public HolidaysKrClient(@Qualifier("holidaysKrRestTemplate") RestTemplate restTemplate,
                            HolidayProperties properties,
                            ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public HolidaySource source() {
        return HolidaySource.HOLIDAYS_KR;
    }

    @Override
    public List<ExternalHoliday> fetch(int year) {
        String url = properties.getFallback().getBaseUrl() + "/" + year + ".json";

        String body;
        try {
            body = restTemplate.getForObject(url, String.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.info("폴백 소스가 커버하지 않는 연도입니다: year={}", year);
                return List.of();
            }
            throw new HolidayProviderException("폴백 소스 호출 실패: year=" + year, e);
        } catch (RestClientException e) {
            throw new HolidayProviderException("폴백 소스 호출 실패: year=" + year, e);
        }

        return parse(body, year);
    }

    private List<ExternalHoliday> parse(String body, int year) {
        if (body == null || body.isBlank()) {
            throw new HolidayProviderException("폴백 소스 응답이 비어 있습니다: year=" + year);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            throw new HolidayProviderException("폴백 소스 응답 파싱 실패: year=" + year, e);
        }

        List<ExternalHoliday> holidays = new ArrayList<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = root.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();

            LocalDate date;
            try {
                date = LocalDate.parse(entry.getKey());
            } catch (DateTimeParseException e) {
                log.warn("폴백 소스 날짜 형식이 올바르지 않아 건너뜁니다: year={}, key={}", year, entry.getKey());
                continue;
            }

            // 같은 날 두 공휴일이 겹치면 이름이 배열로 온다(예: 2025-05-05 어린이날·부처님 오신 날).
            for (JsonNode nameNode : entry.getValue()) {
                String rawName = nameNode.asText();
                if (rawName.isBlank()) {
                    continue;
                }
                holidays.add(new ExternalHoliday(
                    date,
                    HolidayNameNormalizer.normalize(rawName),
                    HolidayNameNormalizer.resolveType(rawName)
                ));
            }
        }
        return holidays;
    }
}
