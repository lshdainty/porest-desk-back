package io.groo.damda.card.domain;

import io.groo.core.type.YNType;
import io.groo.damda.common.domain.AuditingFieldsWithIp;
import io.groo.damda.expense.domain.ExpenseCategory;
import io.groo.damda.user.domain.User;
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
@Table(name = "card_benefit_category_mapping")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardBenefitCategoryMapping extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_row_id")
    private User user;

    @Column(name = "benefit_category", nullable = false, length = 50)
    private String benefitCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_category_row_id", nullable = false)
    private ExpenseCategory expenseCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static CardBenefitCategoryMapping createUserMapping(User user, String benefitCategory, ExpenseCategory expenseCategory) {
        CardBenefitCategoryMapping mapping = new CardBenefitCategoryMapping();
        mapping.user = user;
        mapping.benefitCategory = benefitCategory;
        mapping.expenseCategory = expenseCategory;
        mapping.isDeleted = YNType.N;
        return mapping;
    }

    public void updateExpenseCategory(ExpenseCategory expenseCategory) {
        this.expenseCategory = expenseCategory;
    }

    public void deleteMapping() {
        this.isDeleted = YNType.Y;
    }
}
