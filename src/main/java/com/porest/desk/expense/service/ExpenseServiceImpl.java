package com.porest.desk.expense.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.asset.service.AssetBalanceHistoryService;
import com.porest.desk.calendar.domain.CalendarEvent;
import com.porest.desk.calendar.repository.CalendarEventRepository;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.domain.ExpenseBudget;
import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.desk.expense.domain.ExpenseSplit;
import com.porest.desk.expense.repository.ExpenseBudgetRepository;
import com.porest.desk.expense.repository.ExpenseCategoryRepository;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.repository.ExpenseSplitRepository;
import com.porest.desk.expense.service.dto.ExpenseServiceDto;
import com.porest.desk.expense.service.dto.ExpenseSplitServiceDto;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.notification.service.NotificationService;
import com.porest.desk.notification.service.dto.NotificationServiceDto;
import com.porest.desk.notification.type.NotificationType;
import com.porest.desk.notification.type.ReferenceType;
import com.porest.desk.todo.domain.Todo;
import com.porest.desk.todo.repository.TodoRepository;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import com.porest.desk.user.service.UserService;
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
    /** 예산 사용량 알림 임계값 (사용률). warn 는 사용자 설정, over 는 100% 고정. */
    private static final double BUDGET_OVER_THRESHOLD = 1.0;

    private final ExpenseRepository expenseRepository;
    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final ExpenseBudgetRepository expenseBudgetRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final ExpenseSplitService expenseSplitService;
    private final NotificationService notificationService;
    private final UserService userService;
    private final AssetRepository assetRepository;
    private final AssetBalanceHistoryService balanceHistoryService;
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
        validateCategoryOwnership(category, command.userRowId());
        // 거래 유형 == 카테고리 유형 강제 (수입 거래는 수입 카테고리에만, 지출은 지출에만).
        // 프론트가 타입별로 카테고리를 거르지만 API 2차 가드 — 혼재 시 집계(breakdown) 오염 방지.
        if (category.getExpenseType() != command.expenseType()) {
            throw new InvalidValueException(DeskErrorCode.EXPENSE_TYPE_CATEGORY_MISMATCH);
        }

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

        expenseRepository.save(expense);

        // 자산 잔액 이력: 거래 flow 적재 → recompute 가 asset.balance 반영 (단일 writer)
        balanceHistoryService.recordExpense(asset, expense.getRowId(),
            command.expenseType(), command.amount(), command.expenseDate());

        // 예산 임계 도달 시 알림 (생성이므로 이전 기여분 없음). 생성 시점엔 분할이 아직 없어 거래 카테고리로 귀속.
        notifyBudgetThresholdIfCrossed(expense, 0L, Map.of());

        log.info("지출 등록 완료: expenseId={}, userRowId={}", expense.getRowId(), command.userRowId());

        return ExpenseServiceDto.ExpenseInfo.from(expense);
    }

    @Override
    public List<ExpenseServiceDto.ExpenseInfo> getExpenses(Long userRowId, Long categoryRowId, Long assetRowId, ExpenseType expenseType, LocalDate startDate, LocalDate endDate) {
        log.debug("지출 목록 조회: userRowId={}, assetRowId={}, expenseType={}", userRowId, assetRowId, expenseType);
        validateDateRange(startDate, endDate);

        List<Expense> allExpenses = new java.util.ArrayList<>(
            expenseRepository.findByUser(userRowId, categoryRowId, expenseType, startDate, endDate));

        // Asset 필터 (서비스 층) — repo 쿼리 시그니처 확장 대신 여기서 후처리
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

        // 수정 전 이 거래의 EXPENSE 기여분 (수정 후 임계 돌파 판정용 delta 기준) — 총액 + 카테고리별(split-aware).
        // 변경 전 값으로 캡처해야 하므로 expense.updateExpense(...) 전에 계산한다. 분할이 있으면 그 분할로 귀속.
        long previousTotal = (expense.getExpenseType() == ExpenseType.EXPENSE
                && expense.getAmount() != null) ? expense.getAmount() : 0L;
        Map<Long, Long> previousByCat = expenseSpendRollup(
                List.of(expense), loadSplitsByExpense(List.of(expense)));

        ExpenseCategory category = expenseCategoryRepository.findById(command.categoryRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.EXPENSE_CATEGORY_NOT_FOUND));
        // 거래 유형 == 카테고리 유형 강제 (create 와 대칭).
        if (category.getExpenseType() != command.expenseType()) {
            throw new InvalidValueException(DeskErrorCode.EXPENSE_TYPE_CATEGORY_MISMATCH);
        }
        // 정책: 상위(자식 보유) 카테고리에는 거래를 둘 수 없음.
        if (expenseCategoryRepository.hasChildren(category.getRowId())) {
            throw new InvalidValueException(DeskErrorCode.EXPENSE_CATEGORY_NOT_LEAF);
        }

        Asset asset = null;
        if (command.assetRowId() != null) {
            asset = assetRepository.findById(command.assetRowId())
                .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.ASSET_NOT_FOUND));
            validateAssetOwnership(asset, userRowId); // create 와 대칭 — 남의 자산 할당 차단
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

        // 자산 잔액 이력: 기존 flow soft-delete 후 새 flow 적재(자산 변경 포함) → recompute 가 잔액 반영
        balanceHistoryService.removeExpense(expense.getRowId());
        balanceHistoryService.recordExpense(asset, expense.getRowId(),
            command.expenseType(), command.amount(), command.expenseDate());

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

        // 분할 합 일치화: 분할이 있는 거래는 거래 금액과 분할 합이 항상 같아야 한다.
        // - splits != null: 클라이언트가 맞춘 분할로 교체. 합 == abs(금액) 검증은 replaceSplits 가 수행
        //   (이 시점 expense 는 새 금액으로 갱신돼 있으므로 새 금액 기준으로 검증된다) → 원자적 동시 수정.
        // - splits == null 인데 기존 활성 분할이 새 금액과 어긋남: 거부하여 클라이언트가 분할을
        //   맞춰 다시 저장하도록 유도(거래 금액↔분할 합 불변식 보호).
        if (command.splits() != null) {
            expenseSplitService.replaceSplits(new ExpenseSplitServiceDto.ReplaceCommand(
                expenseId, userRowId, command.splits()));
            // replaceSplits 가 영속성 컨텍스트를 flush·clear 하므로 응답은 재조회로 구성한다.
            expense = findExpenseOrThrow(expenseId);
        } else if (command.amount() != null) {
            List<ExpenseSplit> existingSplits = expenseSplitRepository.findByExpense(expenseId);
            if (!existingSplits.isEmpty()) {
                long splitSum = existingSplits.stream().mapToLong(ExpenseSplit::getAmount).sum();
                if (splitSum != Math.abs(command.amount())) {
                    log.warn("분할 합 불일치로 지출 수정 거부 - expenseId={}, splitSum={}, amount={}",
                        expenseId, splitSum, command.amount());
                    throw new InvalidValueException(DeskErrorCode.EXPENSE_SPLIT_AMOUNT_MISMATCH);
                }
            }
        }

        // 예산 임계 도달 시 알림 — 분할 영속화 이후에 실행해 새 분할까지 반영된 카테고리 귀속으로 판정.
        notifyBudgetThresholdIfCrossed(expense, previousTotal, previousByCat);

        log.info("지출 수정 완료: expenseId={}", expenseId);

        return ExpenseServiceDto.ExpenseInfo.from(expense);
    }

    @Override
    @Transactional
    public void deleteExpense(Long expenseId, Long userRowId) {
        log.debug("지출 삭제 시작: expenseId={}", expenseId);

        Expense expense = findExpenseOrThrow(expenseId);
        validateExpenseOwnership(expense, userRowId);

        expense.deleteExpense();
        // 자산 잔액 이력: 해당 거래 flow soft-delete → recompute 가 잔액 반영
        balanceHistoryService.removeExpense(expenseId);

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
    public ExpenseServiceDto.RangeSummary getRangeSummary(Long userRowId, LocalDate startDate, LocalDate endDate) {
        log.debug("지출 기간 요약 조회: userRowId={}, startDate={}, endDate={}", userRowId, startDate, endDate);
        validateDateRange(startDate, endDate);

        List<Expense> expenses = expenseRepository.findByDateRange(userRowId, startDate, endDate);

        Long totalIncome = expenses.stream()
            .filter(e -> e.getExpenseType() == ExpenseType.INCOME)
            .mapToLong(Expense::getAmount)
            .sum();

        Long totalExpense = expenses.stream()
            .filter(e -> e.getExpenseType() == ExpenseType.EXPENSE)
            .mapToLong(Expense::getAmount)
            .sum();

        List<ExpenseServiceDto.CategoryBreakdown> categoryBreakdown = buildCategoryBreakdown(expenses);

        // 추이 차트용 월별 버킷 — startDate~endDate 안의 모든 (year, month) 슬롯을 보장 (0 인 달도 포함)
        Map<String, List<Expense>> grouped = expenses.stream()
            .collect(Collectors.groupingBy(e -> e.getExpenseDate().getYear() + "-" + e.getExpenseDate().getMonthValue()));

        List<ExpenseServiceDto.RangeMonthlyBucket> monthlyBuckets = new java.util.ArrayList<>();
        LocalDate cursor = startDate.withDayOfMonth(1);
        LocalDate endMonth = endDate.withDayOfMonth(1);
        while (!cursor.isAfter(endMonth)) {
            int y = cursor.getYear();
            int m = cursor.getMonthValue();
            List<Expense> bucket = grouped.getOrDefault(y + "-" + m, List.of());
            long income = bucket.stream()
                .filter(e -> e.getExpenseType() == ExpenseType.INCOME)
                .mapToLong(Expense::getAmount).sum();
            long expense = bucket.stream()
                .filter(e -> e.getExpenseType() == ExpenseType.EXPENSE)
                .mapToLong(Expense::getAmount).sum();
            monthlyBuckets.add(new ExpenseServiceDto.RangeMonthlyBucket(y, m, income, expense));
            cursor = cursor.plusMonths(1);
        }

        return new ExpenseServiceDto.RangeSummary(
            startDate, endDate, totalIncome, totalExpense, categoryBreakdown, monthlyBuckets);
    }

    /**
     * 거래 목록을 카테고리 단위 합계로 집계.
     * 분할(ExpenseSplit) 항목이 있는 거래는 부모 카테고리 대신 분할 카테고리별로 집계.
     * 분할 합계는 부모 amount 와 일치하므로 totalIncome/totalExpense 는 영향 없음.
     */
    private List<ExpenseServiceDto.CategoryBreakdown> buildCategoryBreakdown(List<Expense> expenses) {
        if (expenses.isEmpty()) return List.of();

        List<Long> expenseIds = expenses.stream().map(Expense::getRowId).toList();
        List<ExpenseSplit> splits = expenseSplitRepository.findByExpenseIds(expenseIds);
        Map<Long, List<ExpenseSplit>> splitsByExpense = splits.stream()
            .collect(Collectors.groupingBy(s -> s.getExpense().getRowId()));

        Map<Long, ExpenseServiceDto.CategoryBreakdown> agg = new HashMap<>();
        for (Expense e : expenses) {
            List<ExpenseSplit> es = splitsByExpense.get(e.getRowId());
            if (es != null && !es.isEmpty()) {
                for (ExpenseSplit s : es) {
                    accumulateBreakdown(agg, s.getCategory(), e.getExpenseType(), s.getAmount());
                }
            } else {
                if (e.getCategory() == null) continue;
                accumulateBreakdown(agg, e.getCategory(), e.getExpenseType(), e.getAmount());
            }
        }
        return List.copyOf(agg.values());
    }

    private void accumulateBreakdown(Map<Long, ExpenseServiceDto.CategoryBreakdown> agg,
                                      ExpenseCategory category, ExpenseType type, Long amount) {
        Long key = category.getRowId();
        ExpenseServiceDto.CategoryBreakdown existing = agg.get(key);
        if (existing == null) {
            ExpenseCategory parent = category.getParent();
            agg.put(key, new ExpenseServiceDto.CategoryBreakdown(
                category.getRowId(),
                category.getCategoryName(),
                parent != null ? parent.getRowId() : null,
                parent != null ? parent.getCategoryName() : null,
                type,
                amount
            ));
        } else {
            agg.put(key, new ExpenseServiceDto.CategoryBreakdown(
                existing.categoryRowId(),
                existing.categoryName(),
                existing.parentCategoryRowId(),
                existing.parentCategoryName(),
                existing.expenseType(),
                existing.totalAmount() + amount
            ));
        }
    }

    @Override
    public List<ExpenseServiceDto.MonthlyTrend> getMonthlyTrend(Long userRowId, Integer months) {
        if (months != null && months < 0) {
            throw new InvalidValueException(DeskErrorCode.INVALID_INPUT);
        }
        int n = (months == null || months < 1) ? 6 : Math.min(months, 24);
        log.debug("지출 월별 트렌드 조회: userRowId={}, months={}", userRowId, n);

        LocalDate now = LocalDate.now();
        List<ExpenseServiceDto.MonthlyTrend> trends = new java.util.ArrayList<>(n);

        for (int i = n - 1; i >= 0; i--) {
            LocalDate m = now.minusMonths(i);
            int y = m.getYear();
            int mm = m.getMonthValue();
            LocalDate ms = LocalDate.of(y, mm, 1);
            LocalDate me = ms.plusMonths(1).minusDays(1);
            List<Expense> expenses = expenseRepository.findByDateRange(userRowId, ms, me);

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
    public List<ExpenseServiceDto.MerchantSummary> getMerchantSummary(Long userRowId, LocalDate startDate, LocalDate endDate) {
        log.debug("거래처별 요약 조회: userRowId={}", userRowId);
        validateDateRange(startDate, endDate);

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
        validateDateRange(startDate, endDate);

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
    public List<ExpenseServiceDto.HeatmapCell> getHeatmap(Long userRowId, LocalDate startDate, LocalDate endDate) {
        log.debug("지출 히트맵 조회: userRowId={}, startDate={}, endDate={}", userRowId, startDate, endDate);
        validateDateRange(startDate, endDate);

        // 지출(EXPENSE)만 히트맵 집계 대상. 합계 그대로 반환 — 평균 정규화는 클라이언트가 기간 길이로.
        List<Object[]> rows = expenseRepository.sumGroupedByDayOfWeekAndHour(
            userRowId, ExpenseType.EXPENSE, startDate, endDate
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
     * 해당 월의 EXPENSE 지출을 카테고리별로 집계해 반환(split-aware).
     * 분할이 있는 거래는 분할 항목 카테고리로, 없으면 거래 카테고리로 귀속하며,
     * 각 leaf 금액을 leaf 키와 부모 키 모두에 누적(롤업)한다.
     */
    @Override
    public Map<Long, Long> getMonthlyExpenseSpendByCategory(Long userRowId, int year, int month) {
        LocalDate ms = LocalDate.of(year, month, 1);
        LocalDate me = ms.plusMonths(1).minusDays(1);
        List<Expense> monthly = expenseRepository.findByDateRange(userRowId, ms, me);
        return expenseSpendRollup(monthly, loadSplitsByExpense(monthly));
    }

    /** 거래 id 목록의 활성 분할을 거래별로 묶어 반환. */
    private Map<Long, List<ExpenseSplit>> loadSplitsByExpense(List<Expense> expenses) {
        List<Long> ids = expenses.stream().map(Expense::getRowId).toList();
        if (ids.isEmpty()) return Map.of();
        return expenseSplitRepository.findByExpenseIds(ids).stream()
            .collect(Collectors.groupingBy(s -> s.getExpense().getRowId()));
    }

    /**
     * EXPENSE 거래를 split-aware 하게 카테고리별 합계로 집계(leaf + 부모 롤업).
     * 분할이 있으면 분할 항목 카테고리로, 없으면 거래 카테고리로 귀속.
     */
    private Map<Long, Long> expenseSpendRollup(List<Expense> expenses, Map<Long, List<ExpenseSplit>> splitsByExpense) {
        Map<Long, Long> spent = new HashMap<>();
        for (Expense e : expenses) {
            if (e.getExpenseType() != ExpenseType.EXPENSE || e.getAmount() == null) continue;
            List<ExpenseSplit> es = splitsByExpense.get(e.getRowId());
            if (es != null && !es.isEmpty()) {
                for (ExpenseSplit s : es) addSpendRollup(spent, s.getCategory(), s.getAmount());
            } else {
                addSpendRollup(spent, e.getCategory(), e.getAmount());
            }
        }
        return spent;
    }

    /** 금액을 카테고리 leaf 키와 (있으면) 부모 키 양쪽에 누적. */
    private void addSpendRollup(Map<Long, Long> spent, ExpenseCategory category, Long amount) {
        if (category == null || amount == null) return;
        spent.merge(category.getRowId(), amount, Long::sum);
        if (category.getParent() != null) {
            spent.merge(category.getParent().getRowId(), amount, Long::sum);
        }
    }

    /** 기간 조회 공통 검증 — 시작일이 종료일보다 늦으면 조용히 빈/부분 결과를 내지 않고 거부. */
    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new InvalidValueException(DeskErrorCode.EXPENSE_INVALID_DATE_RANGE);
        }
    }

    /**
     * 이번 지출 변경으로 월 예산이 warn/100% 임계를 "처음으로" 넘었을 때만 알림 생성 (split-aware).
     * 대상 예산: 전체(null) / 거래·분할이 귀속되는 카테고리(leaf) 및 그 부모.
     *
     * @param previousTotal 변경 전 이 거래가 EXPENSE 로 반영하던 총액(생성=0).
     * @param previousByCat 변경 전 이 거래의 카테고리별(leaf+부모 롤업) 기여분(생성=빈 맵).
     *        카테고리별 delta = 현재 기여분 − previousByCat 으로 임계 "돌파"를 판정(수정 시 전체가 새로
     *        더해진 것으로 오판 방지 + warn→over 에스컬레이션 유지). 실패는 무시(저장을 막지 않음).
     */
    private void notifyBudgetThresholdIfCrossed(Expense expense, long previousTotal, Map<Long, Long> previousByCat) {
        try {
            if (expense == null || expense.getExpenseType() != ExpenseType.EXPENSE) return;
            if (expense.getAmount() == null || expense.getAmount() <= 0) return;

            Long userRowId = expense.getUser().getRowId();
            int year = expense.getExpenseDate().getYear();
            int month = expense.getExpenseDate().getMonthValue();

            List<ExpenseBudget> budgets = expenseBudgetRepository.findByUser(userRowId, year, month);
            if (budgets.isEmpty()) return;

            // 사용자 설정 warn 임계(%)
            Integer warnPercent = userService.getBudgetAlertThreshold(userRowId);
            double warnThreshold = (warnPercent != null ? warnPercent : 85) / 100.0;

            // 해당 월의 split-aware 카테고리 지출(leaf+부모 롤업) + 전체 합계 (방금 저장된 이 expense 포함)
            LocalDate ms = LocalDate.of(year, month, 1);
            LocalDate me = ms.plusMonths(1).minusDays(1);
            List<Expense> monthly = expenseRepository.findByDateRange(userRowId, ms, me);
            Map<Long, List<ExpenseSplit>> splitsByExpense = loadSplitsByExpense(monthly);
            Map<Long, Long> spentByCat = expenseSpendRollup(monthly, splitsByExpense);
            long totalSpent = monthly.stream()
                .filter(e -> e.getExpenseType() == ExpenseType.EXPENSE && e.getAmount() != null)
                .mapToLong(Expense::getAmount).sum();

            // 이번 거래의 현재 기여분(leaf+부모) — delta(=현재−이전) 산정용
            Map<Long, Long> currentByCat = expenseSpendRollup(List.of(expense), splitsByExpense);
            long currentTotal = expense.getAmount();

            for (ExpenseBudget budget : budgets) {
                if (budget.getBudgetAmount() == null || budget.getBudgetAmount() <= 0) continue;
                Long bCatId = budget.getCategory() != null ? budget.getCategory().getRowId() : null;

                // 이 예산이 이번 거래(현재 또는 이전 기여)와 관련 있는가?
                boolean matches = bCatId == null
                    || currentByCat.containsKey(bCatId)
                    || previousByCat.containsKey(bCatId);
                if (!matches) continue;

                long afterSpent = (bCatId == null) ? totalSpent : spentByCat.getOrDefault(bCatId, 0L);
                long delta = (bCatId == null)
                    ? (currentTotal - previousTotal)
                    : (currentByCat.getOrDefault(bCatId, 0L) - previousByCat.getOrDefault(bCatId, 0L));
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
                } else if (beforePct < warnThreshold && afterPct >= warnThreshold) {
                    // WARN 분기는 정의상 afterPct < 1.0(초과 아님). 반올림이 100 이 되어
                    // '100% 사용' 으로 오표기되지 않도록 99 로 cap (초과는 위 OVER 분기가 담당).
                    int pct = Math.min(99, (int) Math.round(afterPct * 100));
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
