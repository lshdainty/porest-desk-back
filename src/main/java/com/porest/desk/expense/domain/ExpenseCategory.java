package com.porest.desk.expense.domain;

import com.porest.core.type.YNType;
import com.porest.desk.common.domain.AuditingFieldsWithIp;
import com.porest.desk.expense.type.ExpenseType;
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

@Entity
@Table(name = "expense_category")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExpenseCategory extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_row_id")
    private User user;

    @Column(name = "category_name", nullable = false, length = 50)
    private String categoryName;

    @Column(name = "icon", length = 50)
    private String icon;

    @Column(name = "color", length = 20)
    private String color;

    @Enumerated(EnumType.STRING)
    @Column(name = "expense_type", nullable = false, length = 20)
    private ExpenseType expenseType;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static ExpenseCategory createCategory(User user, String categoryName, String icon, String color, ExpenseType expenseType) {
        ExpenseCategory category = new ExpenseCategory();
        category.user = user;
        category.categoryName = categoryName;
        category.icon = icon;
        category.color = color;
        category.expenseType = expenseType;
        category.sortOrder = 0;
        category.isDeleted = YNType.N;
        return category;
    }

    public void updateCategory(String categoryName, String icon, String color, Integer sortOrder) {
        this.categoryName = categoryName;
        this.icon = icon;
        this.color = color;
        this.sortOrder = sortOrder;
    }

    public void deleteCategory() {
        this.isDeleted = YNType.Y;
    }
}
