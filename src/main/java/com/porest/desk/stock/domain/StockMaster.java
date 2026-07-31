package com.porest.desk.stock.domain;

import com.porest.core.type.YNType;
import com.porest.desk.common.domain.AuditingFieldsWithIp;
import com.porest.desk.stock.client.dto.KisStockRecord;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "is_active", nullable = false, length = 1)
    private YNType isActive;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static StockMaster create(StockMarket market, KisStockRecord record) {
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
        stock.source = "KIS";
        stock.isActive = YNType.Y;
        stock.isDeleted = YNType.N;
        return stock;
    }

    /**
     * 파일 최신값으로 맞춘다. 실제로 달라진 게 있을 때만 true 를 돌려줘
     * 변경 없는 3만여 행의 수정 이력이 매일 갱신되는 것을 막는다.
     */
    public boolean syncFrom(KisStockRecord record) {
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
        return changed;
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
