package com.porest.desk.expense.domain;

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

@Entity
@Table(name = "expense_split")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExpenseSplit extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_row_id")
    private Expense expense;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_row_id")
    private ExpenseCategory category;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "label", length = 200)
    private String label;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static ExpenseSplit create(Expense expense, ExpenseCategory category,
                                       Long amount, String label, Integer sortOrder) {
        ExpenseSplit split = new ExpenseSplit();
        split.expense = expense;
        split.category = category;
        split.amount = amount;
        split.label = label;
        split.sortOrder = sortOrder != null ? sortOrder : 0;
        split.isDeleted = YNType.N;
        return split;
    }

    public void update(ExpenseCategory category, Long amount, String label, Integer sortOrder) {
        this.category = category;
        this.amount = amount;
        this.label = label;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
    }

    public void deleteSplit() {
        this.isDeleted = YNType.Y;
    }
}
