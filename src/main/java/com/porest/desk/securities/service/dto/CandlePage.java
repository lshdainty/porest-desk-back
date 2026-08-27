package com.porest.desk.securities.service.dto;

import java.util.List;

/**
 * 캔들 한 페이지. <b>시간 오름차순</b>이다.
 *
 * <p>차트는 과거로 팬할 때마다 {@link #nextCursor} 로 이전 페이지를 당겨 앞에 붙인다.
 * 그래서 한 번에 전부 주지 않고 페이지로 나눈다 — 1년치를 한 요청에 담으면 나무는
 * 종목당 1콜인 채로 응답만 커지고, 사용자는 첫 그림을 그만큼 늦게 본다.
 *
 * @param candles    이 페이지의 봉들. 없으면 빈 리스트(null 아님)
 * @param nextCursor 더 과거 페이지를 가리키는 커서. <b>null 이면 여기가 끝</b>
 */
public record CandlePage(List<SecuritiesCandle> candles, String nextCursor) {

    public CandlePage {
        candles = candles == null ? List.of() : List.copyOf(candles);
        nextCursor = nextCursor == null || nextCursor.isBlank() ? null : nextCursor;
    }

    public static CandlePage empty() {
        return new CandlePage(List.of(), null);
    }

    /** 더 받을 게 남았는가 — 커서가 있으면 남은 것이다. */
    public boolean hasNext() {
        return nextCursor != null;
    }
}
