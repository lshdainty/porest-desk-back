package com.porest.desk.stock.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 내려받을 마스터파일 하나.
 *
 * <p>파일 메타데이터를 시장 enum 에서 분리한 이유 — KIS 는 파일 1개가 시장 1개지만
 * <b>NH 는 파일 1개에 시장이 여러 개</b>다({@code m_gtsstock.mst} 하나에 거래소 15곳).
 * 시장 enum 에 파일명을 달아 두면 NH 를 표현할 자리가 없다.
 *
 * <p>{@code market} 이 null 인 파일은 <b>레코드마다 시장이 다르다</b> — 파서가 레코드에서
 * 읽어 정한다.
 *
 * <p>{@code domesticTailLength} 는 KIS 국내 {@code .mst} 의 종목명 뒤 고정영역 길이다.
 * KIS 는 구조 정의를 배포하지 않아 파일마다 실측값을 박아 둘 수밖에 없다. NH 는 {@code .h} 로
 * 구조체를 함께 주므로 레이아웃을 데이터로 옮겼다
 * ({@code com.porest.desk.stock.client.parser.NhMasterLayouts}).
 */
@Getter
@RequiredArgsConstructor
public enum MasterFile {

    KIS_KOSPI(MasterSource.KIS, "kospi_code.mst.zip", MasterFileFormat.KIS_DOMESTIC_STOCK, StockMarket.KOSPI, 227),
    KIS_KOSDAQ(MasterSource.KIS, "kosdaq_code.mst.zip", MasterFileFormat.KIS_DOMESTIC_STOCK, StockMarket.KOSDAQ, 221),
    KIS_KONEX(MasterSource.KIS, "konex_code.mst.zip", MasterFileFormat.KIS_DOMESTIC_STOCK, StockMarket.KONEX, 184),
    KIS_KRX_IDX(MasterSource.KIS, "idxcode.mst.zip", MasterFileFormat.KIS_DOMESTIC_INDEX, StockMarket.KRX_IDX, 0),
    KIS_NAS(MasterSource.KIS, "nasmst.cod.zip", MasterFileFormat.KIS_OVERSEAS, StockMarket.NAS, 0),
    KIS_NYS(MasterSource.KIS, "nysmst.cod.zip", MasterFileFormat.KIS_OVERSEAS, StockMarket.NYS, 0),
    KIS_AMS(MasterSource.KIS, "amsmst.cod.zip", MasterFileFormat.KIS_OVERSEAS, StockMarket.AMS, 0),
    KIS_SHS(MasterSource.KIS, "shsmst.cod.zip", MasterFileFormat.KIS_OVERSEAS, StockMarket.SHS, 0),
    KIS_SHI(MasterSource.KIS, "shimst.cod.zip", MasterFileFormat.KIS_OVERSEAS, StockMarket.SHI, 0),
    KIS_SZS(MasterSource.KIS, "szsmst.cod.zip", MasterFileFormat.KIS_OVERSEAS, StockMarket.SZS, 0),
    KIS_SZI(MasterSource.KIS, "szimst.cod.zip", MasterFileFormat.KIS_OVERSEAS, StockMarket.SZI, 0),
    KIS_TSE(MasterSource.KIS, "tsemst.cod.zip", MasterFileFormat.KIS_OVERSEAS, StockMarket.TSE, 0),
    KIS_HKS(MasterSource.KIS, "hksmst.cod.zip", MasterFileFormat.KIS_OVERSEAS, StockMarket.HKS, 0),
    KIS_HNX(MasterSource.KIS, "hnxmst.cod.zip", MasterFileFormat.KIS_OVERSEAS, StockMarket.HNX, 0),
    KIS_HSX(MasterSource.KIS, "hsxmst.cod.zip", MasterFileFormat.KIS_OVERSEAS, StockMarket.HSX, 0),

    /** NH 국내 — KOSPI·KOSDAQ·ETN 이 한 파일에 있고 레코드의 시장구분(1/4/A)으로 갈린다. */
    NH_DOMESTIC(MasterSource.NH, "m_new_stock.mst", MasterFileFormat.NH_DOMESTIC_STOCK, null, 0),

    /** NH 해외 — 거래소 15곳이 한 파일에 있고 레코드의 거래소코드로 갈린다. */
    NH_OVERSEAS(MasterSource.NH, "m_gtsstock.mst", MasterFileFormat.NH_OVERSEAS, null, 0);

    private final MasterSource source;
    private final String fileName;
    private final MasterFileFormat format;
    /** 파일 전체가 한 시장이면 그 시장, 레코드마다 다르면 null. */
    private final StockMarket market;
    /** KIS 국내 .mst 전용. 나머지는 0. */
    private final int domesticTailLength;
}
