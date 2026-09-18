package com.porest.desk.expense.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.asset.service.AssetBalanceHistoryService;
import com.porest.desk.asset.service.AssetService;
import com.porest.desk.asset.service.dto.AssetServiceDto;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.common.validation.AmountLimits;
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
import com.porest.core.time.ServiceClock;
import com.porest.core.time.UserClock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@Transactional(readOnly = true)
public class RecurringTransactionServiceImpl implements RecurringTransactionService {
    private final RecurringTransactionRepository recurringTransactionRepository;
    private final UserClock userClock;
    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final AssetRepository assetRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final AssetBalanceHistoryService balanceHistoryService;
    private final AssetService assetService;
    private final ServiceClock serviceClock;
    private final ReservationRefs reservationRefs;

    /**
     * 자정 배치에서 <b>반복 거래 한 건마다</b> 새 트랜잭션을 여는 템플릿.
     *
     * <p>{@code @RequiredArgsConstructor} 를 버리고 생성자를 손으로 쓴 이유가 이것 하나다.
     * 자세한 이유는 {@link #executeDueTransactions} 주석에 있다.
     */
    private final TransactionTemplate newTransaction;

    public RecurringTransactionServiceImpl(RecurringTransactionRepository recurringTransactionRepository,
                                           UserClock userClock,
                                           ExpenseCategoryRepository expenseCategoryRepository,
                                           AssetRepository assetRepository,
                                           ExpenseRepository expenseRepository,
                                           UserRepository userRepository,
                                           AssetBalanceHistoryService balanceHistoryService,
                                           AssetService assetService,
                                           ServiceClock serviceClock,
                                           ReservationRefs reservationRefs,
                                           PlatformTransactionManager transactionManager) {
        this.recurringTransactionRepository = recurringTransactionRepository;
        this.userClock = userClock;
        this.expenseCategoryRepository = expenseCategoryRepository;
        this.assetRepository = assetRepository;
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.balanceHistoryService = balanceHistoryService;
        this.assetService = assetService;
        this.serviceClock = serviceClock;
        this.reservationRefs = reservationRefs;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional
    public RecurringTransactionServiceDto.RecurringInfo createRecurring(RecurringTransactionServiceDto.CreateCommand command) {
        validateAmount(command.amount());
        log.debug("반복 거래 생성 시작: userRowId={}", command.userRowId());

        User user = userRepository.findById(command.userRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

        ExpenseCategory category = reservationRefs.resolveOwnedCategory(
            command.expenseType(), command.categoryRowId(), command.userRowId());

        Asset asset = reservationRefs.findOwnedAsset(command.assetRowId(), command.userRowId());
        Asset toAsset = reservationRefs.findOwnedAsset(command.toAssetRowId(), command.userRowId());
        RecurringTransferValidator.validate(command.expenseType(), category, asset, toAsset,
            command.amount(), command.fee(), command.interestAmount());

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
            user, category, asset, toAsset, command.fee(), command.interestAmount(), sourceExpense,
            command.expenseType(), command.amount(), command.description(),
            command.merchant(), command.paymentMethod(),
            command.frequency(), command.intervalValue(),
            command.dayOfWeek(), command.dayOfMonth(),
            command.executionTime(),
            command.startDate(), command.endDate(), command.maxOccurrences(), nextExecutionDate,
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
        // 음수 limit 은 전체 반환으로 새어나가지 않도록 입력 자체를 거부 (null=제한 없음 유지)
        if (limit != null && limit < 0) {
            throw new InvalidValueException(DeskErrorCode.INVALID_INPUT);
        }

        LocalDate today = userClock.today(userRowId);
        java.util.stream.Stream<RecurringTransaction> stream = recurringTransactionRepository.findByUser(userRowId).stream();
        if (upcomingOnly) {
            stream = stream.filter(r -> r.getIsActive() == com.porest.core.type.YNType.Y)
                .filter(r -> r.getExpenseType() == com.porest.desk.expense.type.TxKind.EXPENSE)
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
        validateAmount(command.amount());
        log.debug("반복 거래 수정 시작: recurringId={}", recurringId);

        RecurringTransaction recurring = findRecurringOrThrow(recurringId);
        validateRecurringOwnership(recurring, userRowId);

        // 규칙 소유는 위에서 확인했다. 카테고리 소유 재확인은 종전 동작에 없다 — 정리 PR 이라
        // 여기서 늘리지 않는다(생성은 resolveOwnedCategory 로 계속 본다).
        ExpenseCategory category =
            reservationRefs.resolveCategory(command.expenseType(), command.categoryRowId());

        Asset asset = reservationRefs.findOwnedAsset(command.assetRowId(), userRowId);
        Asset toAsset = reservationRefs.findOwnedAsset(command.toAssetRowId(), userRowId);
        RecurringTransferValidator.validate(command.expenseType(), category, asset, toAsset,
            command.amount(), command.fee(), command.interestAmount());

        LocalDate nextExecutionDate = calculateNextExecutionDate(
            command.startDate(), command.frequency(), command.intervalValue(),
            command.dayOfWeek(), command.dayOfMonth()
        );

        recurring.updateRecurring(
            category, asset, toAsset, command.fee(), command.interestAmount(),
            command.expenseType(), command.amount(), command.description(),
            command.merchant(), command.paymentMethod(),
            command.frequency(), command.intervalValue(),
            command.dayOfWeek(), command.dayOfMonth(),
            command.executionTime(),
            command.startDate(), command.endDate(), command.maxOccurrences(), nextExecutionDate,
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

    /**
     * 자정 배치 — 오늘 실행할 반복 거래를 <b>건마다 따로</b> 처리한다.
     *
     * <p>배치 전체가 트랜잭션 하나면 한 건의 실패가 그날 처리분을 통째로 되돌린다. 아래
     * {@code catch} 는 예외를 삼키지만 트랜잭션은 그것과 별개로 이미 <b>rollback-only</b> 로
     * 찍혀 있어(프록시를 지나는 {@code assetService.createTransfer} 에서 터지면 특히 그렇다)
     * 마지막 커밋이 {@code UnexpectedRollbackException} 으로 끝난다. 계좌 하나 지워 둔 이체
     * 규칙 때문에 남의 반복 지출까지 안 남는다 — 다음 날 아무도 눈치채지 못한다.
     *
     * <p>그래서 이 메서드는 트랜잭션을 들지 않고({@code NOT_SUPPORTED}), 한 건을
     * {@link #newTransaction} 안에서 돌린다. 실패한 건만 롤백되고 나머지는 그대로 커밋된다.
     * 목록은 rowId 만 들고 나온다 — 트랜잭션 밖에서 읽은 엔티티는 어차피 준영속이라
     * 더티 체킹({@code markExecuted})이 안 먹는다.
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void executeDueTransactions() {
        // 배치 — 서비스 운영 기준 날짜(JVM 기본 UTC 를 쓰면 하루 어긋난다)
        LocalDate today = serviceClock.today();
        log.debug("반복 거래 실행 시작: date={}", today);

        List<Long> dueIds = recurringTransactionRepository.findDueTransactions(today).stream()
            .map(RecurringTransaction::getRowId)
            .toList();

        for (Long recurringId : dueIds) {
            try {
                newTransaction.executeWithoutResult(status -> executeOne(recurringId, today));
            } catch (Exception e) {
                log.error("반복 거래 실행 실패: recurringId={}", recurringId, e);
            }
        }

        log.info("반복 거래 실행 완료: 총 {}건 처리", dueIds.size());
    }

    /** 반복 거래 1건 — {@link #newTransaction} 이 연 트랜잭션 안에서만 부른다. */
    private void executeOne(Long recurringId, LocalDate today) {
        RecurringTransaction recurring = findRecurringOrThrow(recurringId);

        // 실행 시각은 반복 거래마다 사용자가 정한다. 예전에는 09:00 고정이었고,
        // 그 값이 컬럼 기본값이라 안 고른 건은 그대로 09:00 이다.
        LocalDateTime executionDateTime = today.atTime(
            recurring.getExecutionTime() != null
                ? recurring.getExecutionTime()
                : RecurringTransaction.DEFAULT_EXECUTION_TIME);

        // 무엇을 만드느냐만 갈린다 — 다음 날짜 계산·실행 표시는 종류와 무관하다.
        Long createdRowId = recurring.getExpenseType().isTransfer()
            ? executeAsTransfer(recurring, executionDateTime)
            : executeAsExpense(recurring, executionDateTime);

        LocalDate nextDate = calculateNextDate(
            recurring.getNextExecutionDate(),
            recurring.getFrequency(),
            recurring.getIntervalValue(),
            recurring.getDayOfWeek(),
            recurring.getDayOfMonth()
        );

        recurring.markExecuted(LocalDateTime.now(), nextDate);

        log.info("반복 거래 실행 완료: recurringId={}, type={}, createdRowId={}, nextDate={}",
            recurring.getRowId(), recurring.getExpenseType(), createdRowId, nextDate);
    }

    /** 지출·수입 반복 1건 실행 → 만들어진 지출의 rowId. */
    private Long executeAsExpense(RecurringTransaction recurring, LocalDateTime executionDateTime) {
        Expense expense = Expense.createExpense(
            recurring.getUser(),
            recurring.getCategory(),
            recurring.getAsset(),
            recurring.getExpenseType().toExpenseType(),
            recurring.getAmount(),
            recurring.getDescription(),
            executionDateTime,
            recurring.getMerchant(),
            recurring.getPaymentMethod(),
            null, // 반복 거래는 할부 개념이 없다
            null, null, null // 원화 결제
        );

        expenseRepository.save(expense);

        // 자산 잔액 이력: 자동 생성 expense 의 flow 적재 — 잔액은 조회할 때 이력에서 집계한다
        balanceHistoryService.recordExpense(recurring.getAsset(), expense.getRowId(),
            recurring.getExpenseType().toExpenseType(), recurring.getAmount(), executionDateTime);

        return expense.getRowId();
    }

    /**
     * 이체 반복 1건 실행 → 만들어진 이체의 rowId.
     *
     * <p>이체는 잔액 이력 2건·대출 이자 지출까지 끌고 다니므로 여기서 흉내 내지 않고
     * {@link AssetService#createTransfer} 를 그대로 부른다.
     *
     * <p><b>autoSource 는 걸지 않는다</b>(사용자 결정 2026-09-14). 값을 걸면 만들어진 이체가
     * 잠겨 고칠 수도 지울 수도 없는데, 반복 지출로 생긴 거래는 그냥 고쳐지므로 이체만 다르면
     * 사용자가 이유를 알 수 없다. 잘못 나간 이체는 그 건을 고치고, 규칙 자체는 반복 설정에서
     * 따로 고친다 — 이미 실행된 건과 앞으로 실행될 규칙은 별개다.
     */
    private Long executeAsTransfer(RecurringTransaction recurring, LocalDateTime executionDateTime) {
        // 저장할 때 통과한 규칙이라도 그 사이 계좌가 지워졌을 수 있다. createTransfer 가 다시 본다.
        AssetServiceDto.TransferInfo transfer = assetService.createTransfer(
            new AssetServiceDto.CreateTransferCommand(
                recurring.getUser().getRowId(),
                recurring.getAsset() != null ? recurring.getAsset().getRowId() : null,
                recurring.getToAsset() != null ? recurring.getToAsset().getRowId() : null,
                recurring.getAmount(),
                recurring.getFee(),
                recurring.getInterestAmount(),
                recurring.getDescription(),
                executionDateTime,
                null // autoSource — 위 주석 참고
            ));
        return transfer.rowId();
    }

    private LocalDate calculateNextExecutionDate(LocalDate startDate, RecurringFrequency frequency,
                                                  Integer intervalValue, Integer dayOfWeek, Integer dayOfMonth) {
        // 시작일·주기가 없으면 여기서 끊는다 — 아래 isBefore 와 switch 가 둘 다 null 을 못 견뎌
        // 종전엔 NullPointerException 이 그대로 500 으로 나갔다(QA 2026-09-07 #85).
        // DTO 에도 @NotNull 이 있지만, 계산이 생성·수정 두 경로에서 같은 자리를 지나므로
        // 여기 한 줄이 그 둘을 함께 지킨다.
        if (startDate == null || frequency == null) {
            throw new InvalidValueException(DeskErrorCode.REQUIRED_VALUE_MISSING);
        }
        LocalDate today = serviceClock.today();
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

    private RecurringTransaction findRecurringOrThrow(Long recurringId) {
        return recurringTransactionRepository.findById(recurringId)
            .orElseThrow(() -> {
                log.warn("반복 거래 조회 실패 - 존재하지 않는 반복 거래: recurringId={}", recurringId);
                return new EntityNotFoundException(DeskErrorCode.RECURRING_TRANSACTION_NOT_FOUND);
            });
    }

    /**
     * 반복거래 금액도 1원 이상 100억원 이하여야 한다 — 실행될 때마다 지출 flow 를 만들기 때문이다.
     *
     * <p>상한이 거래(ExpenseApiDto.MAX_AMOUNT)보다 크면 이 설정이 거래 상한을 우회하는 경로가
     * 된다 — 종전엔 상한이 없어 99조 반복 설정을 스케줄러가 그대로 찍었다(QA 2026-09-03 #54).
     * DTO 검증과 겹치지만 스케줄러·가져오기가 서비스를 직접 부르므로 여기도 남긴다.
     */
    private void validateAmount(Long amount) {
        if (amount == null || amount <= 0 || amount > AmountLimits.MAX_TX_AMOUNT) {
            throw new InvalidValueException(DeskErrorCode.EXPENSE_INVALID_AMOUNT);
        }
    }
}
