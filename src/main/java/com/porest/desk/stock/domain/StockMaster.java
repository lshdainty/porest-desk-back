package com.porest.desk.stock.domain;

import com.porest.core.type.YNType;
import com.porest.desk.common.domain.AuditingFieldsWithIp;
import com.porest.desk.stock.client.dto.InstrumentRecord;
import com.porest.desk.stock.type.MasterSource;
import com.porest.desk.stock.type.StockMarket;
import com.porest.desk.stock.type.StockSecurityType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * 주식 종목 마스터. KIS 마스터파일과 매일 diff 동기화한다.
 *
 * <p>(market, symbol) 이 유니크 키다. 국내 005930 과 상해 600519 처럼 6자리 숫자 코드가
 * 시장 간 겹치므로 심볼 단독으로는 종목을 특정할 수 없다.
 */
@Entity
@Table(name = "stock_master")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockMaster extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "market_code", nullable = false, length = 10)
    private StockMarket marketCode;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Column(name = "standard_code", length = 12)
    private String standardCode;

    @Column(name = "realtime_symbol", length = 20)
    private String realtimeSymbol;

    @Column(name = "name_kr", nullable = false, length = 100)
    private String nameKr;

    @Column(name = "name_en", length = 100)
    private String nameEn;

    @Enumerated(EnumType.STRING)
    @Column(name = "security_type", nullable = false, length = 10)
    private StockSecurityType securityType;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "source", nullable = false, length = 20)
    private String source;

    // ── NH 마스터파일 보강. 전부 nullable — KIS 만 있는 행에는 값이 없다.
    /**
     * NH 해외종목 통합코드(예: {@code USAAAPL}).
     *
     * <p><b>REST 시세 키가 아니다.</b> 나무 REST 해외 시세({@code /gbstock/quote/v1/current})의
     * {@code iem_cd} 는 티커를 받으므로 조회는 {@code symbol} 로 나간다. GIC 는 나무
     * <b>WebSocket 실시간 채널</b>(RH/rh/RC/rc)의 {@code tr_key}({@code gicz15}) 자리 값이고,
     * 이 서버는 나무 WebSocket 을 구현하지 않았다 — 그래서 지금은 적재만 하고 읽지 않는다
     * ({@code realtimeSymbol} 과 같은 상태다).
     */
    @Column(name = "nh_gic", length = 15)
    private String nhGic;

    /** NXT(넥스트레이드) 거래 가능 여부. 나무 국내시세 market_cd(KRX/NXT/UNT) 판단 근거. */
    @Enumerated(EnumType.STRING)
    @Column(name = "nxt_tradable", length = 1)
    private YNType nxtTradable;

    /** 가격 소수점 자릿수. 없으면 화면이 반올림을 틀린다(미국 4 / 중국 2 / 일본 0~1). */
    @Column(name = "price_decimals")
    private Integer priceDecimals;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_active", nullable = false, length = 1)
    private YNType isActive;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static StockMaster create(MasterSource source, InstrumentRecord record) {
        StockMarket market = record.market();
        StockMaster stock = new StockMaster();
        stock.countryCode = market.getCountryCode();
        stock.marketCode = market;
        stock.symbol = record.symbol();
        stock.standardCode = record.standardCode();
        stock.realtimeSymbol = record.realtimeSymbol();
        stock.nameKr = record.nameKr();
        stock.nameEn = record.nameEn();
        stock.securityType = record.securityType();
        stock.currency = record.currency();
        stock.source = source.name();
        stock.nhGic = record.nhGic();
        stock.nxtTradable = toYn(record.nxtTradable());
        stock.priceDecimals = record.priceDecimals();
        stock.isActive = YNType.Y;
        stock.isDeleted = YNType.N;
        return stock;
    }

    /**
     * 파일 최신값으로 맞춘다. 실제로 달라진 게 있을 때만 true 를 돌려줘
     * 변경 없는 3만여 행의 수정 이력이 매일 갱신되는 것을 막는다.
     */
    public boolean syncFrom(InstrumentRecord record) {
        boolean changed = false;
        if (!Objects.equals(this.standardCode, record.standardCode())) {
            this.standardCode = record.standardCode();
            changed = true;
        }
        if (!Objects.equals(this.realtimeSymbol, record.realtimeSymbol())) {
            this.realtimeSymbol = record.realtimeSymbol();
            changed = true;
        }
        if (!Objects.equals(this.nameKr, record.nameKr())) {
            this.nameKr = record.nameKr();
            changed = true;
        }
        if (!Objects.equals(this.nameEn, record.nameEn())) {
            this.nameEn = record.nameEn();
            changed = true;
        }
        if (this.securityType != record.securityType()) {
            this.securityType = record.securityType();
            changed = true;
        }
        if (!Objects.equals(this.currency, record.currency())) {
            this.currency = record.currency();
            changed = true;
        }
        // 파일에 다시 나타난 종목(재상장·일시 누락)은 되살린다.
        if (this.isActive != YNType.Y) {
            this.isActive = YNType.Y;
            changed = true;
        }
        // 소유 소스가 보강 필드도 주면 함께 반영한다(NH 소유 시장이 그렇다).
        return enrichFrom(record) || changed;
    }

    /**
     * 다른 소스가 주는 보강 필드만 채운다. 종목명·시장·유형은 <b>건드리지 않는다</b> —
     * 소스마다 표기가 달라 서로 덮어쓰면 매일 왔다 갔다 한다.
     *
     * <p>값이 실제로 달라졌을 때만 true 를 돌려줘 변경 없는 행의 수정 이력이 매일 갱신되는 걸 막는다.
     */
    public boolean enrichFrom(InstrumentRecord record) {
        boolean changed = false;
        if (record.nhGic() != null && !Objects.equals(this.nhGic, record.nhGic())) {
            this.nhGic = record.nhGic();
            changed = true;
        }
        YNType nxt = toYn(record.nxtTradable());
        if (nxt != null && this.nxtTradable != nxt) {
            this.nxtTradable = nxt;
            changed = true;
        }
        if (record.priceDecimals() != null && !Objects.equals(this.priceDecimals, record.priceDecimals())) {
            this.priceDecimals = record.priceDecimals();
            changed = true;
        }
        return changed;
    }

    private static YNType toYn(Boolean value) {
        return value == null ? null : (value ? YNType.Y : YNType.N);
    }

    /** 마스터파일에서 사라진 종목(상장폐지 추정). 자산 연결이 남아 있을 수 있어 행은 지우지 않는다. */
    public void deactivate() {
        this.isActive = YNType.N;
    }

    public boolean isActive() {
        return YNType.Y == this.isActive;
    }

    public boolean isDeleted() {
        return YNType.Y == this.isDeleted;
    }
}
