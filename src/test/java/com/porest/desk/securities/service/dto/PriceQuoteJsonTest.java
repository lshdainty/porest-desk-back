package com.porest.desk.securities.service.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PriceQuote} 의 <b>JSON 모양</b>. 자바 객체를 비교해서는 못 잡는 자리라 직렬화 결과를 본다.
 *
 * <p>이 레코드는 {@code /api/v1/securities/prices} · {@code /api/v1/namu/kr/price} ·
 * {@code /api/v1/namu/gb/price} 응답에 그대로 실린다. Jackson 이 {@code isKrw()} 를 getter 로
 * 잡아 {@code krw} 필드를 내보내고 있었다 — 내부 판별용인데 스펙에 실려 클라이언트가 계약으로
 * 읽을 수 있었다.
 */
class PriceQuoteJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("내부 판별용 isKrw() 는 응답에 안 나간다")
    void doesNotLeakKrwFlag() throws Exception {
        String json = mapper.writeValueAsString(
            new PriceQuote("005930", new BigDecimal("70000"), "KRW", new BigDecimal("69500")));

        assertThat(json).doesNotContain("krw");
        assertThat(mapper.readTree(json).fieldNames()).toIterable()
            .containsExactlyInAnyOrder("symbol", "price", "currency", "previousClose");
    }

    @Test
    @DisplayName("판별 자체는 그대로 — 통화가 없거나 KRW 면 원화다")
    void stillClassifies() {
        assertThat(PriceQuote.of("005930", BigDecimal.ONE, null).isKrw()).isTrue();
        assertThat(PriceQuote.of("005930", BigDecimal.ONE, "KRW").isKrw()).isTrue();
        assertThat(PriceQuote.of("AAPL", BigDecimal.ONE, "USD").isKrw()).isFalse();
    }
}
