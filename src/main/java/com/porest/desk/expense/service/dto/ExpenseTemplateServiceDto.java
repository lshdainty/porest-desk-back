package com.porest.desk.expense.service.dto;

import com.porest.desk.expense.domain.ExpenseTemplate;
import com.porest.desk.expense.type.ExpenseType;

import java.time.LocalDateTime;

public class ExpenseTemplateServiceDto {

    public record CreateCommand(
        Long userRowId,
        String templateName,
        Long categoryRowId,
        Long assetRowId,
        ExpenseType expenseType,
        Long amount,
        String description,
        String merchant,
        String paymentMethod,
        Integer sortOrder
    ) {}

    public record UpdateCommand(
        String templateName,
        Long categoryRowId,
        Long assetRowId,
        ExpenseType expenseType,
        Long amount,
        String description,
        String merchant,
        String paymentMethod
    ) {}

    public record TemplateInfo(
        Long rowId,
        Long userRowId,
        String templateName,
        Long categoryRowId,
        String categoryName,
        Long assetRowId,
        String assetName,
        ExpenseType expenseType,
        Long amount,
        String description,
        String merchant,
        String paymentMethod,
        Integer useCount,
        Integer sortOrder,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static TemplateInfo from(ExpenseTemplate template) {
            return new TemplateInfo(
                template.getRowId(),
                template.getUser().getRowId(),
                template.getTemplateName(),
                template.getCategory() != null ? template.getCategory().getRowId() : null,
                template.getCategory() != null ? template.getCategory().getCategoryName() : null,
                template.getAsset() != null ? template.getAsset().getRowId() : null,
                template.getAsset() != null ? template.getAsset().getAssetName() : null,
                template.getExpenseType(),
                template.getAmount(),
                template.getDescription(),
                template.getMerchant(),
                template.getPaymentMethod(),
                template.getUseCount(),
                template.getSortOrder(),
                template.getCreateAt(),
                template.getModifyAt()
            );
        }
    }
}
