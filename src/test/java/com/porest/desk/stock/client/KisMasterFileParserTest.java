package com.porest.desk.stock.client;

import com.porest.desk.stock.client.dto.KisStockRecord;
import com.porest.desk.stock.type.StockMarket;
import com.porest.desk.stock.type.StockSecurityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KIS 마스터파일 파싱 테스트.
 *
 * <p>포맷 근거는 한국투자 공식 정제 샘플이다. 국내 .mst 는 시장마다 고정영역(tail) 길이가 다르고
 * (KOSPI 227 / KOSDAQ 221 / KONEX 184), 원본에 깨진 행이 섞여도 행 단위로만 건너뛰어야 한다.
 */
class KisMasterFileParserTest {

    /** 국내 .mst 1행: 단축코드(9) + 표준코드(12) + 한글명 + tail(첫 2자 = 증권그룹구분코드) */
    private String domesticLine(StockMarket market, String symbol, String isin, String name, String groupCode) {
        int tail = market.getDomesticTailLength();
        return pad(symbol, 9) + pad(isin, 12) + name + groupCode + " ".repeat(tail - groupCode.length());
    }

    /** 해외 .cod 1행: 탭 구분 24컬럼 */
    private String overseasLine(String symbol, String realtimeSymbol, String nameKr, String nameEn,
                                String securityType, String currency) {
        String[] cols = new String[24];
        cols[0] = "US";
        cols[1] = "512";
        cols[2] = "NAS";
        cols[3] = "나스닥";
        cols[4] = symbol;
        cols[5] = realtimeSymbol;
        cols[6] = nameKr;
        cols[7] = nameEn;
        cols[8] = securityType;
        cols[9] = currency;
        for (int i = 10; i < 24; i++) {
            cols[i] = "0";
        }
        return String.join("\t", cols);
    }

    private String pad(String value, int width) {
        return value + " ".repeat(width - value.length());
    }

    @Test
    @DisplayName("국내 주식 — 단축코드·ISIN·한글명·그룹코드를 고정폭에서 잘라내고 KRW 로 채운다")
    void parsesDomesticStock() {
        String text = String.join("\n",
            domesticLine(StockMarket.KOSPI, "005930", "KR7005930003", "삼성전자", "ST"),
            domesticLine(StockMarket.KOSPI, "069500", "KR7069500007", "KODEX 200", "EF"));

        List<KisStockRecord> records = KisMasterFileParser.parse(StockMarket.KOSPI, text);

        assertThat(records).hasSize(2);
        KisStockRecord samsung = records.get(0);
        assertThat(samsung.symbol()).isEqualTo("005930");
        assertThat(samsung.standardCode()).isEqualTo("KR7005930003");
        assertThat(samsung.nameKr()).isEqualTo("삼성전자");
        assertThat(samsung.securityType()).isEqualTo(StockSecurityType.STOCK);
        assertThat(samsung.currency()).isEqualTo("KRW");
        assertThat(samsung.realtimeSymbol()).isNull();
        assertThat(records.get(1).securityType()).isEqualTo(StockSecurityType.ETF);
        assertThat(records.get(1).nameKr()).isEqualTo("KODEX 200");
    }

    @Test
    @DisplayName("국내 주식 — 그룹코드가 ETP(EN)면 ETF, 신주인수권(SR)이면 WARRANT 로 정규화한다")
    void normalizesDomesticSecurityType() {
        String text = String.join("\n",
            domesticLine(StockMarket.KOSDAQ, "580011", "KR7580011000", "삼성 ETN", "EN"),
            domesticLine(StockMarket.KOSDAQ, "90043X1", "KR9990043X15", "우리금융 신주인수권", "SR"));

        List<KisStockRecord> records = KisMasterFileParser.parse(StockMarket.KOSDAQ, text);

        assertThat(records).extracting(KisStockRecord::securityType)
            .containsExactly(StockSecurityType.ETF, StockSecurityType.WARRANT);
    }

    @Test
    @DisplayName("국내 주식 — tail 보다 짧은 깨진 행은 건너뛰고 나머지는 정상 파싱한다")
    void skipsBrokenDomesticLine() {
        String text = String.join("\n",
            "깨진행",
            domesticLine(StockMarket.KONEX, "0070X0", "KR70070X0000", "에스테크엠", "ST"));

        List<KisStockRecord> records = KisMasterFileParser.parse(StockMarket.KONEX, text);

        assertThat(records).hasSize(1);
        assertThat(records.get(0).symbol()).isEqualTo("0070X0");
    }

    @Test
    @DisplayName("국내 업종지수 — 시장플래그를 떼고 업종코드 4자리와 업종명을 INDEX 로 적재한다")
    void parsesDomesticIndex() {
        String text = String.join("\n",
            "00001종합                    ",
            "11001KOSDAQ                  ");

        List<KisStockRecord> records = KisMasterFileParser.parse(StockMarket.KRX_IDX, text);

        assertThat(records).hasSize(2);
        assertThat(records.get(0).symbol()).isEqualTo("0001");
        assertThat(records.get(0).nameKr()).isEqualTo("종합");
        assertThat(records.get(0).securityType()).isEqualTo(StockSecurityType.INDEX);
        assertThat(records.get(1).symbol()).isEqualTo("1001");
        assertThat(records.get(1).nameKr()).isEqualTo("KOSDAQ");
    }

    @Test
    @DisplayName("해외 — 심볼·실시간심볼·한글명·영문명·유형·통화를 탭 컬럼에서 읽는다")
    void parsesOverseas() {
        String text = String.join("\n",
            overseasLine("AAPL", "NASAAPL", "애플", "APPLE INC", "2", "USD"),
            overseasLine("QQQ", "NASQQQ", "인베스코 QQQ", "INVESCO QQQ", "3", "USD"),
            overseasLine("COMP", "NASCOMP", "나스닥종합", "NASDAQ COMPOSITE", "1", "USD"));

        List<KisStockRecord> records = KisMasterFileParser.parse(StockMarket.NAS, text);

        assertThat(records).hasSize(3);
        KisStockRecord apple = records.get(0);
        assertThat(apple.symbol()).isEqualTo("AAPL");
        assertThat(apple.realtimeSymbol()).isEqualTo("NASAAPL");
        assertThat(apple.nameKr()).isEqualTo("애플");
        assertThat(apple.nameEn()).isEqualTo("APPLE INC");
        assertThat(apple.securityType()).isEqualTo(StockSecurityType.STOCK);
        assertThat(apple.currency()).isEqualTo("USD");
        assertThat(records.get(1).securityType()).isEqualTo(StockSecurityType.ETF);
        assertThat(records.get(2).securityType()).isEqualTo(StockSecurityType.INDEX);
    }

    @Test
    @DisplayName("해외 — 한글명이 비면 영문명으로 채우고, 컬럼이 모자란 깨진 행은 건너뛴다")
    void fallsBackToEnglishNameAndSkipsBrokenOverseasLine() {
        String text = String.join("\n",
            overseasLine("TSLA", "NASTSLA", "", "TESLA INC", "2", "USD"),
            "깨진\t행");

        List<KisStockRecord> records = KisMasterFileParser.parse(StockMarket.NAS, text);

        assertThat(records).hasSize(1);
        assertThat(records.get(0).nameKr()).isEqualTo("TESLA INC");
        assertThat(records.get(0).nameEn()).isEqualTo("TESLA INC");
    }

    @Test
    @DisplayName("시장 내 중복 심볼은 첫 행만 남긴다 — (market, symbol) 유니크 제약 방어")
    void deduplicatesSymbolWithinMarket() {
        String text = String.join("\n",
            overseasLine("AAPL", "NASAAPL", "애플", "APPLE INC", "2", "USD"),
            overseasLine("AAPL", "NASAAPL", "애플 중복", "APPLE DUP", "2", "USD"));

        List<KisStockRecord> records = KisMasterFileParser.parse(StockMarket.NAS, text);

        assertThat(records).hasSize(1);
        assertThat(records.get(0).nameKr()).isEqualTo("애플");
    }
}
