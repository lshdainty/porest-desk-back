package com.porest.desk.expense.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.calendar.domain.CalendarEvent;
import com.porest.desk.calendar.repository.CalendarEventRepository;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.domain.ExpenseBudget;
import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.desk.expense.repository.ExpenseBudgetRepository;
import com.porest.desk.expense.repository.ExpenseCategoryRepository;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.service.dto.ExpenseServiceDto;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.notification.service.NotificationService;
import com.porest.desk.notification.service.dto.NotificationServiceDto;
import com.porest.desk.notification.type.NotificationType;
import com.porest.desk.notification.type.ReferenceType;
import com.porest.desk.group.domain.UserGroup;
import com.porest.desk.group.domain.UserGroupMember;
import com.porest.desk.group.repository.UserGroupRepository;
import com.porest.desk.group.service.GroupMembershipValidator;
import com.porest.desk.todo.domain.Todo;
import com.porest.desk.todo.repository.TodoRepository;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ExpenseServiceImpl implements ExpenseService {
    /** 예산 사용량 알림 임계값 (사용률) */
    private static final double BUDGET_WARN_THRESHOLD = 0.85;
    private static final double BUDGET_OVER_THRESHOLD = 1.0;

    private final ExpenseRepository expenseRepository;
    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final ExpenseBudgetRepository expenseBudgetRepository;
    private final NotificationService notificationService;
    private final AssetRepository assetRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final TodoRepository todoRepository;
    private final UserRepository userRepository;
    private final GroupMembershipValidator groupMembershipValidator;
    private final UserGroupRepository userGroupRepository;

    @Override
    @Transactional
    public ExpenseServiceDto.ExpenseInfo createExpense(ExpenseServiceDto.CreateCommand command) {
        log.debug("지출 등록 시작: userRowId={}, amount={}", command.userRowId(), command.amount());

        User user = userRepository.findById(command.userRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

        ExpenseCategory category = expenseCategoryRepository.findById(command.categoryRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.EXPENSE_CATEGORY_NOT_FOUND));
        validateCategoryOwnership(category, command.userRowId());

        if (expenseCategoryRepository.hasChildren(category.getRowId())) {
            throw new InvalidValueException(DeskErrorCode.EXPENSE_CATEGORY_NOT_LEAF);
        }

        Asset asset = null;
        if (command.assetRowId() != null) {
            asset = assetRepository.findById(command.assetRowId())
                .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.ASSET_NOT_FOUND));
            validateAssetOwnership(asset, command.userRowId());
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

        if (command.groupRowId() != null) {
            groupMembershipValidator.validateMembership(command.groupRowId(), command.userRowId());
            UserGroup group = userGroupRepository.findById(command.groupRowId())
                .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.GROUP_NOT_FOUND));
            expense.setGroup(group);
        }

        expenseRepository.save(expense);

        // 자산 잔액 동기화: 수입은 +, 지출은 -
        applyExpenseToAssetBalance(asset, command.expenseType(), command.amount());

        // 예산 임계 도달 시 알림
        notifyBudgetThresholdIfCrossed(expense);

        log.info("지출 등록 완료: expenseId={}, userRowId={}", expense.getRowId(), command.userRowId());

        return ExpenseServiceDto.ExpenseInfo.from(expense);
    }

    @Override
    public List<ExpenseServiceDto.ExpenseInfo> getExpenses(Long userRowId, Long categoryRowId, Long assetRowId, ExpenseType expenseType, LocalDate startDate, LocalDate endDate) {
        log.debug("지출 목록 조회: userRowId={}, assetRowId={}, expenseType={}", userRowId, assetRowId, expenseType);

        List<Expense> personalExpenses = expenseRepository.findByUser(userRowId, categoryRowId, expenseType, startDate, endDate);

        List<Long> groupIds = groupMembershipValidator.getUserGroupIds(userRowId);
        List<Expense> groupExpenses = expenseRepository.findByGroups(groupIds, categoryRowId, expenseType, startDate, endDate);

        java.util.Set<Long> personalIds = personalExpenses.stream()
            .map(Expense::getRowId)
            .collect(java.util.stream.Collectors.toSet());
        List<Expense> allExpenses = new java.util.ArrayList<>(personalExpenses);
        groupExpenses.stream()
            .filter(e -> !personalIds.contains(e.getRowId()))
            .forEach(allExpenses::add);

        // Asset 필터 (서비스 층) — repo 쿼리 2개 시그니처 확장 대신 여기서 후처리
        if (assetRowId != null) {
            allExpenses = allExpenses.stream()
                .filter(e -> e.getAsset() != null && assetRowId.equals(e.getAsset().getRowId()))
                .collect(java.util.stream.Collectors.toList());
        }

        allExpenses.sort(java.util.Comparator.comparing(Expense::getExpenseDate).reversed()
            .thenComparing(java.util.Comparator.comparing(Expense::getRowId).reversed()));

        return allExpenses.stream()
            .map(ExpenseServiceDto.ExpenseInfo::from)
            .toList();
    }

    @Override
    @Transactional
    public ExpenseServiceDto.ExpenseInfo updateExpense(Long expenseId, Long userRowId, ExpenseServiceDto.UpdateCommand command) {
        log.debug("지출 수정 시작: expenseId={}", expenseId);

        Expense expense = findExpenseOrThrow(expenseId);
        validateExpenseOwnership(expense, userRowId);

        ExpenseCategory category = expenseCategoryRepository.findById(command.categoryRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.EXPENSE_CATEGORY_NOT_FOUND));

        Asset asset = null;
        if (command.assetRowId() != null) {
            asset = assetRepository.findById(command.assetRowId())
                .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.ASSET_NOT_FOUND));
        }

        // 이전 영향 제거 (기존 값 스냅샷 → 롤백 후 새 값 적용)
        Asset previousAsset = expense.getAsset();
        ExpenseType previousType = expense.getExpenseType();
        Long previousAmount = expense.getAmount();
        revertExpenseFromAssetBalance(previousAsset, previousType, previousAmount);

        expense.updateExpense(
            category, asset,
            command.expenseType(),
            command.amount(),
            command.description(),
            command.expenseDate(),
            command.merchant(),
            command.paymentMethod()
        );

        // 새 영향 적용
        applyExpenseToAssetBalance(asset, command.expenseType(), command.amount());

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

        if (command.groupRowId() != null) {
            UserGroup group = userGroupRepository.findById(command.groupRowId())
                .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.GROUP_NOT_FOUND));
            groupMembershipValidator.validateMembership(command.groupRowId(), expense.getUser().getRowId());
            expense.setGroup(group);
        } else {
            expense.setGroup(null);
        }

        log.info("지출 수정 완료: expenseId={}", expenseId);

        return ExpenseServiceDto.ExpenseInfo.from(expense);
    }

    @Override
    @Transactional
    public void deleteExpense(Long expenseId, Long userRowId) {
        log.debug("지출 삭제 시작: expenseId={}", expenseId);

        Expense expense = findExpenseOrThrow(expenseId);
        validateExpenseOwnership(expense, userRowId);

        // 자산 잔액 복원 (삭제되는 expense의 영향 제거)
        revertExpenseFromAssetBalance(expense.getAsset(), expense.getExpenseType(), expense.getAmount());

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
                ExpenseCategory parentCategory = first.getCategory().getParent();
                return new ExpenseServiceDto.CategoryBreakdown(
                    first.getCategory().getRowId(),
                    first.getCategory().getCategoryName(),
                    parentCategory != null ? parentCategory.getRowId() : null,
                    parentCategory != null ? parentCategory.getCategoryName() : null,
                    first.getExpenseType(),
                    totalAmount
                );
            })
            .toList();

        return new ExpenseServiceDto.MonthlySummary(year, month, totalIncome, totalExpense, categoryBreakdown);
    }

    @Override
    public List<ExpenseServiceDto.MonthlyTrend> getMonthlyTrend(Long userRowId, Integer months) {
        int n = (months == null || months < 1) ? 6 : Math.min(months, 24);
        log.debug("지출 월별 트렌드 조회: userRowId={}, months={}", userRowId, n);

        LocalDate now = LocalDate.now();
        List<ExpenseServiceDto.MonthlyTrend> trends = new java.util.ArrayList<>(n);

        for (int i = n - 1; i >= 0; i--) {
            LocalDate m = now.minusMonths(i);
            int y = m.getYear();
            int mm = m.getMonthValue();
            List<Expense> expenses = expenseRepository.findMonthlySummary(userRowId, y, mm);

            long income = expenses.stream()
                .filter(e -> e.getExpenseType() == ExpenseType.INCOME)
                .mapToLong(Expense::getAmount)
                .sum();
            long expense = expenses.stream()
                .filter(e -> e.getExpenseType() == ExpenseType.EXPENSE)
                .mapToLong(Expense::getAmount)
                .sum();

            trends.add(new ExpenseServiceDto.MonthlyTrend(y, mm, income, expense));
        }
        return trends;
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
            .map(entry -> {
                List<Expense> monthExpenses = entry.getValue();

                Long monthIncome = monthExpenses.stream()
                    .filter(e -> e.getExpenseType() == ExpenseType.INCOME)
                    .mapToLong(Expense::getAmount)
                    .sum();

                Long monthExpense = monthExpenses.stream()
                    .filter(e -> e.getExpenseType() == ExpenseType.EXPENSE)
                    .mapToLong(Expense::getAmount)
                    .sum();

                List<ExpenseServiceDto.CategoryBreakdown> categoryBreakdown = monthExpenses.stream()
                    .collect(Collectors.groupingBy(e -> e.getCategory().getRowId(), Collectors.toList()))
                    .entrySet().stream()
                    .map(catEntry -> {
                        List<Expense> categoryExpenses = catEntry.getValue();
                        Expense first = categoryExpenses.get(0);
                        Long totalAmount = categoryExpenses.stream()
                            .mapToLong(Expense::getAmount)
                            .sum();
                        ExpenseCategory parentCategory = first.getCategory().getParent();
                        return new ExpenseServiceDto.CategoryBreakdown(
                            first.getCategory().getRowId(),
                            first.getCategory().getCategoryName(),
                            parentCategory != null ? parentCategory.getRowId() : null,
                            parentCategory != null ? parentCategory.getCategoryName() : null,
                            first.getExpenseType(),
                            totalAmount
                        );
                    })
                    .toList();

                return new ExpenseServiceDto.MonthlyAmount(entry.getKey(), monthIncome, monthExpense, categoryBreakdown);
            })
            .sorted((a, b) -> a.month().compareTo(b.month()))
            .toList();

        return new ExpenseServiceDto.YearlySummary(year, totalIncome, totalExpense, monthlyAmounts);
    }

    @Override
    public List<ExpenseServiceDto.MerchantSummary> getMerchantSummary(Long userRowId, LocalDate startDate, LocalDate endDate) {
        log.debug("거래처별 요약 조회: userRowId={}", userRowId);

        List<Expense> expenses = expenseRepository.findByUser(userRowId, null, null, startDate, endDate);

        return expenses.stream()
            .filter(e -> e.getExpenseType() == ExpenseType.EXPENSE)
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
            .filter(e -> e.getExpenseType() == ExpenseType.EXPENSE)
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
    public List<ExpenseServiceDto.ExpenseInfo> getGroupExpenses(Long userRowId, Long groupId, Long categoryRowId, ExpenseType expenseType, LocalDate startDate, LocalDate endDate) {
        groupMembershipValidator.validateMembership(groupId, userRowId);

        List<Expense> expenses = expenseRepository.findByGroups(List.of(groupId), categoryRowId, expenseType, startDate, endDate);

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

    @Override
    public List<ExpenseServiceDto.HeatmapCell> getHeatmap(Long userRowId, Integer year, Integer month) {
        log.debug("지출 히트맵 조회: userRowId={}, year={}, month={}", userRowId, year, month);

        // 지출(EXPENSE)만 히트맵 집계 대상
        List<Object[]> rows = expenseRepository.sumGroupedByDayOfWeekAndHour(
            userRowId, ExpenseType.EXPENSE, year, month
        );

        // MySQL/MariaDB DAYOFWEEK(1=일 ~ 7=토) → Java DayOfWeek(1=월 ~ 7=일) 변환
        //   sun(1) → 7, mon(2) → 1, tue(3) → 2, ..., sat(7) → 6
        //   공식: javaDow = ((mysqlDow + 5) % 7) + 1
        return rows.stream()
            .map(row -> {
                int mysqlDow = ((Number) row[0]).intValue();
                int hour = ((Number) row[1]).intValue();
                long amount = ((Number) row[2]).longValue();
                int javaDow = ((mysqlDow + 5) % 7) + 1;
                return new ExpenseServiceDto.HeatmapCell(javaDow, hour, amount);
            })
            .toList();
    }

    private void validateExpenseOwnership(Expense expense, Long userRowId) {
        if (expense.getGroup() != null) {
            UserGroupMember member = groupMembershipValidator.validateMembership(
                expense.getGroup().getRowId(), userRowId);
            if (!groupMembershipValidator.canEditOrDelete(member, expense.getUser().getRowId(), userRowId)) {
                throw new ForbiddenException(DeskErrorCode.EXPENSE_ACCESS_DENIED);
            }
            return;
        }
        if (!expense.getUser().getRowId().equals(userRowId)) {
            log.warn("지출 소유권 검증 실패 - expenseId={}, ownerRowId={}, requestUserRowId={}",
                expense.getRowId(), expense.getUser().getRowId(), userRowId);
            throw new ForbiddenException(DeskErrorCode.EXPENSE_ACCESS_DENIED);
        }
    }

    private void validateCategoryOwnership(ExpenseCategory category, Long userRowId) {
        if (!category.getUser().getRowId().equals(userRowId)) {
            log.warn("지출 카테고리 소유권 검증 실패 - categoryId={}, ownerRowId={}, requestUserRowId={}",
                category.getRowId(), category.getUser().getRowId(), userRowId);
            throw new ForbiddenException(DeskErrorCode.EXPENSE_ACCESS_DENIED);
        }
    }

    private void validateAssetOwnership(Asset asset, Long userRowId) {
        if (!asset.getUser().getRowId().equals(userRowId)) {
            log.warn("자산 소유권 검증 실패 - assetId={}, ownerRowId={}, requestUserRowId={}",
                asset.getRowId(), asset.getUser().getRowId(), userRowId);
            throw new ForbiddenException(DeskErrorCode.EXPENSE_ACCESS_DENIED);
        }
    }

    private Expense findExpenseOrThrow(Long expenseId) {
        return expenseRepository.findById(expenseId)
            .orElseThrow(() -> {
                log.warn("지출 조회 실패 - 존재하지 않는 지출: expenseId={}", expenseId);
                return new EntityNotFoundException(DeskErrorCode.EXPENSE_NOT_FOUND);
            });
    }

    /**
     * expense 생성/수정 시 asset.balance를 동기화.
     * 수입(INCOME)은 잔액 증가, 지출(EXPENSE)은 잔액 감소.
     * asset이 null이거나 amount가 null이면 no-op.
     */
    private void applyExpenseToAssetBalance(Asset asset, ExpenseType type, Long amount) {
        if (asset == null || type == null || amount == null) {
            return;
        }
        long delta = (type == ExpenseType.INCOME) ? amount : -amount;
        asset.updateBalance(asset.getBalance() + delta);
    }

    /**
     * expense 삭제/수정 시 기존 영향을 롤백.
     * applyExpenseToAssetBalance 의 역연산.
     */
    private void revertExpenseFromAssetBalance(Asset asset, ExpenseType type, Long amount) {
        if (asset == null || type == null || amount == null) {
            return;
        }
        long delta = (type == ExpenseType.INCOME) ? -amount : amount;
        asset.updateBalance(asset.getBalance() + delta);
    }

    /**
     * 이번 지출로 해당 월 예산이 85% / 100% 임계를 "처음으로" 넘었을 때만 알림 생성.
     * 대상 예산: 전체(overall, categoryRowId=null) / 지출 카테고리 본인 / 지출 카테고리의 부모.
     * 실패는 무시(알림 실패가 지출 저장을 막으면 안 됨).
     */
    private void notifyBudgetThresholdIfCrossed(Expense expense) {
        try {
            if (expense == null || expense.getExpenseType() != ExpenseType.EXPENSE) return;
            if (expense.getAmount() == null || expense.getAmount() <= 0) return;

            Long userRowId = expense.getUser().getRowId();
            int year = expense.getExpenseDate().getYear();
            int month = expense.getExpenseDate().getMonthValue();

            List<ExpenseBudget> budgets = expenseBudgetRepository.findByUser(userRowId, year, month);
            if (budgets.isEmpty()) return;

            Long catId = expense.getCategory() != null ? expense.getCategory().getRowId() : null;
            Long parentId = (expense.getCategory() != null && expense.getCategory().getParent() != null)
                ? expense.getCategory().getParent().getRowId()
                : null;

            // 해당 월의 총 지출/카테고리별 지출 집계 (방금 저장된 이 expense 포함)
            List<Expense> monthly = expenseRepository.findMonthlySummary(userRowId, year, month);
            long totalSpent = 0L;
            Map<Long, Long> spentByCat = new HashMap<>();
            for (Expense e : monthly) {
                if (e.getExpenseType() != ExpenseType.EXPENSE) continue;
                totalSpent += e.getAmount();
                if (e.getCategory() == null) continue;
                spentByCat.merge(e.getCategory().getRowId(), e.getAmount(), Long::sum);
                if (e.getCategory().getParent() != null) {
                    spentByCat.merge(e.getCategory().getParent().getRowId(), e.getAmount(), Long::sum);
                }
            }

            long delta = expense.getAmount();

            for (ExpenseBudget budget : budgets) {
                if (budget.getBudgetAmount() == null || budget.getBudgetAmount() <= 0) continue;
                Long bCatId = budget.getCategory() != null ? budget.getCategory().getRowId() : null;

                // 이 예산이 방금 지출과 관련 있는가?
                boolean matches = bCatId == null
                    || bCatId.equals(catId)
                    || (parentId != null && bCatId.equals(parentId));
                if (!matches) continue;

                long afterSpent = (bCatId == null) ? totalSpent : spentByCat.getOrDefault(bCatId, 0L);
                long beforeSpent = afterSpent - delta;
                double limit = budget.getBudgetAmount();
                double beforePct = beforeSpent / limit;
                double afterPct = afterSpent / limit;

                String categoryName = bCatId == null ? "전체" : budget.getCategory().getCategoryName();

                if (beforePct < BUDGET_OVER_THRESHOLD && afterPct >= BUDGET_OVER_THRESHOLD) {
                    notificationService.createNotification(new NotificationServiceDto.CreateCommand(
                        userRowId,
                        NotificationType.BUDGET_ALERT,
                        String.format("%s 예산 초과", categoryName),
                        String.format("%s 예산 %s원을 초과했어요 (현재 %s원).",
                            categoryName, formatKRW((long) limit), formatKRW(afterSpent)),
                        ReferenceType.EXPENSE_BUDGET,
                        budget.getRowId()
                    ));
                } else if (beforePct < BUDGET_WARN_THRESHOLD && afterPct >= BUDGET_WARN_THRESHOLD) {
                    int pct = (int) Math.round(afterPct * 100);
                    notificationService.createNotification(new NotificationServiceDto.CreateCommand(
                        userRowId,
                        NotificationType.BUDGET_ALERT,
                        String.format("%s 예산 %d%% 사용", categoryName, pct),
                        String.format("%s 예산의 %d%%를 사용했어요 (%s / %s원).",
                            categoryName, pct, formatKRW(afterSpent), formatKRW((long) limit)),
                        ReferenceType.EXPENSE_BUDGET,
                        budget.getRowId()
                    ));
                }
            }
        } catch (Exception ex) {
            log.warn("예산 임계 알림 처리 실패: {}", ex.getMessage());
        }
    }

    private static String formatKRW(long v) {
        return String.format("%,d", v);
    }
}
