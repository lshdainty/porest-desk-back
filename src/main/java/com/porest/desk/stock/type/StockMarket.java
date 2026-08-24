package com.porest.desk.stock.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 종목이 상장된 시장. <b>순수한 시장 축</b>이며 파일 메타데이터는 들지 않는다
 * (그건 {@link MasterFile} 에 있다).
 *
 * <p>{@code owner} 는 이 시장 행을 <b>만드는</b> 소스다. KIS 소유 시장에 NH 레코드가 들어오면
 * 행을 만들지 않고 보강만 한다 — 두 소스가 같은 종목을 다른 거래소로 분류하기 때문이다
 * ({@link MasterSource} 주석 참조).
 *
 * <p>{@code nhExchangeCode} 는 NH 해외 마스터의 거래소코드({@code sStockCode})다.
 * NH 레코드를 어느 시장으로 볼지 정하는 유일한 근거라 여기 데이터로 둔다.
 */
@Getter
@RequiredArgsConstructor
public enum StockMarket {

    // ── 국내 (KIS)
    KOSPI("KR", MasterSource.KIS, null),
    KOSDAQ("KR", MasterSource.KIS, null),
    KONEX("KR", MasterSource.KIS, null),
    KRX_IDX("KR", MasterSource.KIS, null),

    // ── 해외 (KIS 가 주는 시장 — NH 는 보강만)
    NAS("US", MasterSource.KIS, "NQQ"),
    NYS("US", MasterSource.KIS, "NYY"),
    AMS("US", MasterSource.KIS, "ASQ"),
    SHS("CN", MasterSource.KIS, "SHC"),
    SHI("CN", MasterSource.KIS, null),
    SZS("CN", MasterSource.KIS, "SHZ"),
    SZI("CN", MasterSource.KIS, null),
    TSE("JP", MasterSource.KIS, "TYO"),
    HKS("HK", MasterSource.KIS, "HKG"),
    HNX("VN", MasterSource.KIS, "HNX"),
    HSX("VN", MasterSource.KIS, "HSX"),

    // ── 해외 (NH 만 주는 시장). KIS 와 안 겹쳐 NH 코드를 그대로 시장코드로 쓴다.
    ASX("AU", MasterSource.NH, "ASX"),
    GER("DE", MasterSource.NH, "GER"),
    LSE("GB", MasterSource.NH, "LSE"),
    JKT("ID", MasterSource.NH, "JKT"),
    BTQ("US", MasterSource.NH, "BTQ"),
    PNK("US", MasterSource.NH, "PNK");

    private final String countryCode;
    /** 이 시장 행을 만드는 소스. 다른 소스의 레코드는 보강만 한다. */
    private final MasterSource owner;
    /** NH 해외 마스터의 거래소코드. NH 가 안 주는 시장은 null. */
    private final String nhExchangeCode;

    private static final Map<String, StockMarket> BY_NH_EXCHANGE = Arrays.stream(values())
        .filter(m -> m.nhExchangeCode != null)
        .collect(Collectors.toUnmodifiableMap(m -> m.nhExchangeCode, Function.identity()));

    /** NH 거래소코드 → 시장. 우리가 모르는 거래소면 null(레코드를 버린다). */
    public static StockMarket byNhExchange(String exchangeCode) {
        return exchangeCode == null ? null : BY_NH_EXCHANGE.get(exchangeCode.trim());
    }

    /** 이 시장 행을 해당 소스가 만들어도 되는가. 아니면 보강만 허용된다. */
    public boolean isOwnedBy(MasterSource source) {
        return owner == source;
    }
}
