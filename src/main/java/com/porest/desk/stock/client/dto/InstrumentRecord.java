package com.porest.desk.stock.client.dto;

import com.porest.desk.stock.type.StockMarket;
import com.porest.desk.stock.type.StockSecurityType;

/**
 * 마스터파일 1행을 정규화한 종목 레코드. 소스가 KIS 든 NH 든 여기로 모인다.
 *
 * @param market         상장 시장. KIS 는 파일이, NH 는 레코드가 정한다
 * @param symbol         종목 코드 (국내 단축코드, 해외 티커, 업종지수 코드)
 * @param standardCode   표준 코드 (ISIN). KIS 국내 주식·NH 해외만 존재
 * @param realtimeSymbol KIS 실시간 시세용 심볼 (예: NASAAPL). KIS 해외만 존재
 * @param nameKr         한글 종목명
 * @param nameEn         영문 종목명. 해외만 존재
 * @param securityType   정규화된 종목 유형
 * @param currency       거래 통화
 * @param nhGic          NH 해외종목 통합코드(예: {@code USAAAPL}) — NH 해외만 존재.
 *                       <b>REST 시세 키가 아니다.</b> 나무 REST 해외 시세
 *                       ({@code /gbstock/quote/v1/current})의 {@code iem_cd} 는 티커를 받는다.
 *                       GIC 는 나무 <b>WebSocket 실시간 채널</b>(RH/rh/RC/rc)의 {@code tr_key}
 *                       ({@code gicz15}) 자리에 쓰는 값이고, 이 서버는 나무 WebSocket 을
 *                       구현하지 않아 지금은 저장만 한다
 * @param nxtTradable    NXT(넥스트레이드) 거래 가능 여부. 나무 국내시세 market_cd 판단 근거 — NH 국내만 존재
 * @param priceDecimals  가격 소수점 자릿수. 표시에 필요 — NH 해외만 존재
 */
public record InstrumentRecord(
    StockMarket market,
    String symbol,
    String standardCode,
    String realtimeSymbol,
    String nameKr,
    String nameEn,
    StockSecurityType securityType,
    String currency,
    String nhGic,
    Boolean nxtTradable,
    Integer priceDecimals
) {
    /** KIS 레코드 — 보강 필드가 없다. */
    public static InstrumentRecord kis(StockMarket market, String symbol, String standardCode, String realtimeSymbol,
                                       String nameKr, String nameEn, StockSecurityType securityType, String currency) {
        return new InstrumentRecord(market, symbol, standardCode, realtimeSymbol,
            nameKr, nameEn, securityType, currency, null, null, null);
    }
}
