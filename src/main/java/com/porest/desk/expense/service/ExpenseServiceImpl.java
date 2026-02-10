package com.porest.desk.expense.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.calendar.domain.CalendarEvent;
import com.porest.desk.calendar.repository.CalendarEventRepository;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.desk.expense.repository.ExpenseCategoryRepository;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.service.dto.ExpenseServiceDto;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.todo.domain.Todo;
import com.porest.desk.todo.repository.TodoRepository;
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
    private final AssetRepository assetRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final TodoRepository todoRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public ExpenseServiceDto.ExpenseInfo createExpense(ExpenseServiceDto.CreateCommand command) {
        log.debug("지출 등록 시작: userRowId={}, amount={}", command.userRowId(), command.amount());

        User user = userRepository.findById(command.userRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

        ExpenseCategory category = expenseCategoryRepository.findById(command.categoryRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.EXPENSE_CATEGORY_NOT_FOUND));

        Asset asset = null;
        if (command.assetRowId() != null) {
            asset = assetRepository.findById(command.assetRowId())
                .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.ASSET_NOT_FOUND));
        }

        Expense expense = Expense.createExpense(
            user, category, asset,
            command.expenseType(),
            command.amount(),
            command.description(),
            command.expenseDate(),
            command.merchant(),
            command.paymentMethod()
        );

        if (command.calendarEventRowId() != null) {
            CalendarEvent event = calendarEventRepository.findById(command.calendarEventRowId())
                .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.CALENDAR_EVENT_NOT_FOUND));
            expense.setCalendarEvent(event);
        }

        if (command.todoRowId() != null) {
            Todo todo = todoRepository.findById(command.todoRowId())
                .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.TODO_NOT_FOUND));
            expense.setTodo(todo);
        }

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

        Asset asset = null;
        if (command.assetRowId() != null) {
            asset = assetRepository.findById(command.assetRowId())
                .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.ASSET_NOT_FOUND));
        }

        expense.updateExpense(
            category, asset,
            command.expenseType(),
            command.amount(),
            command.description(),
            command.expenseDate(),
            command.merchant(),
            command.paymentMethod()
        );

        if (command.calendarEventRowId() != null) {
            CalendarEvent event = calendarEventRepository.findById(command.calendarEventRowId())
                .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.CALENDAR_EVENT_NOT_FOUND));
            expense.setCalendarEvent(event);
        } else {
            expense.setCalendarEvent(null);
        }

        if (command.todoRowId() != null) {
            Todo todo = todoRepository.findById(command.todoRowId())
                .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.TODO_NOT_FOUND));
            expense.setTodo(todo);
        } else {
            expense.setTodo(null);
        }

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

    @Override
    public ExpenseServiceDto.WeeklySummary getWeeklySummary(Long userRowId, LocalDate weekStart, LocalDate weekEnd) {
        log.debug("지출 주간 요약 조회: userRowId={}, weekStart={}, weekEnd={}", userRowId, weekStart, weekEnd);

        List<Expense> expenses = expenseRepository.findWeeklySummary(userRowId, weekStart, weekEnd);

        Long totalIncome = expenses.stream()
            .filter(e -> e.getExpenseType() == ExpenseType.INCOME)
            .mapToLong(Expense::getAmount)
            .sum();

        Long totalExpense = expenses.stream()
            .filter(e -> e.getExpenseType() == ExpenseType.EXPENSE)
            .mapToLong(Expense::getAmount)
            .sum();

        return new ExpenseServiceDto.WeeklySummary(weekStart, weekEnd, totalIncome, totalExpense);
    }

    @Override
    public ExpenseServiceDto.YearlySummary getYearlySummary(Long userRowId, Integer year) {
        log.debug("지출 연간 요약 조회: userRowId={}, year={}", userRowId, year);

        List<Expense> expenses = expenseRepository.findYearlySummary(userRowId, year);

        Long totalIncome = expenses.stream()
            .filter(e -> e.getExpenseType() == ExpenseType.INCOME)
            .mapToLong(Expense::getAmount)
            .sum();

        Long totalExpense = expenses.stream()
            .filter(e -> e.getExpenseType() == ExpenseType.EXPENSE)
            .mapToLong(Expense::getAmount)
            .sum();

        Map<Integer, List<Expense>> byMonth = expenses.stream()
            .collect(Collectors.groupingBy(e -> e.getExpenseDate().getMonthValue()));

        List<ExpenseServiceDto.MonthlyAmount> monthlyAmounts = byMonth.entrySet().stream()
            .map(entry -> new ExpenseServiceDto.MonthlyAmount(
                entry.getKey(),
                entry.getValue().stream().filter(e -> e.getExpenseType() == ExpenseType.INCOME).mapToLong(Expense::getAmount).sum(),
                entry.getValue().stream().filter(e -> e.getExpenseType() == ExpenseType.EXPENSE).mapToLong(Expense::getAmount).sum()
            ))
            .sorted((a, b) -> a.month().compareTo(b.month()))
            .toList();

        return new ExpenseServiceDto.YearlySummary(year, totalIncome, totalExpense, monthlyAmounts);
    }

    @Override
    public List<ExpenseServiceDto.MerchantSummary> getMerchantSummary(Long userRowId, LocalDate startDate, LocalDate endDate) {
        log.debug("거래처별 요약 조회: userRowId={}", userRowId);

        List<Expense> expenses = expenseRepository.findByUser(userRowId, null, null, startDate, endDate);

        return expenses.stream()
            .filter(e -> e.getMerchant() != null && !e.getMerchant().isBlank())
            .collect(Collectors.groupingBy(Expense::getMerchant))
            .entrySet().stream()
            .map(entry -> new ExpenseServiceDto.MerchantSummary(
                entry.getKey(),
                entry.getValue().stream().mapToLong(Expense::getAmount).sum(),
                entry.getValue().size()
            ))
            .sorted((a, b) -> Long.compare(b.totalAmount(), a.totalAmount()))
            .toList();
    }

    @Override
    public List<ExpenseServiceDto.AssetSummary> getAssetSummary(Long userRowId, LocalDate startDate, LocalDate endDate) {
        log.debug("자산별 요약 조회: userRowId={}", userRowId);

        List<Expense> expenses = expenseRepository.findByUser(userRowId, null, null, startDate, endDate);

        return expenses.stream()
            .filter(e -> e.getAsset() != null)
            .collect(Collectors.groupingBy(e -> e.getAsset().getRowId()))
            .entrySet().stream()
            .map(entry -> {
                List<Expense> assetExpenses = entry.getValue();
                Expense first = assetExpenses.get(0);
                return new ExpenseServiceDto.AssetSummary(
                    first.getAsset().getRowId(),
                    first.getAsset().getAssetName(),
                    assetExpenses.stream().mapToLong(Expense::getAmount).sum(),
                    assetExpenses.size()
                );
            })
            .sorted((a, b) -> Long.compare(b.totalAmount(), a.totalAmount()))
            .toList();
    }

    @Override
    public List<ExpenseServiceDto.ExpenseInfo> searchExpenses(ExpenseServiceDto.SearchCommand command) {
        log.debug("지출 검색: userRowId={}, keyword={}", command.userRowId(), command.keyword());

        List<Expense> expenses = expenseRepository.search(
            command.userRowId(), command.categoryRowId(), command.assetRowId(),
            command.expenseType(), command.keyword(), command.merchant(),
            command.minAmount(), command.maxAmount(), command.startDate(), command.endDate()
        );

        return expenses.stream()
            .map(ExpenseServiceDto.ExpenseInfo::from)
            .toList();
    }

    @Override
    public List<ExpenseServiceDto.ExpenseInfo> getExpensesByCalendarEvent(Long calendarEventRowId) {
        log.debug("일정 연결 지출 조회: calendarEventRowId={}", calendarEventRowId);

        return expenseRepository.findByCalendarEvent(calendarEventRowId).stream()
            .map(ExpenseServiceDto.ExpenseInfo::from)
            .toList();
    }

    @Override
    public List<ExpenseServiceDto.ExpenseInfo> getExpensesByTodo(Long todoRowId) {
        log.debug("할일 연결 지출 조회: todoRowId={}", todoRowId);

        return expenseRepository.findByTodo(todoRowId).stream()
            .map(ExpenseServiceDto.ExpenseInfo::from)
            .toList();
    }

    private Expense findExpenseOrThrow(Long expenseId) {
        return expenseRepository.findById(expenseId)
            .orElseThrow(() -> {
                log.warn("지출 조회 실패 - 존재하지 않는 지출: expenseId={}", expenseId);
                return new EntityNotFoundException(DeskErrorCode.EXPENSE_NOT_FOUND);
            });
    }
}
