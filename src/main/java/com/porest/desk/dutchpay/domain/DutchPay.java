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

    @Column(name = "dutch_pay_date", nullable = false)
    private LocalDate dutchPayDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_settled", nullable = false, length = 1)
    private YNType isSettled;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    // 참가자는 soft-delete(is_deleted) 로 관리 — 물리 삭제(orphanRemoval) 대신 플래그 처리.
    @OneToMany(mappedBy = "dutchPay", cascade = CascadeType.ALL)
    private List<DutchPayParticipant> participants = new ArrayList<>();

    /** 활성(미삭제) 참가자만. 정산/노출/검증의 기준. */
    public List<DutchPayParticipant> getActiveParticipants() {
        return this.participants.stream()
            .filter(p -> p.getIsDeleted() == YNType.N)
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

    public void settleAll() {
        this.isSettled = YNType.Y;
        getActiveParticipants().forEach(DutchPayParticipant::markPaid);
    }

    public void checkSettled() {
        List<DutchPayParticipant> active = getActiveParticipants();
        boolean allPaid = !active.isEmpty() && active.stream()
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
