package com.porest.desk.expense.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.desk.expense.domain.ExpenseTemplate;
import com.porest.desk.expense.repository.ExpenseCategoryRepository;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.repository.ExpenseTemplateRepository;
import com.porest.desk.expense.service.dto.ExpenseServiceDto;
import com.porest.desk.expense.service.dto.ExpenseTemplateServiceDto;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ExpenseTemplateServiceImpl implements ExpenseTemplateService {
    private final ExpenseTemplateRepository expenseTemplateRepository;
    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final AssetRepository assetRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public ExpenseTemplateServiceDto.TemplateInfo createTemplate(ExpenseTemplateServiceDto.CreateCommand command) {
        log.debug("경비 템플릿 생성 시작: userRowId={}, templateName={}", command.userRowId(), command.templateName());

        User user = userRepository.findById(command.userRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

        ExpenseCategory category = null;
        if (command.categoryRowId() != null) {
            category = expenseCategoryRepository.findById(command.categoryRowId())
                .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.EXPENSE_CATEGORY_NOT_FOUND));
        }

        Asset asset = null;
        if (command.assetRowId() != null) {
            asset = assetRepository.findById(command.assetRowId())
                .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.ASSET_NOT_FOUND));
        }

        ExpenseTemplate template = ExpenseTemplate.createTemplate(
            user, command.templateName(), category, asset,
            command.expenseType(), command.amount(), command.description(),
            command.merchant(), command.paymentMethod(), command.sortOrder()
        );

        expenseTemplateRepository.save(template);
        log.info("경비 템플릿 생성 완료: templateId={}", template.getRowId());

        return ExpenseTemplateServiceDto.TemplateInfo.from(template);
    }

    @Override
    public List<ExpenseTemplateServiceDto.TemplateInfo> getTemplates(Long userRowId) {
        log.debug("경비 템플릿 목록 조회: userRowId={}", userRowId);

        return expenseTemplateRepository.findByUser(userRowId).stream()
            .map(ExpenseTemplateServiceDto.TemplateInfo::from)
            .toList();
    }

    @Override
    @Transactional
    public ExpenseTemplateServiceDto.TemplateInfo updateTemplate(Long templateId, ExpenseTemplateServiceDto.UpdateCommand command) {
        log.debug("경비 템플릿 수정 시작: templateId={}", templateId);

        ExpenseTemplate template = findTemplateOrThrow(templateId);

        ExpenseCategory category = null;
        if (command.categoryRowId() != null) {
            category = expenseCategoryRepository.findById(command.categoryRowId())
                .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.EXPENSE_CATEGORY_NOT_FOUND));
        }

        Asset asset = null;
        if (command.assetRowId() != null) {
            asset = assetRepository.findById(command.assetRowId())
                .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.ASSET_NOT_FOUND));
        }

        template.updateTemplate(
            command.templateName(), category, asset,
            command.expenseType(), command.amount(), command.description(),
            command.merchant(), command.paymentMethod()
        );

        log.info("경비 템플릿 수정 완료: templateId={}", templateId);

        return ExpenseTemplateServiceDto.TemplateInfo.from(template);
    }

    @Override
    @Transactional
    public void deleteTemplate(Long templateId) {
        log.debug("경비 템플릿 삭제 시작: templateId={}", templateId);

        ExpenseTemplate template = findTemplateOrThrow(templateId);
        template.deleteTemplate();

        log.info("경비 템플릿 삭제 완료: templateId={}", templateId);
    }

    @Override
    @Transactional
    public ExpenseServiceDto.ExpenseInfo useTemplate(Long templateId, LocalDate expenseDate) {
        log.debug("경비 템플릿 사용: templateId={}, expenseDate={}", templateId, expenseDate);

        ExpenseTemplate template = findTemplateOrThrow(templateId);

        Expense expense = Expense.createExpense(
            template.getUser(),
            template.getCategory(),
            template.getAsset(),
            template.getExpenseType(),
            template.getAmount(),
            template.getDescription(),
            expenseDate,
            template.getMerchant(),
            template.getPaymentMethod()
        );

        expenseRepository.save(expense);
        template.incrementUseCount();

        log.info("경비 템플릿 사용 완료: templateId={}, expenseId={}", templateId, expense.getRowId());

        return ExpenseServiceDto.ExpenseInfo.from(expense);
    }

    private ExpenseTemplate findTemplateOrThrow(Long templateId) {
        return expenseTemplateRepository.findById(templateId)
            .orElseThrow(() -> {
                log.warn("경비 템플릿 조회 실패 - 존재하지 않는 템플릿: templateId={}", templateId);
                return new EntityNotFoundException(DeskErrorCode.EXPENSE_TEMPLATE_NOT_FOUND);
            });
    }
}
