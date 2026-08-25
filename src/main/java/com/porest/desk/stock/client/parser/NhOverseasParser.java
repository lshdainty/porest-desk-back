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
 * NH 해외 주식({@code m_gtsstock.mst}) — 164바이트 고정 레코드.
 *
 * <p>거래소 15곳이 한 파일에 있고 {@code sStockCode} 로 갈린다. 오프셋은
 * {@link NhMasterLayouts#OVERSEAS} 가 {@code .h} 대로 들고 있다.
 *
 * <p><b>해외는 NH 가 넓다.</b> KIS 에 없는 거래소가 5곳(독일 GER · 영국 LSE · 호주 ASX ·
 * 인도네시아 JKT · 미국 장외 BTQ/PNK) 13,858종목이다. 다만 겹치는 거래소는 분류가 서로 달라
 * ({@code MasterSource} 주석) KIS 소유 시장에는 행을 만들지 않고 보강만 한다.
 *
 * <p>{@code sGIC} 와 {@code sDecimalPoint} 는 KIS 가 안 주는 값이다 — 뒤는 가격 표시
 * 자릿수(미국 4 / 중국 2 / 일본 0~1)라 없으면 화면이 반올림을 틀린다. 앞은 나무
 * <b>WebSocket 실시간 채널</b>(RH/rh/RC/rc)의 {@code tr_key}({@code gicz15}) 용이다 —
 * <b>REST 시세 키가 아니다.</b> {@code /gbstock/quote/v1/current} 의 {@code iem_cd} 는
 * 티커를 받는다. 이 서버는 나무 WebSocket 을 구현하지 않아 지금은 적재만 한다.
 */
@Component
public class NhOverseasParser extends AbstractMasterFileParser {

    /** 종목구분 12 = ETF. 코드표가 공개돼 있지 않아 실측 기준이다. */
    private static final String ISSUE_ETF = "12";

    @Override
    public boolean supports(MasterFile file) {
        return file.getFormat() == MasterFileFormat.NH_OVERSEAS;
    }

    @Override
    protected List<InstrumentRecord> parseRecords(MasterFile file, byte[] raw, Counters counters) {
        FixedWidthLayout layout = NhMasterLayouts.OVERSEAS;
        List<InstrumentRecord> records = new ArrayList<>();

        for (byte[] record : records(file, raw, layout.recordSize())) {
            String symbol = layout.read(record, "sSymbol").trim();
            String nameEn = layout.read(record, "sEngName").trim();
            String nameKr = layout.read(record, "sKorName").trim();
            if (nameKr.isEmpty()) {
                nameKr = nameEn;
            }
            // 우리가 모르는 거래소는 버린다 — 시장을 못 정하면 어느 행에 넣을지 알 수 없다.
            StockMarket market = StockMarket.byNhExchange(layout.read(record, "sStockCode"));

            if (symbol.isEmpty() || nameKr.isEmpty() || market == null) {
                counters.skip();
                continue;
            }
            records.add(new InstrumentRecord(
                market, symbol,
                layout.readOrNull(record, "sStandardCode"),
                null,
                nameKr, emptyToNull(nameEn),
                ISSUE_ETF.equals(layout.read(record, "gIssue")) ? StockSecurityType.ETF : StockSecurityType.STOCK,
                layout.read(record, "gPayMoney"),
                layout.readOrNull(record, "sGIC"),
                null,
                decimals(layout.read(record, "sDecimalPoint"))));
        }
        return records;
    }

    private static Integer decimals(String raw) {
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
