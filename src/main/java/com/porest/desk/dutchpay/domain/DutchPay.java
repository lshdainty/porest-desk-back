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

    @OneToMany(mappedBy = "dutchPay", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DutchPayParticipant> participants = new ArrayList<>();

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
    }

    public void settleAll() {
        this.isSettled = YNType.Y;
        this.participants.forEach(DutchPayParticipant::markPaid);
    }

    public void checkSettled() {
        boolean allPaid = this.participants.stream()
            .allMatch(p -> p.getIsPaid() == YNType.Y);
        this.isSettled = allPaid ? YNType.Y : YNType.N;
    }

    public void addParticipant(DutchPayParticipant participant) {
        this.participants.add(participant);
    }

    public void clearParticipants() {
        this.participants.clear();
    }
}
