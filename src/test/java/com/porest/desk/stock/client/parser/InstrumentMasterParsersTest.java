package com.porest.desk.stock.client.parser;

import com.porest.desk.stock.client.dto.InstrumentRecord;
import com.porest.desk.stock.type.MasterFile;
import com.porest.desk.stock.type.StockMarket;
import com.porest.desk.stock.type.StockSecurityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 마스터파일 파서 — 포맷별 규칙과 라우팅.
 *
 * <p>포맷이 5가지(KIS 3 · NH 2)라 한 파일에 몰아 두면 한쪽을 고칠 때 다른 쪽이 깨진다.
 * 여기서는 <b>포맷마다 달라지는 지점</b>과 <b>공통 방어</b>(레코드 경계·깨진 행)를 본다.
 */
class InstrumentMasterParsersTest {

    private static final Charset CP949 = Charset.forName("MS949");

    private final InstrumentMasterParsers parsers = new InstrumentMasterParsers(List.of(
        new KisDomesticStockParser(), new KisDomesticIndexParser(), new KisOverseasParser(),
        new NhDomesticStockParser(), new NhOverseasParser()));

    // ── 픽스처 ────────────────────────────────────────────────────────

    /** KIS 국내 .mst 1행: 단축코드(9) + ISIN(12) + 한글명 + tail(첫 2자 = 증권그룹구분코드) */
    private static String kisDomesticLine(MasterFile file, String symbol, String isin, String name, String group) {
        int tail = file.getDomesticTailLength();
        return pad(symbol, 9) + pad(isin, 12) + name + group + " ".repeat(tail - group.length());
    }

    /** KIS 해외 .cod 1행: 탭 구분 24컬럼 */
    private static String kisOverseasLine(String symbol, String realtime, String nameKr, String nameEn,
                                          String type, String currency) {
        String[] cols = new String[24];
        Arrays.fill(cols, "0");
        cols[4] = symbol;
        cols[5] = realtime;
        cols[6] = nameKr;
        cols[7] = nameEn;
        cols[8] = type;
        cols[9] = currency;
        return String.join("\t", cols);
    }

    /**
     * NH 고정폭 레코드를 레이아웃대로 만든다.
     *
     * <p>오프셋을 테스트가 다시 세지 않는다 — 레이아웃이 유일한 근거여야 실제 파싱과 같은 것을 본다.
     */
    private static byte[] nhRecord(FixedWidthLayout layout, Map<String, String> values) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(layout.recordSize());
        for (String field : layout.fieldNames()) {
            int length = layout.lengthOf(field);
            byte[] raw = values.getOrDefault(field, "").getBytes(CP949);
            byte[] cell = new byte[length];
            Arrays.fill(cell, (byte) ' ');
            System.arraycopy(raw, 0, cell, 0, Math.min(raw.length, length));
            out.writeBytes(cell);
        }
        byte[] record = out.toByteArray();
        record[record.length - 1] = '\n';
        return record;
    }

    private static Map<String, String> values(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private static String pad(String value, int width) {
        return value + " ".repeat(width - value.length());
    }

    private static byte[] bytes(String text) {
        return text.getBytes(CP949);
    }

    // ── KIS ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("KIS")
    class Kis {

        @Test
        @DisplayName("국내 주식 — 고정폭에서 잘라내고 그룹코드로 유형을 가른다")
        void domesticStock() {
            byte[] raw = bytes(String.join("\n",
                kisDomesticLine(MasterFile.KIS_KOSPI, "005930", "KR7005930003", "삼성전자", "ST"),
                kisDomesticLine(MasterFile.KIS_KOSPI, "069500", "KR7069500007", "KODEX 200", "EF")));

            List<InstrumentRecord> records = parsers.parse(MasterFile.KIS_KOSPI, raw);

            assertThat(records).hasSize(2);
            assertThat(records.get(0).symbol()).isEqualTo("005930");
            assertThat(records.get(0).standardCode()).isEqualTo("KR7005930003");
            assertThat(records.get(0).securityType()).isEqualTo(StockSecurityType.STOCK);
            assertThat(records.get(0).market()).isEqualTo(StockMarket.KOSPI);
            assertThat(records.get(1).securityType()).isEqualTo(StockSecurityType.ETF);
        }

        @Test
        @DisplayName("ETN 단축코드의 Q 접두사를 벗긴다 — 안 벗기면 NH 와 한 종목도 안 맞는다")
        void stripsEtnPrefix() {
            byte[] raw = bytes(kisDomesticLine(MasterFile.KIS_KOSPI, "Q500061", "KRG500000614", "신한 인버스 ETN", "EN"));

            List<InstrumentRecord> records = parsers.parse(MasterFile.KIS_KOSPI, raw);

            assertThat(records).singleElement()
                .extracting(InstrumentRecord::symbol).isEqualTo("500061");
        }

        @Test
        @DisplayName("업종지수 — 시장플래그를 떼고 업종코드 4자리를 쓴다")
        void domesticIndex() {
            byte[] raw = bytes("10001코스피");

            List<InstrumentRecord> records = parsers.parse(MasterFile.KIS_KRX_IDX, raw);

            assertThat(records).singleElement().satisfies(r -> {
                assertThat(r.symbol()).isEqualTo("0001");
                assertThat(r.nameKr()).isEqualTo("코스피");
                assertThat(r.securityType()).isEqualTo(StockSecurityType.INDEX);
            });
        }

        @Test
        @DisplayName("해외 — 한글명이 비면 영문명으로 채운다 (name_kr 이 NOT NULL 이라서)")
        void overseasFallsBackToEnglishName() {
            byte[] raw = bytes(kisOverseasLine("AAPL", "NASAAPL", "", "APPLE INC", "2", "USD"));

            List<InstrumentRecord> records = parsers.parse(MasterFile.KIS_NAS, raw);

            assertThat(records).singleElement().satisfies(r -> {
                assertThat(r.nameKr()).isEqualTo("APPLE INC");
                assertThat(r.realtimeSymbol()).isEqualTo("NASAAPL");
                assertThat(r.currency()).isEqualTo("USD");
            });
        }

        @Test
        @DisplayName("깨진 행은 건너뛰되 나머지는 살린다 — 한 행 때문에 그날 배치가 죽으면 안 된다")
        void skipsBrokenLine() {
            byte[] raw = bytes(String.join("\n",
                "짧아서 못 자르는 행",
                kisDomesticLine(MasterFile.KIS_KOSPI, "005930", "KR7005930003", "삼성전자", "ST")));

            assertThat(parsers.parse(MasterFile.KIS_KOSPI, raw)).hasSize(1);
        }
    }

    // ── NH ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("NH")
    class Nh {

        @Test
        @DisplayName("국내 — 종목명 선두 지수마커(*/#)를 뗀다. 붙은 채로 두면 검색·정렬이 어긋난다")
        void domesticStripsIndexMarker() {
            byte[] raw = nhRecord(NhMasterLayouts.DOMESTIC_STOCK, values(
                "sCode", "005930", "sMarket", "1",
                "sKorName", "*삼성전자", "sEngName", "*SamsungElec", "nxt_yn", "Y"));

            List<InstrumentRecord> records = parsers.parse(MasterFile.NH_DOMESTIC, raw);

            assertThat(records).singleElement().satisfies(r -> {
                assertThat(r.nameKr()).isEqualTo("삼성전자");
                assertThat(r.nameEn()).isEqualTo("SamsungElec");
                assertThat(r.market()).isEqualTo(StockMarket.KOSPI);
                // KIS 가 안 주는 값 — 나무 국내시세 market_cd 판단의 유일한 근거다.
                assertThat(r.nxtTradable()).isTrue();
            });
        }

        @Test
        @DisplayName("국내 — 시장구분으로 코스피/코스닥을 가르고 ETN(A)은 코스피 상장으로 본다")
        void domesticResolvesMarket() {
            byte[] raw = concat(
                nhRecord(NhMasterLayouts.DOMESTIC_STOCK, values("sCode", "247540", "sMarket", "4", "sKorName", "에코프로비엠")),
                nhRecord(NhMasterLayouts.DOMESTIC_STOCK, values("sCode", "500023", "sMarket", "A", "sKorName", "신한 콩 선물 ETN")));

            List<InstrumentRecord> records = parsers.parse(MasterFile.NH_DOMESTIC, raw);

            assertThat(records).extracting(InstrumentRecord::market)
                .containsExactly(StockMarket.KOSDAQ, StockMarket.KOSPI);
            assertThat(records.get(1).securityType()).isEqualTo(StockSecurityType.ETF);
        }

        @Test
        @DisplayName("해외 — 거래소코드로 시장을 정하고 KIS 가 안 주는 GIC·소수점자릿수를 싣는다")
        void overseasCarriesEnrichment() {
            byte[] raw = nhRecord(NhMasterLayouts.OVERSEAS, values(
                "sGIC", "USAAAPL", "sKorName", "애플", "sEngName", "APPLE INC",
                "sNationCode", "USA", "sSymbol", "AAPL", "sStockCode", "NQQ",
                "sStandardCode", "US0378331005", "gPayMoney", "USD", "sDecimalPoint", "4"));

            List<InstrumentRecord> records = parsers.parse(MasterFile.NH_OVERSEAS, raw);

            assertThat(records).singleElement().satisfies(r -> {
                assertThat(r.market()).isEqualTo(StockMarket.NAS);
                assertThat(r.nhGic()).isEqualTo("USAAAPL");
                assertThat(r.priceDecimals()).isEqualTo(4);
                assertThat(r.standardCode()).isEqualTo("US0378331005");
            });
        }

        @Test
        @DisplayName("해외 — KIS 에 없는 거래소(독일)도 시장으로 잡는다")
        void overseasResolvesNhOnlyMarket() {
            byte[] raw = nhRecord(NhMasterLayouts.OVERSEAS, values(
                "sKorName", "지멘스", "sSymbol", "SIE", "sStockCode", "GER",
                "sNationCode", "DEU", "gPayMoney", "EUR"));

            assertThat(parsers.parse(MasterFile.NH_OVERSEAS, raw))
                .singleElement()
                .satisfies(r -> assertThat(r.market()).isEqualTo(StockMarket.GER));
        }

        @Test
        @DisplayName("해외 — 모르는 거래소는 버린다. 시장을 못 정하면 어느 행에 넣을지 알 수 없다")
        void overseasDropsUnknownExchange() {
            byte[] raw = nhRecord(NhMasterLayouts.OVERSEAS, values(
                "sKorName", "정체불명", "sSymbol", "XXX", "sStockCode", "ZZZ"));

            assertThat(parsers.parse(MasterFile.NH_OVERSEAS, raw)).isEmpty();
        }

        @Test
        @DisplayName("레코드 경계가 안 맞으면 한 건도 만들지 않는다 — 밀린 채로 자르면 그럴듯한 쓰레기가 나온다")
        void rejectsMisalignedFile() {
            byte[] good = nhRecord(NhMasterLayouts.OVERSEAS, values("sSymbol", "AAPL", "sStockCode", "NQQ", "sKorName", "애플"));
            byte[] truncated = Arrays.copyOf(good, good.length - 3);

            assertThat(parsers.parse(MasterFile.NH_OVERSEAS, concat(good, truncated))).isEmpty();
        }
    }

    // ── 레이아웃·라우팅 ────────────────────────────────────────────────

    @Test
    @DisplayName("레이아웃은 필드 길이 합이 레코드 크기와 같아야 한다 — .h 의 MST_ASSERT_SIZE 와 같은 장치")
    void layoutSelfChecks() {
        assertThat(NhMasterLayouts.DOMESTIC_STOCK.recordSize()).isEqualTo(237);
        assertThat(NhMasterLayouts.OVERSEAS.recordSize()).isEqualTo(164);

        assertThatThrownBy(() -> FixedWidthLayout.of(10, "a", 3, "b", 3))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("레코드 크기 불일치");
    }

    @Test
    @DisplayName("등록된 마스터파일 전부에 맡을 파서가 있다 — 없으면 기동할 때 터진다")
    void everyFileHasParser() {
        parsers.verifyEveryFileCovered();

        InstrumentMasterParsers incomplete = new InstrumentMasterParsers(List.of(new KisOverseasParser()));
        assertThatThrownBy(incomplete::verifyEveryFileCovered)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("파서가 없는 마스터파일");
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
