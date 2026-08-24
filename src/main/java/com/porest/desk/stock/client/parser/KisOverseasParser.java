package com.porest.desk.stock.client.parser;

import com.porest.desk.stock.client.dto.InstrumentRecord;
import com.porest.desk.stock.type.MasterFile;
import com.porest.desk.stock.type.MasterFileFormat;
import com.porest.desk.stock.type.StockSecurityType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * KIS 해외(.cod) — 탭 구분 24컬럼. 쓰는 컬럼: 심볼(4), 실시간심볼(5), 한글명(6), 영문명(7),
 * 종목유형(8, 1:지수/2:주식/3:ETP/4:워런트), 통화(9).
 *
 * <p>고정폭이 아니라 구분자 포맷이라 {@link FixedWidthLayout} 을 쓰지 않는다.
 */
@Component
public class KisOverseasParser extends AbstractMasterFileParser {

    private static final int MIN_COLUMNS = 10;

    @Override
    public boolean supports(MasterFile file) {
        return file.getFormat() == MasterFileFormat.KIS_OVERSEAS;
    }

    @Override
    protected List<InstrumentRecord> parseRecords(MasterFile file, byte[] raw, Counters counters) {
        List<InstrumentRecord> records = new ArrayList<>();
        for (String line : lines(raw)) {
            String[] cols = line.split("\t");
            if (cols.length < MIN_COLUMNS) {
                counters.skip();
                continue;
            }
            String symbol = cols[4].trim();
            String nameEn = cols[7].trim();
            // 한글명이 비면 영문명으로 채운다. name_kr 이 NOT NULL 이고 검색 기본 표기라서다.
            String nameKr = cols[6].trim().isEmpty() ? nameEn : cols[6].trim();

            if (symbol.isEmpty() || nameKr.isEmpty()) {
                counters.skip();
                continue;
            }
            records.add(InstrumentRecord.kis(file.getMarket(), symbol, null,
                emptyToNull(cols[5].trim()), nameKr, emptyToNull(nameEn),
                securityType(cols[8].trim()), cols[9].trim()));
        }
        return records;
    }

    private static StockSecurityType securityType(String code) {
        return switch (code) {
            case "1" -> StockSecurityType.INDEX;
            case "3" -> StockSecurityType.ETF;
            case "4" -> StockSecurityType.WARRANT;
            default -> StockSecurityType.STOCK;
        };
    }
}
