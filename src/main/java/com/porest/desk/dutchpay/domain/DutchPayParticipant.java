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

    /**
     * 결제자 여부 — 한 정산에 Y 는 한 명. 나머지는 그 사람에게 갚을 참여자다.
     *
     * <p>예전엔 이 값이 없어 화면이 참가자 순서로 결제자를 추측했다(웹은 userRowId 보유자,
     * 앱은 index 0). 참가자를 지웠다 다시 넣으면 row_id 가 재채번돼 결제자가 바뀌었고,
     * 거기 걸린 "받을 돈" 집계까지 같이 틀어졌다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "is_payer", nullable = false, length = 1)
    private YNType isPayer;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_paid", nullable = false, length = 1)
    private YNType isPaid;

    /** [UTC] 시스템 기록 시각 — 저장·비교 UTC, 표시할 때만 사용자 타임존 변환 */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static DutchPayParticipant create(DutchPay dutchPay, User user, String participantName,
                                             Long amount, boolean isPayer) {
        DutchPayParticipant participant = new DutchPayParticipant();
        participant.dutchPay = dutchPay;
        participant.user = user;
        participant.participantName = participantName;
        participant.amount = amount;
        participant.isPayer = YNType.from(isPayer);
        participant.isPaid = YNType.N;
        participant.isDeleted = YNType.N;
        return participant;
    }

    /** 이 사람이 결제했는가. 결제자는 받을 쪽이라 정산 완료 판정에서 빠진다. */
    public boolean isPayer() {
        return this.isPayer == YNType.Y;
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

    /** 이름·금액·결제자 여부를 제자리에서 고친다 — 정산 표시(is_paid/paid_at)는 그대로 둔다. */
    public void updateParticipant(User user, String participantName, Long amount, boolean isPayer) {
        this.user = user;
        this.participantName = participantName;
        this.amount = amount;
        this.isPayer = YNType.from(isPayer);
    }

    public void updateAmount(Long amount) {
        this.amount = amount;
    }

    /**
     * 이름을 <b>한 트랜잭션 안에서만</b> 쓰는 임시값으로 비켜 둔다 — 밖으로 새지 않는다.
     *
     * <p>한 정산 안의 활성 참가자 이름이 DB UNIQUE 로 묶이면, 두 참가자의 이름을 서로 맞바꾸는
     * 저장(A↔B)이 중간 상태에서 제약에 걸린다 — UPDATE 는 한 문장씩 나가므로 순서를 어떻게
     * 잡아도 잠깐 같은 이름 둘이 된다. 최종 이름을 쓰기 전에 바뀌는 행을 전부 이 자리로
     * 비켜 두면 그 순간이 사라진다.
     *
     * <p>임시값이 진짜 이름과 부딪히지 않는 근거는 <b>앞공백</b>이다. 저장되는 이름은 전부
     * {@code NameNormalizer} 를 지나 trim 되므로 공백으로 시작할 수 없다. 행마다 다른 값이어야
     * 임시값끼리도 안 부딪히므로 {@code row_id} 를 붙인다.
     */
    public void parkNameForRename() {
        this.participantName = " tmp:" + this.rowId;
    }
}
