package com.porest.desk.expense.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.desk.expense.domain.ExpenseSplit;
import com.porest.desk.expense.repository.ExpenseCategoryRepository;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.repository.ExpenseSplitRepository;
import com.porest.desk.expense.service.dto.ExpenseSplitServiceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ExpenseSplitServiceImpl implements ExpenseSplitService {
    private final ExpenseSplitRepository expenseSplitRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseCategoryRepository expenseCategoryRepository;

    @Override
    public List<ExpenseSplitServiceDto.SplitInfo> getSplits(Long expenseRowId, Long userRowId) {
        Expense expense = findExpenseOrThrow(expenseRowId);
        validateExpenseOwnership(expense, userRowId);

        return expenseSplitRepository.findByExpense(expenseRowId).stream()
            .map(ExpenseSplitServiceDto.SplitInfo::from)
            .toList();
    }

    @Override
    @Transactional
    public List<ExpenseSplitServiceDto.SplitInfo> replaceSplits(ExpenseSplitServiceDto.ReplaceCommand command) {
        log.debug("분할 항목 교체 시작: expenseRowId={}", command.expenseRowId());

        Expense expense = findExpenseOrThrow(command.expenseRowId());
        validateExpenseOwnership(expense, command.userRowId());

        List<ExpenseSplitServiceDto.SplitCommand> incoming = command.splits() != null ? command.splits() : List.of();
        validateAmountSum(expense, incoming);

        expenseSplitRepository.deleteByExpense(command.expenseRowId());

        for (int i = 0; i < incoming.size(); i++) {
            ExpenseSplitServiceDto.SplitCommand sc = incoming.get(i);
            ExpenseCategory category = expenseCategoryRepository.findById(sc.categoryRowId())
                .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.EXPENSE_CATEGORY_NOT_FOUND));
            int order = sc.sortOrder() != null ? sc.sortOrder() : i;
            ExpenseSplit split = ExpenseSplit.create(expense, category, sc.amount(), sc.label(), order);
            expenseSplitRepository.save(split);
        }

        log.info("분할 항목 교체 완료: expenseRowId={}, count={}", command.expenseRowId(), incoming.size());

        return expenseSplitRepository.findByExpense(command.expenseRowId()).stream()
            .map(ExpenseSplitServiceDto.SplitInfo::from)
            .toList();
    }

    @Override
    @Transactional
    public void deleteAllSplits(Long expenseRowId, Long userRowId) {
        Expense expense = findExpenseOrThrow(expenseRowId);
        validateExpenseOwnership(expense, userRowId);

        expenseSplitRepository.deleteByExpense(expenseRowId);
        log.info("분할 항목 전체 삭제 완료: expenseRowId={}", expenseRowId);
    }

    private void validateAmountSum(Expense expense, List<ExpenseSplitServiceDto.SplitCommand> splits) {
        if (splits.isEmpty()) return;
        long sum = splits.stream().mapToLong(ExpenseSplitServiceDto.SplitCommand::amount).sum();
        long expected = Math.abs(expense.getAmount());
        if (sum != expected) {
            log.warn("분할 합계 불일치 - expenseRowId={}, expected={}, actual={}",
                expense.getRowId(), expected, sum);
            throw new InvalidValueException(DeskErrorCode.EXPENSE_SPLIT_AMOUNT_MISMATCH);
        }
    }

    private Expense findExpenseOrThrow(Long expenseRowId) {
        return expenseRepository.findById(expenseRowId)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.EXPENSE_NOT_FOUND));
    }

    private void validateExpenseOwnership(Expense expense, Long userRowId) {
        if (!expense.getUser().getRowId().equals(userRowId)) {
            log.warn("거래 소유권 검증 실패 - expenseRowId={}, ownerRowId={}, requestUserRowId={}",
                expense.getRowId(), expense.getUser().getRowId(), userRowId);
            throw new ForbiddenException(DeskErrorCode.EXPENSE_ACCESS_DENIED);
        }
    }
}
