package com.porest.desk.asset.domain;

import com.porest.core.type.YNType;
import com.porest.desk.common.domain.AuditingFieldsWithIp;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 신용카드 결제일 이력 — 회차마다 실제로 적용된 결제일(D5, 2026-09-21 확정).
 *
 * <p>결제일을 바꿔도 지난 회차의 결제일은 그대로여야 한다. 자산에 결제일 하나만 두면 25일→5일로
 * 바꾸는 순간 아직 결제 안 된 8월분이 "9/5 에 결제됐어야 하는 회차" 로 다시 계산돼 닫힌 회차로
 * 넘어가고, 그 회차는 영영 결제되지 않는다(QA 24차 10③). 그래서 "이 회차부터 이 결제일" 을 행으로 둔다.
 */
@Entity
@Table(name = "asset_payment_day_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssetPaymentDayHistory extends AuditingFieldsWithIp {

    /** "처음부터" — 카드 등록 전 회차까지 같은 결제일로 본다. */
    public static final LocalDate FROM_THE_BEGINNING = LocalDate.of(1970, 1, 1);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_row_id", nullable = false)
    private Asset asset;

    /** [userClock] 이 회차(청구 기간 시작일 = 그 달 1일)부터 이 결제일을 쓴다. */
    @Column(name = "effective_from_period", nullable = false)
    private LocalDate effectiveFromPeriod;

    @Column(name = "payment_day", nullable = false)
    private Integer paymentDay;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static AssetPaymentDayHistory of(Asset asset, LocalDate effectiveFromPeriod, int paymentDay) {
        AssetPaymentDayHistory h = new AssetPaymentDayHistory();
        h.asset = asset;
        h.effectiveFromPeriod = effectiveFromPeriod;
        h.paymentDay = paymentDay;
        h.isDeleted = YNType.N;
        return h;
    }

    /** 뒤에 바꾼 결제일이 같은 회차부터를 다시 정하면 앞의 것은 물린다. */
    public void delete() {
        this.isDeleted = YNType.Y;
    }
}
