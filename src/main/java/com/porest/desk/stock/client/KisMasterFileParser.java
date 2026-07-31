package com.porest.desk.stock.client;

import com.porest.desk.stock.client.dto.KisStockRecord;
import com.porest.desk.stock.type.StockMarket;
import com.porest.desk.stock.type.StockSecurityType;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * KIS 마스터파일 텍스트를 종목 레코드로 정규화한다.
 *
 * <p>포맷 근거는 한국투자 공식 정제 샘플(stocks_info)이다. 원본에 간혹 깨진 행이 섞여도
 * 배치 전체가 죽지 않도록 행 단위로 건너뛰고 건수만 남긴다.
 *
 * @see <a href="https://github.com/koreainvestment/open-trading-api/tree/main/stocks_info">KIS 종목정보 파일 명세</a>
 */
@Slf4j
public final class KisMasterFileParser {

    /** 국내 단축코드 9 + 표준코드(ISIN) 12 */
    private static final int DOMESTIC_NAME_START = 21;

    /** 국내 증권그룹구분코드 → 정규화. ETP(EF/EN/FE)와 신주인수권·ELW(EW/SW/SR)만 가르고 나머지는 주식으로 본다. */
    private static final Set<String> DOMESTIC_ETF_GROUPS = Set.of("EF", "EN", "FE");
    private static final Set<String> DOMESTIC_WARRANT_GROUPS = Set.of("EW", "SW", "SR");

    private KisMasterFileParser() {
    }

    public static List<KisStockRecord> parse(StockMarket market, String text) {
        return switch (market.getFileFormat()) {
            case DOMESTIC_STOCK -> parseDomesticStock(market, text);
            case DOMESTIC_INDEX -> parseDomesticIndex(market, text);
            case OVERSEAS -> parseOverseas(market, text);
        };
    }

    /**
     * 국내 주식(.mst) — 고정폭. 앞은 단축코드(9)+표준코드(12)+한글명(가변),
     * 뒤는 시장마다 길이가 다른 고정영역(tail)이며 첫 2자가 증권그룹구분코드다.
     */
    private static List<KisStockRecord> parseDomesticStock(StockMarket market, String text) {
        List<KisStockRecord> records = new ArrayList<>();
        int tail = market.getDomesticTailLength();
        int skipped = 0;

        for (String line : splitLines(text)) {
            if (line.length() < DOMESTIC_NAME_START + tail) {
                skipped++;
                continue;
            }
            String symbol = line.substring(0, 9).trim();
            String standardCode = line.substring(9, DOMESTIC_NAME_START).trim();
            String nameKr = line.substring(DOMESTIC_NAME_START, line.length() - tail).trim();
            String groupCode = line.substring(line.length() - tail, line.length() - tail + 2).trim();

            if (symbol.isEmpty() || nameKr.isEmpty()) {
                skipped++;
                continue;
            }
            records.add(new KisStockRecord(
                symbol,
                standardCode.isEmpty() ? null : standardCode,
                null,
                nameKr,
                null,
                toDomesticSecurityType(groupCode),
                "KRW"
            ));
        }
        return dedupeBySymbol(market, records, skipped);
    }

    /** 국내 업종지수(idxcode.mst) — 시장플래그(1) + 업종코드(4) + 업종명. 업종코드는 전 시장 유니크다. */
    private static List<KisStockRecord> parseDomesticIndex(StockMarket market, String text) {
        List<KisStockRecord> records = new ArrayList<>();
        int skipped = 0;

        for (String line : splitLines(text)) {
            if (line.length() <= 5) {
                skipped++;
                continue;
            }
            String symbol = line.substring(1, 5).trim();
            String nameKr = line.substring(5).trim();
            if (symbol.isEmpty() || nameKr.isEmpty()) {
                skipped++;
                continue;
            }
            records.add(new KisStockRecord(symbol, null, null, nameKr, null, StockSecurityType.INDEX, "KRW"));
        }
        return dedupeBySymbol(market, records, skipped);
    }

    /**
     * 해외(.cod) — 탭 구분 24컬럼. 사용 컬럼: 심볼(4), 실시간심볼(5), 한글명(6), 영문명(7),
     * 종목유형(8, 1:지수/2:주식/3:ETP/4:워런트), 통화(9).
     */
    private static List<KisStockRecord> parseOverseas(StockMarket market, String text) {
        List<KisStockRecord> records = new ArrayList<>();
        int skipped = 0;

        for (String line : splitLines(text)) {
            String[] cols = line.split("\t");
            if (cols.length < 10) {
                skipped++;
                continue;
            }
            String symbol = cols[4].trim();
            String nameKr = cols[6].trim();
            String nameEn = cols[7].trim();
            // 한글명이 비면 영문명으로 채운다. name_kr 이 NOT NULL 이고 검색 기본 표기라서다.
            if (nameKr.isEmpty()) {
                nameKr = nameEn;
            }
            if (symbol.isEmpty() || nameKr.isEmpty()) {
                skipped++;
                continue;
            }
            records.add(new KisStockRecord(
                symbol,
                null,
                emptyToNull(cols[5].trim()),
                nameKr,
                emptyToNull(nameEn),
                toOverseasSecurityType(cols[8].trim()),
                cols[9].trim()
            ));
        }
        return dedupeBySymbol(market, records, skipped);
    }

    /** 시장 내 심볼이 유니크 키라 중복 행은 첫 행만 남긴다. 실측상 없지만 원본 오염 방어다. */
    private static List<KisStockRecord> dedupeBySymbol(StockMarket market, List<KisStockRecord> records, int skipped) {
        Map<String, KisStockRecord> bySymbol = new LinkedHashMap<>();
        for (KisStockRecord record : records) {
            bySymbol.putIfAbsent(record.symbol(), record);
        }
        int duplicated = records.size() - bySymbol.size();
        if (skipped > 0 || duplicated > 0) {
            log.warn("KIS 마스터파일 파싱 - 건너뜀: market={}, 깨진행={}건, 중복심볼={}건", market, skipped, duplicated);
        }
        return List.copyOf(bySymbol.values());
    }

    private static StockSecurityType toDomesticSecurityType(String groupCode) {
        if (DOMESTIC_ETF_GROUPS.contains(groupCode)) {
            return StockSecurityType.ETF;
        }
        if (DOMESTIC_WARRANT_GROUPS.contains(groupCode)) {
            return StockSecurityType.WARRANT;
        }
        return StockSecurityType.STOCK;
    }

    private static StockSecurityType toOverseasSecurityType(String code) {
        return switch (code) {
            case "1" -> StockSecurityType.INDEX;
            case "3" -> StockSecurityType.ETF;
            case "4" -> StockSecurityType.WARRANT;
            default -> StockSecurityType.STOCK;
        };
    }

    private static List<String> splitLines(String text) {
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\n")) {
            String stripped = line.replace("\r", "");
            if (!stripped.isBlank()) {
                lines.add(stripped);
            }
        }
        return lines;
    }

    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }
}
