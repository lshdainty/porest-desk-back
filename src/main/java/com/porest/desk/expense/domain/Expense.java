package com.porest.desk.expense.domain;

import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.calendar.domain.CalendarEvent;
import com.porest.desk.common.domain.AuditingFieldsWithIp;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.todo.domain.Todo;
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

@Entity
@Table(name = "expense")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Expense extends AuditingFieldsWithIp {
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

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(name = "merchant", length = 100)
    private String merchant;

    @Column(name = "payment_method", length = 30)
    private String paymentMethod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calendar_event_row_id")
    private CalendarEvent calendarEvent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "todo_row_id")
    private Todo todo;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static Expense createExpense(User user, ExpenseCategory category, Asset asset,
                                        ExpenseType expenseType, Long amount, String description,
                                        LocalDate expenseDate, String merchant, String paymentMethod) {
        Expense expense = new Expense();
        expense.user = user;
        expense.category = category;
        expense.asset = asset;
        expense.expenseType = expenseType;
        expense.amount = amount;
        expense.description = description;
        expense.expenseDate = expenseDate;
        expense.merchant = merchant;
        expense.paymentMethod = paymentMethod;
        expense.isDeleted = YNType.N;
        return expense;
    }

    public void updateExpense(ExpenseCategory category, Asset asset, ExpenseType expenseType,
                              Long amount, String description, LocalDate expenseDate,
                              String merchant, String paymentMethod) {
        this.category = category;
        this.asset = asset;
        this.expenseType = expenseType;
        this.amount = amount;
        this.description = description;
        this.expenseDate = expenseDate;
        this.merchant = merchant;
        this.paymentMethod = paymentMethod;
    }

    public void setCalendarEvent(CalendarEvent calendarEvent) {
        this.calendarEvent = calendarEvent;
    }

    public void setTodo(Todo todo) {
        this.todo = todo;
    }

    public void deleteExpense() {
        this.isDeleted = YNType.Y;
    }
}
