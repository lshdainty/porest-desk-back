package com.porest.desk.securities.service.dto;

import com.porest.desk.securities.type.CandleInterval;

/**
 * 캔들 한 페이지를 물을 때 넘기는 것 전부.
 *
 * @param symbol   stock_master 기준 종목코드. 국내인지 해외인지는 <b>서버가 마스터로 판단한다</b> —
 *                 나무는 국내({@code /krstock})와 해외({@code /gbstock})가 엔드포인트부터 다르다
 * @param interval 봉 주기
 * @param size     이 페이지에 담을 봉 수. 호출부가 상한을 이미 씌워서 넘긴다
 * @param cursor   더 과거로 가는 커서. 첫 페이지면 null.
 *                 <b>값의 뜻은 증권사마다 다르다</b> — 토스는 자기가 준 불투명 문자열,
 *                 나무는 우리가 만든 {@code YYYYMMDD}(그 날짜까지 조회). 그래서 여기서는
 *                 해석하지 않고 제공자에게 그대로 넘긴다
 * @param adjusted 수정주가 여부. <b>토스만 받는다</b> — 나무 기간별시세에는 대응 파라미터가
 *                 없어 무시된다. null 이면 증권사 기본값
 */
public record CandleQuery(
        String symbol,
        CandleInterval interval,
        int size,
        String cursor,
        Boolean adjusted
) {
}
