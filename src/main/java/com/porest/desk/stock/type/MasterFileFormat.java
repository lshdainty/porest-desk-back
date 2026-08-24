package com.porest.desk.stock.type;

/**
 * 마스터파일 물리 포맷. 포맷 하나 = 파서 하나
 * ({@code com.porest.desk.stock.client.parser} 의 구현).
 */
public enum MasterFileFormat {

    /** KIS 국내 주식(.mst) — 행 단위. 단축코드 9 + 표준코드 12 + 한글명 + 시장별 고정영역(tail) */
    KIS_DOMESTIC_STOCK,

    /** KIS 국내 업종지수(idxcode.mst) — 행 단위. 시장플래그 1 + 업종코드 4 + 업종명 */
    KIS_DOMESTIC_INDEX,

    /** KIS 해외(.cod) — 탭 구분 24컬럼 */
    KIS_OVERSEAS,

    /** NH 국내 주식(m_new_stock.mst) — 237바이트 고정 레코드, LF 종단 */
    NH_DOMESTIC_STOCK,

    /** NH 해외 주식(m_gtsstock.mst) — 164바이트 고정 레코드, LF 종단. 거래소코드가 레코드 안에 있다 */
    NH_OVERSEAS
}
