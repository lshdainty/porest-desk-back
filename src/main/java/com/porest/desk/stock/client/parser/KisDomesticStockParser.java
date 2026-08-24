package com.porest.desk.stock.client.parser;

import com.porest.desk.stock.client.dto.InstrumentRecord;
import com.porest.desk.stock.type.MasterFile;
import com.porest.desk.stock.type.MasterFileFormat;
import com.porest.desk.stock.type.StockSecurityType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * KIS 국내 주식(.mst) — 행 단위. 앞은 단축코드(9)+표준코드(12)+한글명(가변),
 * 뒤는 시장마다 길이가 다른 고정영역(tail)이며 첫 2자가 증권그룹구분코드다.
 *
 * <p>KIS 는 구조 정의를 배포하지 않아 tail 길이가 실측값이다({@code MasterFile} 에 데이터로 둔다).
 * NH 는 {@code .h} 를 함께 주므로 그쪽은 오프셋을 세지 않는다.
 *
 * <p>ETN 단축코드에는 {@code Q} 접두사가 붙는다("Q500061"). 벗기지 않으면 같은 종목을
 * NH 와 대사할 때 전량이 어긋난다 — 실측 373건이 통째로 "서로 없는 종목" 으로 나왔다.
 */
@Component
public class KisDomesticStockParser extends AbstractMasterFileParser {

    /** 국내 단축코드 9 + 표준코드(ISIN) 12 */
    private static final int NAME_START = 21;

    /** 증권그룹구분코드 → 정규화. ETP(EF/EN/FE)와 신주인수권·ELW(EW/SW/SR)만 가르고 나머지는 주식. */
    private static final Set<String> ETF_GROUPS = Set.of("EF", "EN", "FE");
    private static final Set<String> WARRANT_GROUPS = Set.of("EW", "SW", "SR");

    @Override
    public boolean supports(MasterFile file) {
        return file.getFormat() == MasterFileFormat.KIS_DOMESTIC_STOCK;
    }

    @Override
    protected List<InstrumentRecord> parseRecords(MasterFile file, byte[] raw, Counters counters) {
        List<InstrumentRecord> records = new ArrayList<>();
        int tail = file.getDomesticTailLength();

        for (String line : lines(raw)) {
            if (line.length() < NAME_START + tail) {
                counters.skip();
                continue;
            }
            String symbol = normalizeSymbol(line.substring(0, 9).trim());
            String standardCode = line.substring(9, NAME_START).trim();
            String nameKr = line.substring(NAME_START, line.length() - tail).trim();
            String groupCode = line.substring(line.length() - tail, line.length() - tail + 2).trim();

            if (symbol.isEmpty() || nameKr.isEmpty()) {
                counters.skip();
                continue;
            }
            records.add(InstrumentRecord.kis(file.getMarket(), symbol,
                emptyToNull(standardCode), null, nameKr, null, securityType(groupCode), "KRW"));
        }
        return records;
    }

    /**
     * ETN 단축코드의 {@code Q} 접두사를 벗긴다.
     *
     * <p>KIS 는 "Q500061", NH 는 "500023" 으로 같은 체계의 코드를 다르게 적는다.
     * 실제 상장 종목코드는 접두사 없는 6자리라 그쪽으로 맞춘다.
     */
    private static String normalizeSymbol(String raw) {
        return raw.length() == 7 && Character.isLetter(raw.charAt(0)) ? raw.substring(1) : raw;
    }

    private static StockSecurityType securityType(String groupCode) {
        if (ETF_GROUPS.contains(groupCode)) {
            return StockSecurityType.ETF;
        }
        if (WARRANT_GROUPS.contains(groupCode)) {
            return StockSecurityType.WARRANT;
        }
        return StockSecurityType.STOCK;
    }
}
