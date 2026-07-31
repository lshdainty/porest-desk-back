package com.porest.desk.stock.type;

/** KIS 마스터파일 물리 포맷 */
public enum KisFileFormat {
    /** 국내 주식(.mst) — 고정폭. 단축코드 9 + 표준코드 12 + 한글명 + 고정영역(tail) */
    DOMESTIC_STOCK,
    /** 국내 업종지수(idxcode.mst) — 고정폭. 시장플래그 1 + 업종코드 4 + 업종명 */
    DOMESTIC_INDEX,
    /** 해외(.cod) — 탭 구분 24컬럼 */
    OVERSEAS
}
