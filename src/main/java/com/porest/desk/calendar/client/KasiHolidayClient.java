package com.porest.desk.calendar.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.porest.desk.calendar.client.dto.ExternalHoliday;
import com.porest.desk.calendar.config.HolidayProperties;
import com.porest.desk.calendar.exception.HolidayProviderException;
import com.porest.desk.calendar.type.HolidaySource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 한국천문연구원 특일 정보 API 의 공휴일 조회({@code getRestDeInfo}) 클라이언트.
 *
 * <p>{@code solMonth} 를 생략하면 해당 연도 전체가 한 번에 오므로 연도당 1콜만 쓴다.
 *
 * @see <a href="https://www.data.go.kr/data/15012690/openapi.do">한국천문연구원_특일 정보</a>
 */
@Slf4j
@Component
@Order(1)
public class KasiHolidayClient implements HolidayProvider {

    private static final String OPERATION = "/getRestDeInfo";
    private static final String RESULT_CODE_SUCCESS = "00";
    /** 공휴일은 연 30건을 넘지 않지만 임시공휴일·선거일이 겹치는 해를 감안해 넉넉히 잡는다. */
    private static final int NUM_OF_ROWS = 100;

    private final RestTemplate restTemplate;
    private final HolidayProperties properties;
    private final ObjectMapper objectMapper;

    public KasiHolidayClient(@Qualifier("kasiRestTemplate") RestTemplate restTemplate,
                             HolidayProperties properties,
                             ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public HolidaySource source() {
        return HolidaySource.KASI;
    }

    /** 인증키가 없으면 호출해도 인증 오류만 돌아오므로 소스에서 빠진다. */
    @Override
    public boolean isAvailable() {
        return properties.getKasi().isConfigured();
    }

    @Override
    public List<ExternalHoliday> fetch(int year) {
        HolidayProperties.Kasi kasi = properties.getKasi();
        if (!kasi.isConfigured()) {
            throw new HolidayProviderException("특일정보 API 인증키가 설정되지 않았습니다 (app.holiday.kasi.service-key)");
        }

        String body;
        try {
            body = restTemplate.getForObject(buildUri(year), String.class);
        } catch (RestClientException e) {
            throw new HolidayProviderException("특일정보 API 호출 실패: year=" + year, e);
        }

        return parse(body, year);
    }

    /**
     * 인증키를 직접 인코딩해 URI 를 만든다.
     *
     * <p>공공데이터포털은 같은 키를 인코딩·디코딩 두 형태로 주는데, 인코딩 키를 그대로 쿼리 파라미터로
     * 넘기면 {@code %2B} 가 {@code %252B} 로 다시 인코딩돼 인증에 실패한다. 어느 쪽을 넣어도 동작하도록
     * {@code %} 가 있으면 먼저 디코딩한 뒤 한 번만 인코딩하고, {@code build(true)} 로 재인코딩을 막는다.
     */
    private URI buildUri(int year) {
        String rawKey = properties.getKasi().getServiceKey();
        String decoded = rawKey.contains("%") ? URLDecoder.decode(rawKey, StandardCharsets.UTF_8) : rawKey;
        String encodedKey = URLEncoder.encode(decoded, StandardCharsets.UTF_8);

        return UriComponentsBuilder.fromUriString(properties.getKasi().getBaseUrl() + OPERATION)
            .queryParam("serviceKey", encodedKey)
            .queryParam("solYear", year)
            .queryParam("numOfRows", NUM_OF_ROWS)
            .queryParam("pageNo", 1)
            .queryParam("_type", "json")
            .build(true)
            .toUri();
    }

    private List<ExternalHoliday> parse(String body, int year) {
        if (body == null || body.isBlank()) {
            throw new HolidayProviderException("특일정보 API 응답이 비어 있습니다: year=" + year);
        }

        // 인증키 오류·트래픽 초과는 JSON 요청이어도 XML(OpenAPI_ServiceResponse)로 돌아온다.
        String trimmed = body.trim();
        if (!trimmed.startsWith("{")) {
            throw new HolidayProviderException(
                "특일정보 API 가 JSON 이 아닌 응답을 반환했습니다 (인증키·트래픽 확인 필요): year=" + year
                    + ", body=" + abbreviate(trimmed));
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(trimmed);
        } catch (Exception e) {
            throw new HolidayProviderException("특일정보 API 응답 파싱 실패: year=" + year, e);
        }

        JsonNode header = root.path("response").path("header");
        String resultCode = header.path("resultCode").asText();
        if (!RESULT_CODE_SUCCESS.equals(resultCode)) {
            throw new HolidayProviderException("특일정보 API 오류 응답: year=" + year
                + ", resultCode=" + resultCode + ", resultMsg=" + header.path("resultMsg").asText());
        }

        // totalCount 가 0 이면 items 가 객체가 아니라 빈 문자열로 온다.
        JsonNode items = root.path("response").path("body").path("items");
        if (!items.isObject()) {
            log.info("특일정보 API 에 해당 연도 데이터가 없습니다: year={}", year);
            return List.of();
        }

        // item 은 1건이면 객체, 2건 이상이면 배열로 온다.
        JsonNode item = items.path("item");
        List<JsonNode> nodes = new ArrayList<>();
        if (item.isArray()) {
            item.forEach(nodes::add);
        } else if (item.isObject()) {
            nodes.add(item);
        }

        List<ExternalHoliday> holidays = new ArrayList<>();
        for (JsonNode node : nodes) {
            ExternalHoliday holiday = toHoliday(node, year);
            if (holiday != null) {
                holidays.add(holiday);
            }
        }
        return holidays;
    }

    private ExternalHoliday toHoliday(JsonNode node, int year) {
        // 공휴일 조회 오퍼레이션이라 통상 Y 만 오지만, 국경일이 섞여 들어와도 쉬는 날만 남긴다.
        if (!"Y".equalsIgnoreCase(node.path("isHoliday").asText("Y"))) {
            return null;
        }

        String locdate = node.path("locdate").asText();
        String rawName = node.path("dateName").asText();
        if (locdate.isBlank() || rawName.isBlank()) {
            log.warn("특일정보 API 항목에 날짜·이름이 없어 건너뜁니다: year={}, node={}", year, node);
            return null;
        }

        LocalDate date;
        try {
            date = LocalDate.parse(locdate, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException e) {
            log.warn("특일정보 API 날짜 형식이 올바르지 않아 건너뜁니다: year={}, locdate={}", year, locdate);
            return null;
        }

        return new ExternalHoliday(
            date,
            HolidayNameNormalizer.normalize(rawName),
            HolidayNameNormalizer.resolveType(rawName)
        );
    }

    private static String abbreviate(String value) {
        return value.length() <= 200 ? value : value.substring(0, 200) + "...";
    }
}
