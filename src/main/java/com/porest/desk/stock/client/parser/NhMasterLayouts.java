package com.porest.desk.stock.client.parser;

/**
 * NH 마스터파일 레코드 배치. {@code https://www.nhplug.com/instruments/<파일명>.h} 의
 * {@code FIELDS} 목록을 그대로 옮긴 것이다.
 *
 * <p>손으로 오프셋을 세지 않는다 — {@link FixedWidthLayout} 이 누계로 계산하고, 길이 합이
 * {@code @record} 와 다르면 클래스 로딩 시점에 멈춘다.
 *
 * <p>구조가 개정되면 {@code .h} 를 다시 받아 이 목록만 갈아 끼우면 된다.
 */
final class NhMasterLayouts {

    private NhMasterLayouts() {
    }

    /**
     * {@code m_new_stock.mst} — 국내주식(KOSPI·KOSDAQ·ETN). {@code @record 237}, 39필드.
     *
     * <p>쓰는 필드만 이름을 달고 나머지는 자리만 지킨다 — 길이 합이 레코드 크기와 맞아야
     * 뒷 필드가 안 밀린다.
     */
    static final FixedWidthLayout DOMESTIC_STOCK = FixedWidthLayout.of(237,
        "sCode", 6,          // 종목코드
        "sMarket", 1,        // 시장구분 1:코스피 4:코스닥 A:ETN
        "sKorName", 41,      // 한글명 (선두 1바이트가 지수마커 * / #)
        "sEngName", 41,      // 영문명 (선두 1바이트가 지수마커)
        "sOldName", 40,
        "eCapSize", 1,
        "sUpCodeM", 6,
        "sUpCodeS", 6,
        "sGroup", 2,
        "gManuf", 1,
        "sParvalue", 7,
        "sPrePrice", 7,
        "eRights", 1,
        "eUnder", 1,
        "eStop", 1,
        "eWarn", 1,
        "eGongsi", 1,
        "gTonghap", 1,
        "gVenture", 1,
        "gKrx300", 1,
        "gKospi50", 1,
        "eAccept", 1,
        "gKospiIT", 1,
        "gKospiBD", 1,
        "gIT", 1,
        "gKosdaq150", 1,
        "gKospi100", 1,
        "prdy_avls", 12,
        "invt_epmd_issu_yn", 1,
        "short_over_issu_cls_code", 1,
        "alert_gb", 1,
        "sltr_yn", 1,
        "stck_sdpr", 7,
        "nxt_yn", 1,         // NXT 거래 가능 — 나무 국내시세 market_cd 판단 근거
        "eNXTStop", 1,
        "sUpCodeL", 6,
        "nxt_comp_deal_tr_code", 2,
        "filler", 29,
        "dummy", 1           // LF
    );

    /** {@code m_gtsstock.mst} — 해외주식. {@code @record 164}, 21필드. */
    static final FixedWidthLayout OVERSEAS = FixedWidthLayout.of(164,
        "sGIC", 15,          // 해외종목 통합코드 — 나무 WebSocket tr_key 용. REST 시세는 티커로 문다
        "sKorName", 40,
        "sEngName", 40,
        "sNationCode", 3,
        "sSymbol", 12,       // 티커
        "sStockCode", 3,     // 거래소코드 (NQQ/NYY/GER/LSE/...)
        "sStandardCode", 12, // ISIN
        "gIssue", 2,         // 종목구분 (12:ETF)
        "gIndustryReuter", 4,
        "sIndustryKorea", 4,
        "gLock", 2,
        "gPosTrade", 1,
        "gPayMoney", 3,      // 결제화폐
        "gListed", 1,
        "gTOPIX100", 1,
        "sOriginNationCode", 3,
        "sOriginSymbol", 12,
        "sOriginCurrency", 3,
        "sDecimalPoint", 1,  // 가격 소수점 자릿수
        "sTradePoint", 1,
        "dummy", 1           // LF
    );
}
