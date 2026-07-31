package com.porest.desk.stock.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * KIS 마스터파일이 제공하는 시장. 시장 1개 = 파일 1개.
 *
 * <p>국내 .mst 는 고정폭이라 파싱에 종목명 뒤 고정영역 길이(tail)가 필요하고 파일마다 다르다.
 * 해외 .cod 는 탭 구분이라 tail 을 쓰지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum StockMarket {
    KOSPI("KR", "kospi_code.mst.zip", KisFileFormat.DOMESTIC_STOCK, 227),
    KOSDAQ("KR", "kosdaq_code.mst.zip", KisFileFormat.DOMESTIC_STOCK, 221),
    KONEX("KR", "konex_code.mst.zip", KisFileFormat.DOMESTIC_STOCK, 184),
    KRX_IDX("KR", "idxcode.mst.zip", KisFileFormat.DOMESTIC_INDEX, 0),
    NAS("US", "nasmst.cod.zip", KisFileFormat.OVERSEAS, 0),
    NYS("US", "nysmst.cod.zip", KisFileFormat.OVERSEAS, 0),
    AMS("US", "amsmst.cod.zip", KisFileFormat.OVERSEAS, 0),
    SHS("CN", "shsmst.cod.zip", KisFileFormat.OVERSEAS, 0),
    SHI("CN", "shimst.cod.zip", KisFileFormat.OVERSEAS, 0),
    SZS("CN", "szsmst.cod.zip", KisFileFormat.OVERSEAS, 0),
    SZI("CN", "szimst.cod.zip", KisFileFormat.OVERSEAS, 0),
    TSE("JP", "tsemst.cod.zip", KisFileFormat.OVERSEAS, 0),
    HKS("HK", "hksmst.cod.zip", KisFileFormat.OVERSEAS, 0),
    HNX("VN", "hnxmst.cod.zip", KisFileFormat.OVERSEAS, 0),
    HSX("VN", "hsxmst.cod.zip", KisFileFormat.OVERSEAS, 0);

    private final String countryCode;
    private final String fileName;
    private final KisFileFormat fileFormat;
    /** 국내 .mst 고정폭에서 종목명 뒤에 붙는 고정영역 길이(개행 제외) */
    private final int domesticTailLength;
}
