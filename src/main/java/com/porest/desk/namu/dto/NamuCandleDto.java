package com.porest.desk.namu.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 나무증권 기간별시세(캔들) 응답. 국내·해외가 <b>필드명부터 다르다</b> —
 * {@link NamuMarketDto} 와 같은 이유로 합치지 않고 각자 두고 서비스에서 옮긴다.
 *
 * <h2>봉은 {@code Output_1} 에 있다 — {@code Output_0} 은 읽지 않는다</h2>
 *
 * <p>NH 스펙은 이 두 API 의 {@code Output_0} 에 <b>⚠️ 명세 검증 필요</b> 를 달아 뒀다:
 * "명세상 Array 로 선언되어 있으나 예시 응답은 Object 입니다." 이 레포는 정확히 그 자리에서
 * 한 번 크게 당했다 — {@code Output_0} 을 배열로 모델링했다가 나무 조회가 통째로 실패했다(#254).
 *
 * <p>그래서 <b>추측하지 않는다.</b> 캔들 배열은 스펙이 명확히 {@code Output_1} 이라고 못박은
 * 자리에 있고({@code bsop_date}·{@code stck_oprc}… / {@code trade_date}·{@code open_prc}…),
 * 우리에게 필요한 건 그것뿐이다. {@code Output_0}(종목 요약·현재가)은
 * <b>필드로 선언조차 하지 않아</b> 객체로 오든 배열로 오든 역직렬화가 성공한다
 * ({@code NamuCandleEnvelope} 참고). 실제 타입이 무엇이든 우리 코드는 영향을 받지 않는다.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} 인 이유 — 봉 하나에도 필드가
 * 열 개 넘게 실려 오는데 차트가 쓰는 건 여섯 개뿐이다.
 */
public final class NamuCandleDto {

    private NamuCandleDto() {
    }

    /**
     * 국내주식 기간별시세 한 봉 ({@code POST /krstock/quote/v1/period} 의 {@code Output_1}).
     *
     * <p>종가가 {@code stck_prpr}(현재가)인 것은 오타가 아니다 — 나무는 봉의 종가를
     * 그 봉 시점의 "현재가" 로 부른다. 형제 API 인 현재가 조회와 이름이 같다.
     *
     * @param date   영업일자 {@code bsop_date} / {@code YYYYMMDD}
     * @param time   체결시각 {@code bsop_time} / {@code HHmmss}. 일봉이면 비어 온다
     * @param volume 거래량 {@code vol}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KrCandle(
        @JsonProperty("bsop_date") String date,
        @JsonProperty("bsop_time") String time,
        @JsonProperty("stck_oprc") String open,
        @JsonProperty("stck_hgpr") String high,
        @JsonProperty("stck_lwpr") String low,
        @JsonProperty("stck_prpr") String close,
        @JsonProperty("vol") String volume
    ) {
    }

    /**
     * 해외주식 기간별시세 한 봉 ({@code POST /gbstock/quote/v1/period} 의 {@code Output_1}).
     *
     * <p><b>{@code trade_date} 를 쓰고 {@code bsop_date} 는 쓰지 않는다.</b> 응답에 둘 다
     * 오는데, {@code trade_*} 만 시각({@code trade_time})과 짝이 맞는 거래 시점이다.
     * 두 날짜가 뜻하는 바의 차이는 공개 문서에 없어, 짝이 확실한 쪽을 쓴다.
     *
     * @param date   거래일자 {@code trade_date} / {@code YYYYMMDD}
     * @param time   거래시각 {@code trade_time} / {@code HHmmss}. 일봉이면 비어 온다
     * @param volume 거래량 {@code movolume}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GbCandle(
        @JsonProperty("trade_date") String date,
        @JsonProperty("trade_time") String time,
        @JsonProperty("open_prc") String open,
        @JsonProperty("high") String high,
        @JsonProperty("low") String low,
        @JsonProperty("close_prc") String close,
        @JsonProperty("movolume") String volume
    ) {
    }
}
