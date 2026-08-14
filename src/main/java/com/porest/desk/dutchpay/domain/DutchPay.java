package com.porest.desk.dutchpay.domain;

import com.porest.core.type.YNType;
import com.porest.desk.common.domain.AuditingFieldsWithIp;
import com.porest.desk.dutchpay.type.SplitMethod;
import com.porest.desk.expense.domain.Expense;
import com.porest.desk.user.domain.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "dutch_pay")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DutchPay extends AuditingFieldsWithIp {
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
    @JoinColumn(name = "source_expense_row_id")
    private Expense sourceExpense;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "split_method", nullable = false, length = 20)
    private SplitMethod splitMethod;

    /** [userClock] 사용자·업무가 정한 벽시계 — 타임존 변환 금지(자정 근처 날짜가 밀린다) */
    @Column(name = "dutch_pay_date", nullable = false)
    private LocalDate dutchPayDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_settled", nullable = false, length = 1)
    private YNType isSettled;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    // 참가자는 soft-delete(is_deleted) 로 관리 — 물리 삭제(orphanRemoval) 대신 플래그 처리.
    // 순서를 등록순(row_id)으로 고정한다 — 정렬이 없으면 DB 반환 순서(수정된 행이 밀리는 등)에
    // 따라 매번 달라지는데, 화면은 첫 참가자를 결제자로 그려서 완료 체크 한 번에 결제자가
    // 다른 사람으로 뒤바뀌고 받을 돈 집계까지 흔들린다.
    @OneToMany(mappedBy = "dutchPay", cascade = CascadeType.ALL)
    @OrderBy("rowId ASC")
    private List<DutchPayParticipant> participants = new ArrayList<>();

    /** 활성(미삭제) 참가자만. 정산/노출/검증의 기준. */
    public List<DutchPayParticipant> getActiveParticipants() {
        return this.participants.stream()
            .filter(p -> p.getIsDeleted() == YNType.N)
            .toList();
    }

    /** 결제한 사람. 저장된 값이라 참가자 순서가 바뀌어도 흔들리지 않는다. */
    public DutchPayParticipant getPayer() {
        return getActiveParticipants().stream()
            .filter(DutchPayParticipant::isPayer)
            .findFirst()
            .orElse(null);
    }

    /** 결제자에게 갚아야 할 사람들 — 받을 돈·정산 완료 판정의 대상. */
    public List<DutchPayParticipant> getDebtors() {
        return getActiveParticipants().stream()
            .filter(p -> !p.isPayer())
            .toList();
    }

    public static DutchPay createDutchPay(User user, Expense sourceExpense,
                                           String title, String description,
                                           Long totalAmount, String currency,
                                           SplitMethod splitMethod, LocalDate dutchPayDate) {
        DutchPay dutchPay = new DutchPay();
        dutchPay.user = user;
        dutchPay.sourceExpense = sourceExpense;
        dutchPay.title = title;
        dutchPay.description = description;
        dutchPay.totalAmount = totalAmount;
        dutchPay.currency = currency;
        dutchPay.splitMethod = splitMethod;
        dutchPay.dutchPayDate = dutchPayDate;
        dutchPay.isSettled = YNType.N;
        dutchPay.isDeleted = YNType.N;
        return dutchPay;
    }

    public void updateDutchPay(String title, String description, Long totalAmount,
                                String currency, SplitMethod splitMethod, LocalDate dutchPayDate) {
        this.title = title;
        this.description = description;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.splitMethod = splitMethod;
        this.dutchPayDate = dutchPayDate;
    }

    public void deleteDutchPay() {
        this.isDeleted = YNType.Y;
        // 부모 삭제 시 활성 참가자도 함께 soft-delete (cascade)
        getActiveParticipants().forEach(DutchPayParticipant::deleteParticipant);
    }

    /** 전체 정산 — 갚을 사람들만 완료 처리한다. 결제자는 애초에 갚을 게 없다. */
    public void settleAll() {
        this.isSettled = YNType.Y;
        getDebtors().forEach(DutchPayParticipant::markPaid);
    }

    /**
     * 정산 완료 판정 — <b>결제자를 뺀</b> 참여자가 전부 입금했으면 완료.
     *
     * <p>예전엔 결제자까지 is_paid 여야 완료로 봤는데, 결제자는 갚을 게 없어서 그 체크를
     * 누를 UI 자체가 없었다. 전체 정산으로만 빠져나갈 수 있는 상태였다.
     *
     * <p>참여자가 결제자뿐이면(혼자 쓴 기록) 갚을 사람이 없으니 완료로 본다.
     */
    public void checkSettled() {
        boolean allPaid = !getActiveParticipants().isEmpty() && getDebtors().stream()
            .allMatch(p -> p.getIsPaid() == YNType.Y);
        this.isSettled = allPaid ? YNType.Y : YNType.N;
    }

    public void addParticipant(DutchPayParticipant participant) {
        this.participants.add(participant);
    }

    /** 교체 시 기존 활성 참가자를 soft-delete (물리 삭제 대신 플래그). */
    public void clearParticipants() {
        getActiveParticipants().forEach(DutchPayParticipant::deleteParticipant);
    }
}
