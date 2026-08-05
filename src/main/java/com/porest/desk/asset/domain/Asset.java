package com.porest.desk.asset.domain;

import com.porest.core.type.YNType;
import com.porest.desk.asset.type.AssetType;
import com.porest.desk.card.domain.CardCatalog;
import com.porest.desk.common.domain.AuditingFieldsWithIp;
import com.porest.desk.user.domain.User;
import java.math.BigDecimal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "asset")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Asset extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_row_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_catalog_row_id")
    private CardCatalog cardCatalog;

    @Column(name = "asset_name", nullable = false, length = 100)
    private String assetName;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 30)
    private AssetType assetType;

    @Column(name = "initial_balance", nullable = false)
    private Long initialBalance;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    /**
     * 원화 환산율 (통화 1단위당 원화). KRW 는 1.
     *
     * <p>{@code currency} 만 있고 환산율이 없으면 USD 1,000 잔고가 순자산에 1,000원으로 더해진다.
     * 합계·순자산은 {@code balance × exchangeRate} 로 환산한다.
     *
     * <p>수동 입력이다 — 토스 환율 API 는 구독(SECURITIES) 게이트 대상이라
     * 미구독자가 외화통장을 못 쓰게 된다. 구독자에게 자동으로 채워 주는 건 이 필드 위에 얹으면 된다.
     */
    @Column(name = "exchange_rate", nullable = false, precision = 18, scale = 6)
    private BigDecimal exchangeRate;

    @Column(name = "color", length = 20)
    private String color;

    @Column(name = "institution", length = 100)
    private String institution;

    @Column(name = "memo", length = 500)
    private String memo;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_included_in_total", nullable = false, length = 1)
    private YNType isIncludedInTotal;

    @Column(name = "credit_limit")
    private Long creditLimit;

    @Column(name = "payment_day")
    private Integer paymentDay;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_asset_row_id")
    private Asset paymentAsset;

    // 토스증권 연동 (INVESTMENT 자산 전용) — 종목코드(toss_symbol)와 보유수량(toss_quantity)을 등록하면
    // 토스 시세(현재가) × 수량으로 평가액을 실시간 계산한다. 타 증권사 보유분도 토스 시세를 빌려 평가.
    // 프로(SECURITIES) + 토스 연결 사용자만 설정 가능. 미연결이면 둘 다 NULL → 기존 수동 입력 유지.
    @Column(name = "toss_symbol", length = 30)
    private String tossSymbol;

    @Column(name = "toss_quantity")
    private Long tossQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static Asset createAsset(User user, String assetName, AssetType assetType, Long balance,
                                     String currency, BigDecimal exchangeRate, String color, String institution,
                                     String memo, Integer sortOrder, YNType isIncludedInTotal,
                                     CardCatalog cardCatalog, Long creditLimit, Integer paymentDay,
                                     Asset paymentAsset) {
        Asset asset = new Asset();
        asset.user = user;
        asset.cardCatalog = cardCatalog;
        asset.assetName = assetName;
        asset.assetType = assetType;
        // 잔액 캐시는 두지 않는다 — 금액은 asset_balance_history 가 진실이고,
        // 캐시를 두면 낡은 값으로 판단하게 된다. 초기 잔액은 INIT 앵커로 남는다.
        asset.initialBalance = balance;
        asset.currency = currency;
        asset.exchangeRate = normalizeRate(exchangeRate, currency);
        asset.color = color;
        asset.institution = institution;
        asset.memo = memo;
        asset.sortOrder = sortOrder;
        asset.isIncludedInTotal = isIncludedInTotal != null ? isIncludedInTotal : YNType.Y;
        asset.creditLimit = creditLimit;
        asset.paymentDay = paymentDay;
        asset.paymentAsset = paymentAsset;
        asset.isDeleted = YNType.N;
        return asset;
    }

    // 잔액은 여기서 받지 않는다 — asset_balance_history 가 단독 관리하고 조회 때 집계한다.
    // 잔액 직접 수정은 AssetBalanceHistoryService.recordManual → recompute 경로로만 반영.
    public void updateAsset(String assetName, AssetType assetType, String currency,
                            BigDecimal exchangeRate, String color, String institution, String memo,
                            YNType isIncludedInTotal, CardCatalog cardCatalog,
                            Long creditLimit, Integer paymentDay, Asset paymentAsset) {
        this.assetName = assetName;
        this.assetType = assetType;
        this.currency = currency;
        this.exchangeRate = normalizeRate(exchangeRate, currency);
        this.color = color;
        this.institution = institution;
        this.memo = memo;
        this.isIncludedInTotal = isIncludedInTotal != null ? isIncludedInTotal : this.isIncludedInTotal;
        this.cardCatalog = cardCatalog;
        this.creditLimit = creditLimit != null ? creditLimit : this.creditLimit;
        this.paymentDay = paymentDay != null ? paymentDay : this.paymentDay;
        this.paymentAsset = paymentAsset != null ? paymentAsset : this.paymentAsset;
    }

    /**
     * 환산율 정규화 — 원화이거나 값이 없으면 1. 0 이하는 순자산을 0·음수로 만들어 버리므로 1 로 막는다.
     */
    private static BigDecimal normalizeRate(BigDecimal rate, String currency) {
        if (currency == null || "KRW".equalsIgnoreCase(currency)) {
            return BigDecimal.ONE;
        }
        return (rate == null || rate.signum() <= 0) ? BigDecimal.ONE : rate;
    }

    /** 외화 자산인가 — 통화가 원화가 아니다. */
    public boolean isForeignCurrency() {
        return currency != null && !"KRW".equalsIgnoreCase(currency);
    }

    /**
     * 원화 환산 잔액 — 합계·순자산은 이 값을 쓴다.
     *
     * <p>USD 1,000 (환산율 1,400) → 1,400,000원. 원화 자산은 환산율이 1이라 그대로다.
     */
    public long balanceInKrw(long rawBalance) {
        if (exchangeRate == null || BigDecimal.ONE.compareTo(exchangeRate) == 0) {
            return rawBalance;
        }
        return exchangeRate.multiply(BigDecimal.valueOf(rawBalance))
            .setScale(0, java.math.RoundingMode.HALF_UP)
            .longValueExact();
    }

    /** 토스 종목 1:1 연결 — 종목코드 + 보유수량. 토스 시세 × 수량으로 평가액 실시간 계산. */
    public void linkToss(String tossSymbol, Long tossQuantity) {
        this.tossSymbol = tossSymbol;
        this.tossQuantity = tossQuantity;
    }

    /** 토스 연결 해제 — 다시 수동 입력 잔액으로 복귀. */
    public void unlinkToss() {
        this.tossQuantity = null;
        this.tossSymbol = null;
    }

    /** 토스 보유종목에 연결된 자산인지. */
    public boolean isTossLinked() {
        return tossSymbol != null && !tossSymbol.isBlank();
    }

    public void updateSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void deleteAsset() {
        this.isDeleted = YNType.Y;
    }
}
