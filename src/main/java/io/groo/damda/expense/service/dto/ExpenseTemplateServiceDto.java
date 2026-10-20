package io.groo.damda.expense.service.dto;

import io.groo.core.type.YNType;
import io.groo.damda.common.patch.Patch;
import io.groo.damda.expense.domain.ExpenseTemplate;
import io.groo.damda.expense.type.TxKind;

import java.time.LocalDateTime;

public class ExpenseTemplateServiceDto {

    public record CreateCommand(
        Long userRowId,
        String templateName,
        Long categoryRowId,
        Long assetRowId,
        Long toAssetRowId,
        Long fee,
        Long interestAmount,
        TxKind expenseType,
        Long amount,
        String description,
        String merchant,
        String paymentMethod,
        Integer sortOrder,
        YNType lockAmount,
        /** 고정 수입·지출 기본값 (null = 변동). 이체는 무시된다. */
        Boolean isFixed
    ) {
        /** 고정 칸을 모르는 호출자 — 변동으로 만든다. */
        public CreateCommand(Long userRowId, String templateName, Long categoryRowId, Long assetRowId,
                             Long toAssetRowId, Long fee, Long interestAmount, TxKind expenseType, Long amount,
                             String description, String merchant, String paymentMethod, Integer sortOrder,
                             YNType lockAmount) {
            this(userRowId, templateName, categoryRowId, assetRowId, toAssetRowId, fee, interestAmount,
                expenseType, amount, description, merchant, paymentMethod, sortOrder, lockAmount, null);
        }
    }

    /** 수정 명령 — 각 칸은 "안 왔다 / 지워라 / 이 값으로" 셋 중 하나다({@link Patch}). */
    public record UpdateCommand(
        Patch<String> templateName,
        Patch<Long> categoryRowId,
        Patch<Long> assetRowId,
        Patch<Long> toAssetRowId,
        Patch<Long> fee,
        Patch<Long> interestAmount,
        Patch<TxKind> expenseType,
        Patch<Long> amount,
        Patch<String> description,
        Patch<String> merchant,
        Patch<String> paymentMethod,
        Patch<YNType> lockAmount,
        /** 고정 수입·지출 기본값 — 안 보내면 유지. */
        Patch<Boolean> isFixed
    ) {
        /** 고정 칸을 모르는 호출자 — 표식을 건드리지 않는다. */
        public UpdateCommand(Patch<String> templateName, Patch<Long> categoryRowId, Patch<Long> assetRowId,
                             Patch<Long> toAssetRowId, Patch<Long> fee, Patch<Long> interestAmount,
                             Patch<TxKind> expenseType, Patch<Long> amount, Patch<String> description,
                             Patch<String> merchant, Patch<String> paymentMethod, Patch<YNType> lockAmount) {
            this(templateName, categoryRowId, assetRowId, toAssetRowId, fee, interestAmount, expenseType, amount,
                description, merchant, paymentMethod, lockAmount, Patch.absent());
        }
    }

    public record TemplateInfo(
        Long rowId,
        Long userRowId,
        String templateName,
        Long categoryRowId,
        String categoryName,
        Long assetRowId,
        String assetName,
        Long toAssetRowId,
        String toAssetName,
        Long fee,
        Long interestAmount,
        TxKind expenseType,
        Long amount,
        String description,
        String merchant,
        String paymentMethod,
        Integer useCount,
        Integer sortOrder,
        YNType lockAmount,
        LocalDateTime lastUsedAt,
        LocalDateTime createAt,
        LocalDateTime modifyAt,
        /** 고정 수입·지출 기본값. */
        boolean isFixed
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
                template.getToAsset() != null ? template.getToAsset().getRowId() : null,
                template.getToAsset() != null ? template.getToAsset().getAssetName() : null,
                template.getFee(),
                template.getInterestAmount(),
                template.getExpenseType(),
                template.getAmount(),
                template.getDescription(),
                template.getMerchant(),
                template.getPaymentMethod(),
                template.getUseCount(),
                template.getSortOrder(),
                template.getLockAmount(),
                template.getLastUsedAt(),
                template.getCreateAt(),
                template.getModifyAt(),
                template.isFixedTx()
            );
        }
    }
}
