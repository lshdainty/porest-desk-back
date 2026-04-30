package com.porest.desk.expense.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.desk.expense.domain.RecurringTransaction;
import com.porest.desk.expense.repository.ExpenseCategoryRepository;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.repository.RecurringTransactionRepository;
import com.porest.desk.expense.service.dto.RecurringTransactionServiceDto;
import com.porest.desk.expense.type.RecurringFrequency;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RecurringTransactionServiceImpl implements RecurringTransactionService {
    private final RecurringTransactionRepository recurringTransactionRepository;
    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final AssetRepository assetRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public RecurringTransactionServiceDto.RecurringInfo createRecurring(RecurringTransactionServiceDto.CreateCommand command) {
        log.debug("반복 거래 생성 시작: userRowId={}", command.userRowId());

        User user = userRepository.findById(command.userRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

        ExpenseCategory category = null;
        if (command.categoryRowId() != null) {
            category = expenseCategoryRepository.findById(command.categoryRowId())
                .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.EXPENSE_CATEGORY_NOT_FOUND));
            validateCategoryOwnership(category, command.userRowId());
        }

        Asset asset = null;
        if (command.assetRowId() != null) {
            asset = assetRepository.findById(command.assetRowId())
                .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.ASSET_NOT_FOUND));
            validateAssetOwnership(asset, command.userRowId());
        }

        Expense sourceExpense = null;
        if (command.sourceExpenseRowId() != null) {
            sourceExpense = expenseRepository.findById(command.sourceExpenseRowId())
                .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.EXPENSE_NOT_FOUND));
            if (!sourceExpense.getUser().getRowId().equals(command.userRowId())) {
                throw new ForbiddenException(DeskErrorCode.EXPENSE_ACCESS_DENIED);
            }
        }

        LocalDate nextExecutionDate = calculateNextExecutionDate(
            command.startDate(), command.frequency(), command.intervalValue(),
            command.dayOfWeek(), command.dayOfMonth()
        );

        RecurringTransaction recurring = RecurringTransaction.createRecurring(
            user, category, asset, sourceExpense,
            command.expenseType(), command.amount(), command.description(),
            command.merchant(), command.paymentMethod(),
            command.frequency(), command.intervalValue(),
            command.dayOfWeek(), command.dayOfMonth(),
            command.startDate(), command.endDate(), nextExecutionDate,
            command.autoLog(), command.notifyDayBefore()
        );

        recurringTransactionRepository.save(recurring);
        log.info("반복 거래 생성 완료: recurringId={}", recurring.getRowId());

        return RecurringTransactionServiceDto.RecurringInfo.from(recurring);
    }

    @Override
    public List<RecurringTransactionServiceDto.RecurringInfo> getRecurrings(Long userRowId) {
        return getRecurrings(userRowId, false, null);
    }

    @Override
    public List<RecurringTransactionServiceDto.RecurringInfo> getRecurrings(Long userRowId, boolean upcomingOnly, Integer limit) {
        log.debug("반복 거래 목록 조회: userRowId={}, upcomingOnly={}, limit={}", userRowId, upcomingOnly, limit);

        LocalDate today = LocalDate.now();
        java.util.stream.Stream<RecurringTransaction> stream = recurringTransactionRepository.findByUser(userRowId).stream();
        if (upcomingOnly) {
            stream = stream.filter(r -> r.getIsActive() == com.porest.core.type.YNType.Y)
                .filter(r -> r.getExpenseType() == com.porest.desk.expense.type.ExpenseType.EXPENSE)
                .filter(r -> r.getNextExecutionDate() != null && !r.getNextExecutionDate().isBefore(today));
        }
        if (limit != null && limit > 0) {
            stream = stream.limit(limit);
        }
        return stream.map(RecurringTransactionServiceDto.RecurringInfo::from).toList();
    }

    @Override
    @Transactional
    public RecurringTransactionServiceDto.RecurringInfo updateRecurring(Long recurringId, Long userRowId, RecurringTransactionServiceDto.UpdateCommand command) {
        log.debug("반복 거래 수정 시작: recurringId={}", recurringId);

        RecurringTransaction recurring = findRecurringOrThrow(recurringId);
        validateRecurringOwnership(recurring, userRowId);

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

        LocalDate nextExecutionDate = calculateNextExecutionDate(
            command.startDate(), command.frequency(), command.intervalValue(),
            command.dayOfWeek(), command.dayOfMonth()
        );

        recurring.updateRecurring(
            category, asset,
            command.expenseType(), command.amount(), command.description(),
            command.merchant(), command.paymentMethod(),
            command.frequency(), command.intervalValue(),
            command.dayOfWeek(), command.dayOfMonth(),
            command.startDate(), command.endDate(), nextExecutionDate,
            command.autoLog(), command.notifyDayBefore()
        );

        log.info("반복 거래 수정 완료: recurringId={}", recurringId);

        return RecurringTransactionServiceDto.RecurringInfo.from(recurring);
    }

    @Override
    @Transactional
    public void deleteRecurring(Long recurringId, Long userRowId) {
        log.debug("반복 거래 삭제 시작: recurringId={}", recurringId);

        RecurringTransaction recurring = findRecurringOrThrow(recurringId);
        validateRecurringOwnership(recurring, userRowId);
        recurring.deleteRecurring();

        log.info("반복 거래 삭제 완료: recurringId={}", recurringId);
    }

    @Override
    @Transactional
    public RecurringTransactionServiceDto.RecurringInfo toggleActive(Long recurringId, Long userRowId) {
        log.debug("반복 거래 활성/비활성 토글: recurringId={}", recurringId);

        RecurringTransaction recurring = findRecurringOrThrow(recurringId);
        validateRecurringOwnership(recurring, userRowId);
        recurring.toggleActive();

        log.info("반복 거래 토글 완료: recurringId={}, isActive={}", recurringId, recurring.getIsActive());

        return RecurringTransactionServiceDto.RecurringInfo.from(recurring);
    }

    @Override
    @Transactional
    public void executeDueTransactions() {
        LocalDate today = LocalDate.now();
        // 반복 거래 실행 시각은 오전 9시로 고정 (히트맵 시간 집계를 위한 기본값)
        LocalDateTime executionDateTime = today.atTime(9, 0);
        log.debug("반복 거래 실행 시작: date={}", today);

        List<RecurringTransaction> dueTransactions = recurringTransactionRepository.findDueTransactions(today);

        for (RecurringTransaction recurring : dueTransactions) {
            try {
                Expense expense = Expense.createExpense(
                    recurring.getUser(),
                    recurring.getCategory(),
                    recurring.getAsset(),
                    recurring.getExpenseType(),
                    recurring.getAmount(),
                    recurring.getDescription(),
                    executionDateTime,
                    recurring.getMerchant(),
                    recurring.getPaymentMethod()
                );

                expenseRepository.save(expense);

                // 자산 잔액 동기화: INCOME은 +, EXPENSE는 -
                Asset recurringAsset = recurring.getAsset();
                if (recurringAsset != null && recurring.getAmount() != null) {
                    long delta = (recurring.getExpenseType() == com.porest.desk.expense.type.ExpenseType.INCOME)
                        ? recurring.getAmount()
                        : -recurring.getAmount();
                    recurringAsset.updateBalance(recurringAsset.getBalance() + delta);
                }

                LocalDate nextDate = calculateNextDate(
                    recurring.getNextExecutionDate(),
                    recurring.getFrequency(),
                    recurring.getIntervalValue(),
                    recurring.getDayOfWeek(),
                    recurring.getDayOfMonth()
                );

                recurring.markExecuted(LocalDateTime.now(), nextDate);

                log.info("반복 거래 실행 완료: recurringId={}, expenseId={}, nextDate={}",
                    recurring.getRowId(), expense.getRowId(), nextDate);
            } catch (Exception e) {
                log.error("반복 거래 실행 실패: recurringId={}", recurring.getRowId(), e);
            }
        }

        log.info("반복 거래 실행 완료: 총 {}건 처리", dueTransactions.size());
    }

    private LocalDate calculateNextExecutionDate(LocalDate startDate, RecurringFrequency frequency,
                                                  Integer intervalValue, Integer dayOfWeek, Integer dayOfMonth) {
        LocalDate today = LocalDate.now();
        LocalDate nextDate = startDate;

        if (nextDate.isBefore(today)) {
            nextDate = today;
        }

        return adjustToFrequency(nextDate, frequency, dayOfWeek, dayOfMonth);
    }

    private LocalDate calculateNextDate(LocalDate currentDate, RecurringFrequency frequency,
                                         Integer intervalValue, Integer dayOfWeek, Integer dayOfMonth) {
        int interval = intervalValue != null ? intervalValue : 1;

        return switch (frequency) {
            case DAILY -> currentDate.plusDays(interval);
            case WEEKLY -> currentDate.plusWeeks(interval);
            case MONTHLY -> {
                LocalDate next = currentDate.plusMonths(interval);
                if (dayOfMonth != null) {
                    int maxDay = next.lengthOfMonth();
                    next = next.withDayOfMonth(Math.min(dayOfMonth, maxDay));
                }
                yield next;
            }
            case YEARLY -> currentDate.plusYears(interval);
        };
    }

    private LocalDate adjustToFrequency(LocalDate date, RecurringFrequency frequency,
                                         Integer dayOfWeek, Integer dayOfMonth) {
        return switch (frequency) {
            case DAILY -> date;
            case WEEKLY -> {
                if (dayOfWeek != null) {
                    int currentDow = date.getDayOfWeek().getValue();
                    int diff = dayOfWeek - currentDow;
                    if (diff < 0) diff += 7;
                    yield date.plusDays(diff);
                }
                yield date;
            }
            case MONTHLY -> {
                if (dayOfMonth != null) {
                    int maxDay = date.lengthOfMonth();
                    LocalDate adjusted = date.withDayOfMonth(Math.min(dayOfMonth, maxDay));
                    if (adjusted.isBefore(date)) {
                        adjusted = adjusted.plusMonths(1);
                        maxDay = adjusted.lengthOfMonth();
                        adjusted = adjusted.withDayOfMonth(Math.min(dayOfMonth, maxDay));
                    }
                    yield adjusted;
                }
                yield date;
            }
            case YEARLY -> date;
        };
    }

    private void validateRecurringOwnership(RecurringTransaction recurring, Long userRowId) {
        if (!recurring.getUser().getRowId().equals(userRowId)) {
            log.warn("반복 거래 소유권 검증 실패 - recurringId={}, ownerRowId={}, requestUserRowId={}",
                recurring.getRowId(), recurring.getUser().getRowId(), userRowId);
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

    private RecurringTransaction findRecurringOrThrow(Long recurringId) {
        return recurringTransactionRepository.findById(recurringId)
            .orElseThrow(() -> {
                log.warn("반복 거래 조회 실패 - 존재하지 않는 반복 거래: recurringId={}", recurringId);
                return new EntityNotFoundException(DeskErrorCode.RECURRING_TRANSACTION_NOT_FOUND);
            });
    }
}
