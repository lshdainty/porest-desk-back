package com.porest.desk.expense.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.desk.expense.repository.ExpenseCategoryRepository;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.service.dto.ExpenseServiceDto;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ExpenseServiceImpl implements ExpenseService {
    private final ExpenseRepository expenseRepository;
    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public ExpenseServiceDto.ExpenseInfo createExpense(ExpenseServiceDto.CreateCommand command) {
        log.debug("지출 등록 시작: userRowId={}, amount={}", command.userRowId(), command.amount());

        User user = userRepository.findById(command.userRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

        ExpenseCategory category = expenseCategoryRepository.findById(command.categoryRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.EXPENSE_CATEGORY_NOT_FOUND));

        Expense expense = Expense.createExpense(
            user,
            category,
            command.expenseType(),
            command.amount(),
            command.description(),
            command.expenseDate(),
            command.paymentMethod()
        );

        expenseRepository.save(expense);
        log.info("지출 등록 완료: expenseId={}, userRowId={}", expense.getRowId(), command.userRowId());

        return ExpenseServiceDto.ExpenseInfo.from(expense);
    }

    @Override
    public List<ExpenseServiceDto.ExpenseInfo> getExpenses(Long userRowId, Long categoryRowId, ExpenseType expenseType, LocalDate startDate, LocalDate endDate) {
        log.debug("지출 목록 조회: userRowId={}, expenseType={}", userRowId, expenseType);

        List<Expense> expenses = expenseRepository.findByUser(userRowId, categoryRowId, expenseType, startDate, endDate);

        return expenses.stream()
            .map(ExpenseServiceDto.ExpenseInfo::from)
            .toList();
    }

    @Override
    @Transactional
    public ExpenseServiceDto.ExpenseInfo updateExpense(Long expenseId, ExpenseServiceDto.UpdateCommand command) {
        log.debug("지출 수정 시작: expenseId={}", expenseId);

        Expense expense = findExpenseOrThrow(expenseId);

        ExpenseCategory category = expenseCategoryRepository.findById(command.categoryRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.EXPENSE_CATEGORY_NOT_FOUND));

        expense.updateExpense(
            category,
            command.expenseType(),
            command.amount(),
            command.description(),
            command.expenseDate(),
            command.paymentMethod()
        );

        log.info("지출 수정 완료: expenseId={}", expenseId);

        return ExpenseServiceDto.ExpenseInfo.from(expense);
    }

    @Override
    @Transactional
    public void deleteExpense(Long expenseId) {
        log.debug("지출 삭제 시작: expenseId={}", expenseId);

        Expense expense = findExpenseOrThrow(expenseId);
        expense.deleteExpense();

        log.info("지출 삭제 완료: expenseId={}", expenseId);
    }

    @Override
    public ExpenseServiceDto.DailySummary getDailySummary(Long userRowId, LocalDate date) {
        log.debug("지출 일별 요약 조회: userRowId={}, date={}", userRowId, date);

        List<Expense> expenses = expenseRepository.findDailySummary(userRowId, date);

        Long totalIncome = expenses.stream()
            .filter(e -> e.getExpenseType() == ExpenseType.INCOME)
            .mapToLong(Expense::getAmount)
            .sum();

        Long totalExpense = expenses.stream()
            .filter(e -> e.getExpenseType() == ExpenseType.EXPENSE)
            .mapToLong(Expense::getAmount)
            .sum();

        return new ExpenseServiceDto.DailySummary(date, totalIncome, totalExpense);
    }

    @Override
    public ExpenseServiceDto.MonthlySummary getMonthlySummary(Long userRowId, Integer year, Integer month) {
        log.debug("지출 월별 요약 조회: userRowId={}, year={}, month={}", userRowId, year, month);

        List<Expense> expenses = expenseRepository.findMonthlySummary(userRowId, year, month);

        Long totalIncome = expenses.stream()
            .filter(e -> e.getExpenseType() == ExpenseType.INCOME)
            .mapToLong(Expense::getAmount)
            .sum();

        Long totalExpense = expenses.stream()
            .filter(e -> e.getExpenseType() == ExpenseType.EXPENSE)
            .mapToLong(Expense::getAmount)
            .sum();

        List<ExpenseServiceDto.CategoryBreakdown> categoryBreakdown = expenses.stream()
            .collect(Collectors.groupingBy(
                e -> e.getCategory().getRowId(),
                Collectors.toList()
            ))
            .entrySet().stream()
            .map(entry -> {
                List<Expense> categoryExpenses = entry.getValue();
                Expense first = categoryExpenses.get(0);
                Long totalAmount = categoryExpenses.stream()
                    .mapToLong(Expense::getAmount)
                    .sum();
                return new ExpenseServiceDto.CategoryBreakdown(
                    first.getCategory().getRowId(),
                    first.getCategory().getCategoryName(),
                    first.getExpenseType(),
                    totalAmount
                );
            })
            .toList();

        return new ExpenseServiceDto.MonthlySummary(year, month, totalIncome, totalExpense, categoryBreakdown);
    }

    private Expense findExpenseOrThrow(Long expenseId) {
        return expenseRepository.findById(expenseId)
            .orElseThrow(() -> {
                log.warn("지출 조회 실패 - 존재하지 않는 지출: expenseId={}", expenseId);
                return new EntityNotFoundException(DeskErrorCode.EXPENSE_NOT_FOUND);
            });
    }
}
