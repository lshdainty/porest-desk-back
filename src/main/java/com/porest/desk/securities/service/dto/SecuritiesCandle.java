package com.porest.desk.securities.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 증권사 무관 캔들 한 봉.
 *
 * <p><b>필드 이름과 타입이 {@code TossMarketDto.Candle} 과 같은 것은 우연이 아니다.</b>
 * 화면(TradingView Lightweight Charts 래퍼)이 이미 그 모양을 읽고 있어서, 응답 모양을
 * 그대로 두면 프론트는 <b>URL 한 줄</b>만 바꾸면 된다. 새 모양을 만들면 파싱·정규화·임베드
 * 차트까지 같이 고쳐야 하고, 그 사이 옛 앱은 두 모양을 다 견뎌야 한다.
 *
 * <p>금액을 문자열로 두는 것도 같은 이유다 — 증권사마다 소수 자릿수가 다르고
 * (원화 0자리 · 달러 2자리), 숫자로 바꾸는 순간 뒤 0 이 잘려 나간다. 파싱은 화면이 한다.
 *
 * @param timestamp 봉의 시각. <b>오프셋이 붙은 ISO-8601</b>({@code 2026-08-26T09:00:00+09:00}).
 *                  거래소 현지시각 기준이라 오프셋을 반드시 실어 보낸다 — 빼면 받는 쪽이
 *                  자기 타임존으로 읽어 미국 장중 봉이 한국 새벽으로 밀린다
 * @param volume    거래량. 못 주는 증권사·구간이 있어 {@code "0"} 일 수 있다
 * @param currency  거래 통화. 표시용이고 계산에는 쓰이지 않는다
 */
@Schema(name = "SecuritiesCandle")
public record SecuritiesCandle(
        String timestamp,
        String openPrice,
        String highPrice,
        String lowPrice,
        String closePrice,
        String volume,
        String currency
) {
}
