package com.porest.desk.expense.domain;

import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
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
@Table(name = "expense_template")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExpenseTemplate extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_row_id")
    private User user;

    @Column(name = "template_name", nullable = false, length = 100)
    private String templateName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_row_id")
    private ExpenseCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_row_id")
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(name = "expense_type", nullable = false, length = 20)
    private ExpenseType expenseType;

    @Column(name = "amount")
    private Long amount;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "merchant", length = 100)
    private String merchant;

    @Column(name = "payment_method", length = 30)
    private String paymentMethod;

    @Column(name = "use_count", nullable = false)
    private Integer useCount;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static ExpenseTemplate createTemplate(User user, String templateName, ExpenseCategory category,
                                                  Asset asset, ExpenseType expenseType, Long amount,
                                                  String description, String merchant, String paymentMethod,
                                                  Integer sortOrder) {
        ExpenseTemplate template = new ExpenseTemplate();
        template.user = user;
        template.templateName = templateName;
        template.category = category;
        template.asset = asset;
        template.expenseType = expenseType;
        template.amount = amount;
        template.description = description;
        template.merchant = merchant;
        template.paymentMethod = paymentMethod;
        template.useCount = 0;
        template.sortOrder = sortOrder != null ? sortOrder : 0;
        template.isDeleted = YNType.N;
        return template;
    }

    public void updateTemplate(String templateName, ExpenseCategory category, Asset asset,
                                ExpenseType expenseType, Long amount, String description,
                                String merchant, String paymentMethod) {
        this.templateName = templateName;
        this.category = category;
        this.asset = asset;
        this.expenseType = expenseType;
        this.amount = amount;
        this.description = description;
        this.merchant = merchant;
        this.paymentMethod = paymentMethod;
    }

    public void incrementUseCount() {
        this.useCount++;
    }

    public void deleteTemplate() {
        this.isDeleted = YNType.Y;
    }
}
