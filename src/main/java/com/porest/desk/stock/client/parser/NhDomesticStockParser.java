package com.porest.desk.stock.client.parser;

import com.porest.desk.stock.client.dto.InstrumentRecord;
import com.porest.desk.stock.type.MasterFile;
import com.porest.desk.stock.type.MasterFileFormat;
import com.porest.desk.stock.type.StockMarket;
import com.porest.desk.stock.type.StockSecurityType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * NH 국내 주식({@code m_new_stock.mst}) — 237바이트 고정 레코드.
 *
 * <p>파일 하나에 코스피·코스닥·ETN 이 섞여 있고 {@code sMarket}(1/4/A)으로 갈린다.
 * 오프셋은 {@link NhMasterLayouts#DOMESTIC_STOCK} 이 {@code .h} 대로 들고 있다.
 *
 * <p><b>여기서 얻는 건 종목이 아니라 필드다.</b> 국내는 KIS 가 상위집합이라(정규화 후 교집합
 * 4,301 = NH 전량, KIS 만 195건) NH 로 종목 수를 늘릴 일이 없다. 대신 KIS 가 안 주는
 * {@code nxt_yn}(NXT 거래 가능 여부)이 여기 있고, 그게 나무 국내시세 호출의
 * {@code market_cd}(KRX/NXT/UNT)를 정하는 유일한 근거다.
 */
@Component
public class NhDomesticStockParser extends AbstractMasterFileParser {

    @Override
    public boolean supports(MasterFile file) {
        return file.getFormat() == MasterFileFormat.NH_DOMESTIC_STOCK;
    }

    @Override
    protected List<InstrumentRecord> parseRecords(MasterFile file, byte[] raw, Counters counters) {
        FixedWidthLayout layout = NhMasterLayouts.DOMESTIC_STOCK;
        List<InstrumentRecord> records = new ArrayList<>();

        for (byte[] record : records(file, raw, layout.recordSize())) {
            String symbol = layout.read(record, "sCode").trim();
            // 종목명 선두 1바이트는 지수 마커(* KOSPI200 / # 코스닥150)다. 붙은 채로 두면 검색이 안 맞는다.
            String nameKr = stripIndexMarker(layout.read(record, "sKorName"));
            String nameEn = stripIndexMarker(layout.read(record, "sEngName"));
            StockMarket market = market(layout.read(record, "sMarket"));

            if (symbol.isEmpty() || nameKr.isEmpty() || market == null) {
                counters.skip();
                continue;
            }
            records.add(new InstrumentRecord(
                market, symbol, null, null, nameKr, emptyToNull(nameEn),
                securityType(layout.read(record, "sMarket")), "KRW",
                null,
                "Y".equals(layout.read(record, "nxt_yn")),
                null));
        }
        return records;
    }

    /** 시장구분 1:코스피 4:코스닥 A:ETN. ETN 은 코스피 상장이라 KOSPI 로 본다. */
    private static StockMarket market(String marketFlag) {
        return switch (marketFlag) {
            case "1", "A" -> StockMarket.KOSPI;
            case "4" -> StockMarket.KOSDAQ;
            default -> null;
        };
    }

    /** ETN 은 시장구분 A 로만 알 수 있다 — 별도 종목구분 필드가 없다. */
    private static StockSecurityType securityType(String marketFlag) {
        return "A".equals(marketFlag) ? StockSecurityType.ETF : StockSecurityType.STOCK;
    }
}
