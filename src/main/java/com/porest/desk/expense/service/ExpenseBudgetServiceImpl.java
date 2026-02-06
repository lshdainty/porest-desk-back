package com.porest.desk.expense.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.expense.domain.ExpenseBudget;
import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.desk.expense.repository.ExpenseBudgetRepository;
import com.porest.desk.expense.repository.ExpenseCategoryRepository;
import com.porest.desk.expense.service.dto.ExpenseBudgetServiceDto;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ExpenseBudgetServiceImpl implements ExpenseBudgetService {
    private final ExpenseBudgetRepository expenseBudgetRepository;
    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public ExpenseBudgetServiceDto.BudgetInfo createBudget(ExpenseBudgetServiceDto.CreateCommand command) {
        log.debug("예산 등록 시작: userRowId={}, year={}, month={}", command.userRowId(), command.budgetYear(), command.budgetMonth());

        User user = userRepository.findById(command.userRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

        ExpenseCategory category = null;
        if (command.categoryRowId() != null) {
            category = expenseCategoryRepository.findById(command.categoryRowId())
                .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.EXPENSE_CATEGORY_NOT_FOUND));
        }

        ExpenseBudget budget = ExpenseBudget.createBudget(
            user,
            category,
            command.budgetAmount(),
            command.budgetYear(),
            command.budgetMonth()
        );

        expenseBudgetRepository.save(budget);
        log.info("예산 등록 완료: budgetId={}, userRowId={}", budget.getRowId(), command.userRowId());

        return ExpenseBudgetServiceDto.BudgetInfo.from(budget);
    }

    @Override
    public List<ExpenseBudgetServiceDto.BudgetInfo> getBudgets(Long userRowId, Integer year, Integer month) {
        log.debug("예산 목록 조회: userRowId={}, year={}, month={}", userRowId, year, month);

        List<ExpenseBudget> budgets = expenseBudgetRepository.findByUser(userRowId, year, month);

        return budgets.stream()
            .map(ExpenseBudgetServiceDto.BudgetInfo::from)
            .toList();
    }

    @Override
    @Transactional
    public void deleteBudget(Long budgetId) {
        log.debug("예산 삭제 시작: budgetId={}", budgetId);

        ExpenseBudget budget = findBudgetOrThrow(budgetId);
        expenseBudgetRepository.delete(budget);

        log.info("예산 삭제 완료: budgetId={}", budgetId);
    }

    private ExpenseBudget findBudgetOrThrow(Long budgetId) {
        return expenseBudgetRepository.findById(budgetId)
            .orElseThrow(() -> {
                log.warn("예산 조회 실패 - 존재하지 않는 예산: budgetId={}", budgetId);
                return new EntityNotFoundException(DeskErrorCode.EXPENSE_BUDGET_NOT_FOUND);
            });
    }
}
