package com.porest.desk.dutchpay.domain;

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
    @JoinColumn(name = "dutch_pay_row_id")
    private DutchPay dutchPay;

    @Column(name = "participant_name", nullable = false, length = 100)
    private String participantName;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_paid", nullable = false, length = 1)
    private YNType isPaid;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    public static DutchPayParticipant create(DutchPay dutchPay, String participantName, Long amount) {
        DutchPayParticipant participant = new DutchPayParticipant();
        participant.dutchPay = dutchPay;
        participant.participantName = participantName;
        participant.amount = amount;
        participant.isPaid = YNType.N;
        return participant;
    }

    public void markPaid() {
        this.isPaid = YNType.Y;
        this.paidAt = LocalDateTime.now();
    }

    public void markUnpaid() {
        this.isPaid = YNType.N;
        this.paidAt = null;
    }

    public void updateAmount(Long amount) {
        this.amount = amount;
    }
}
