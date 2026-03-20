package com.porest.desk.expense.domain;

import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.common.domain.AuditingFieldsWithIp;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.expense.type.RecurringFrequency;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "recurring_transaction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecurringTransaction extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_row_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_row_id")
    private ExpenseCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_row_id")
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(name = "expense_type", nullable = false, length = 20)
    private ExpenseType expenseType;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "merchant", length = 100)
    private String merchant;

    @Column(name = "payment_method", length = 30)
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 20)
    private RecurringFrequency frequency;

    @Column(name = "interval_value", nullable = false)
    private Integer intervalValue;

    @Column(name = "day_of_week")
    private Integer dayOfWeek;

    @Column(name = "day_of_month")
    private Integer dayOfMonth;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "next_execution_date", nullable = false)
    private LocalDate nextExecutionDate;

    @Column(name = "last_executed_at")
    private LocalDateTime lastExecutedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_active", nullable = false, length = 1)
    private YNType isActive;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static RecurringTransaction createRecurring(User user, ExpenseCategory category, Asset asset,
                                                        ExpenseType expenseType, Long amount, String description,
                                                        String merchant, String paymentMethod,
                                                        RecurringFrequency frequency, Integer intervalValue,
                                                        Integer dayOfWeek, Integer dayOfMonth,
                                                        LocalDate startDate, LocalDate endDate,
                                                        LocalDate nextExecutionDate) {
        RecurringTransaction recurring = new RecurringTransaction();
        recurring.user = user;
        recurring.category = category;
        recurring.asset = asset;
        recurring.expenseType = expenseType;
        recurring.amount = amount;
        recurring.description = description;
        recurring.merchant = merchant;
        recurring.paymentMethod = paymentMethod;
        recurring.frequency = frequency;
        recurring.intervalValue = intervalValue != null ? intervalValue : 1;
        recurring.dayOfWeek = dayOfWeek;
        recurring.dayOfMonth = dayOfMonth;
        recurring.startDate = startDate;
        recurring.endDate = endDate;
        recurring.nextExecutionDate = nextExecutionDate;
        recurring.isActive = YNType.Y;
        recurring.isDeleted = YNType.N;
        return recurring;
    }

    public void updateRecurring(ExpenseCategory category, Asset asset, ExpenseType expenseType,
                                 Long amount, String description, String merchant, String paymentMethod,
                                 RecurringFrequency frequency, Integer intervalValue,
                                 Integer dayOfWeek, Integer dayOfMonth,
                                 LocalDate startDate, LocalDate endDate, LocalDate nextExecutionDate) {
        this.category = category;
        this.asset = asset;
        this.expenseType = expenseType;
        this.amount = amount;
        this.description = description;
        this.merchant = merchant;
        this.paymentMethod = paymentMethod;
        this.frequency = frequency;
        this.intervalValue = intervalValue;
        this.dayOfWeek = dayOfWeek;
        this.dayOfMonth = dayOfMonth;
        this.startDate = startDate;
        this.endDate = endDate;
        this.nextExecutionDate = nextExecutionDate;
    }

    public void markExecuted(LocalDateTime executedAt, LocalDate nextDate) {
        this.lastExecutedAt = executedAt;
        this.nextExecutionDate = nextDate;
    }

    public void toggleActive() {
        this.isActive = this.isActive == YNType.Y ? YNType.N : YNType.Y;
    }

    public void deleteRecurring() {
        this.isDeleted = YNType.Y;
    }
}
