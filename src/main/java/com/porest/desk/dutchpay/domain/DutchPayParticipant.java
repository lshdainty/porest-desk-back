package com.porest.desk.dutchpay.domain;

import com.porest.core.type.YNType;
import com.porest.desk.common.domain.AuditingFieldsWithIp;
import com.porest.desk.user.domain.User;
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

import java.time.LocalDateTime;

@Entity
@Table(name = "dutch_pay_participant")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DutchPayParticipant extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dutch_pay_row_id", nullable = false)
    private DutchPay dutchPay;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_row_id")
    private User user;

    @Column(name = "participant_name", nullable = false, length = 100)
    private String participantName;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_paid", nullable = false, length = 1)
    private YNType isPaid;

    /** [UTC] 시스템 기록 시각 — 저장·비교 UTC, 표시할 때만 사용자 타임존 변환 */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static DutchPayParticipant create(DutchPay dutchPay, User user, String participantName, Long amount) {
        DutchPayParticipant participant = new DutchPayParticipant();
        participant.dutchPay = dutchPay;
        participant.user = user;
        participant.participantName = participantName;
        participant.amount = amount;
        participant.isPaid = YNType.N;
        participant.isDeleted = YNType.N;
        return participant;
    }

    public void deleteParticipant() {
        this.isDeleted = YNType.Y;
    }

    public void markPaid() {
        this.isPaid = YNType.Y;
        this.paidAt = LocalDateTime.now();
    }

    public void markUnpaid() {
        this.isPaid = YNType.N;
        this.paidAt = null;
    }

    /** 이름·금액을 제자리에서 고친다 — 정산 표시(is_paid/paid_at)는 그대로 둔다. */
    public void updateParticipant(User user, String participantName, Long amount) {
        this.user = user;
        this.participantName = participantName;
        this.amount = amount;
    }

    public void updateAmount(Long amount) {
        this.amount = amount;
    }
}
