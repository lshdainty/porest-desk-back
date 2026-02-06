package com.porest.desk.expense.domain;

import com.porest.desk.common.domain.AuditingFieldsWithIp;
import com.porest.desk.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "expense_budget")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExpenseBudget extends AuditingFieldsWithIp {
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

    @Column(name = "budget_amount", nullable = false)
    private Long budgetAmount;

    @Column(name = "budget_year", nullable = false)
    private Integer budgetYear;

    @Column(name = "budget_month", nullable = false)
    private Integer budgetMonth;

    public static ExpenseBudget createBudget(User user, ExpenseCategory category, Long budgetAmount,
                                             Integer budgetYear, Integer budgetMonth) {
        ExpenseBudget budget = new ExpenseBudget();
        budget.user = user;
        budget.category = category;
        budget.budgetAmount = budgetAmount;
        budget.budgetYear = budgetYear;
        budget.budgetMonth = budgetMonth;
        return budget;
    }

    public void updateBudget(Long budgetAmount) {
        this.budgetAmount = budgetAmount;
    }
}
