package com.porest.desk.stock.client.parser;

import com.porest.desk.stock.client.dto.InstrumentRecord;
import com.porest.desk.stock.type.MasterFile;
import com.porest.desk.stock.type.MasterFileFormat;
import com.porest.desk.stock.type.StockSecurityType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** KIS 국내 업종지수(idxcode.mst) — 시장플래그(1) + 업종코드(4) + 업종명. 업종코드는 전 시장 유니크다. */
@Component
public class KisDomesticIndexParser extends AbstractMasterFileParser {

    @Override
    public boolean supports(MasterFile file) {
        return file.getFormat() == MasterFileFormat.KIS_DOMESTIC_INDEX;
    }

    @Override
    protected List<InstrumentRecord> parseRecords(MasterFile file, byte[] raw, Counters counters) {
        List<InstrumentRecord> records = new ArrayList<>();
        for (String line : lines(raw)) {
            if (line.length() <= 5) {
                counters.skip();
                continue;
            }
            String symbol = line.substring(1, 5).trim();
            String nameKr = line.substring(5).trim();
            if (symbol.isEmpty() || nameKr.isEmpty()) {
                counters.skip();
                continue;
            }
            records.add(InstrumentRecord.kis(file.getMarket(), symbol, null, null,
                nameKr, null, StockSecurityType.INDEX, "KRW"));
        }
        return records;
    }
}
