package com.porest.desk.expense.service;

import com.porest.core.time.ServiceClock;
import com.porest.core.time.UserClock;
import com.porest.desk.calendar.repository.EventReminderRepository;
import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.domain.ExpenseBudget;
import com.porest.desk.expense.repository.ExpenseBudgetRepository;
import com.porest.desk.expense.repository.ExpenseCategoryRepository;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.service.dto.ExpenseBudgetServiceDto;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.notification.repository.NotificationRepository;
import com.porest.desk.notification.scheduler.NotificationTriggerScheduler;
import com.porest.desk.notification.service.NotificationService;
import com.porest.desk.notification.service.dto.NotificationServiceDto;
import com.porest.desk.support.message.TestMessages;
import com.porest.desk.todo.repository.TodoRepository;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import com.porest.desk.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * QA 2026-09-03 #77 ① — 예산 중복이 <b>알림 중복으로 이어지는 인과</b>를 고정한다.
 *
 * <p>알림 스케줄러는 {@code findAllByYearAndMonth} 가 준 <b>예산 행마다</b> 돈다. 중복 방지
 * ({@code existsByUserAndReferenceAndCreatedAfter})도 {@code referenceRowId = 예산 행 아이디} 로
 * 걸려 있어 <b>행이 다르면 같은 상황도 각각 알린다</b>. 그래서 "행 수 = 알림 수" 이고,
 * 등록을 upsert 로 접는 것만으로 알림 중복이 같이 사라진다.
 *
 * <p>두 가지를 한 자리에서 보인다.
 * <ol>
 *   <li>같은 달 전체 예산을 두 번 등록 → 행 1개 → 알림 <b>1건</b></li>
 *   <li>(대조군) 행이 2개면 → 알림 <b>2건</b> — 고치기 전 상태가 이것이었다</li>
 * </ol>
 * 예산 저장소는 서비스와 스케줄러가 <b>같은 것</b>을 봐야 인과가 성립하므로 mock 이 아니라
 * 메모리 가짜 구현을 쓴다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExpenseBudgetDuplicateAlertTest {

    private static final long USER_ID = 1L;

    @Mock private UserRepository userRepository;
    @Mock private ExpenseCategoryRepository expenseCategoryRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private PlatformTransactionManager transactionManager;

    @Mock private NotificationService notificationService;
    @Mock private NotificationRepository notificationRepository;
    @Mock private EventReminderRepository eventReminderRepository;
    @Mock private ExpenseService expenseService;
    @Mock private TodoRepository todoRepository;
    @Mock private UserService userService;

    private final ServiceClock serviceClock = new ServiceClock("Asia/Seoul");
    private final UserClock userClock = new UserClock(rowId -> null, serviceClock);

    private final InMemoryBudgetRepository budgetRepository = new InMemoryBudgetRepository();

    private User user;
    private ExpenseBudgetService budgetService;

    @BeforeEach
    void setUp() {
        user = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(user, "rowId", USER_ID);

        budgetService = new ExpenseBudgetServiceImpl(
                budgetRepository, userClock, expenseCategoryRepository, expenseRepository,
                userRepository, transactionManager);

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(userService.getBudgetAlertThreshold(USER_ID)).willReturn(85);
        given(notificationRepository.existsByUserAndReferenceAndCreatedAfter(
                eq(USER_ID), any(), anyLong(), any())).willReturn(false);

        LocalDate today = serviceClock.today();
        // 전체 예산은 월 전체 지출 합을 본다 — 10,000 중 9,000 이면 임계 85% 를 넘는다.
        given(expenseRepository.findByUser(eq(USER_ID), any(), eq(ExpenseType.EXPENSE), any(), any()))
                .willReturn(List.of(Expense.createExpense(user, null, null, ExpenseType.EXPENSE,
                        9_000L, "x", today.atStartOfDay(), null, null, null, null, null, null, null)));
    }

    private NotificationTriggerScheduler scheduler() {
        return new NotificationTriggerScheduler(
                notificationService, TestMessages.notificationMessages(), notificationRepository,
                eventReminderRepository, budgetRepository, expenseRepository, expenseService,
                serviceClock, userClock, todoRepository, userService);
    }

    private ExpenseBudgetServiceDto.CreateCommand overallBudget(long amount) {
        LocalDate today = serviceClock.today();
        return new ExpenseBudgetServiceDto.CreateCommand(
                USER_ID, null, amount, today.getYear(), today.getMonthValue());
    }

    @Test
    @DisplayName("같은 달 전체 예산을 두 번 등록해도 예산 알림은 한 번만 나간다")
    void twoPostsProduceOneAlert() {
        budgetService.createBudget(overallBudget(10_000L));
        budgetService.createBudget(overallBudget(10_000L));

        LocalDate today = serviceClock.today();
        assertThat(budgetRepository.findAllByYearAndMonth(today.getYear(), today.getMonthValue()))
                .hasSize(1);

        scheduler().checkBudgetAlerts();

        verify(notificationService, times(1)).createNotification(any(NotificationServiceDto.CreateCommand.class));
    }

    @Test
    @DisplayName("대조군 — 예산 행이 2개면 같은 상황을 2번 알린다(고치기 전 상태)")
    void twoRowsProduceTwoAlerts() {
        LocalDate today = serviceClock.today();
        budgetRepository.save(ExpenseBudget.createBudget(
                user, null, 10_000L, today.getYear(), today.getMonthValue()));
        budgetRepository.save(ExpenseBudget.createBudget(
                user, null, 10_000L, today.getYear(), today.getMonthValue()));

        scheduler().checkBudgetAlerts();

        verify(notificationService, times(2)).createNotification(any(NotificationServiceDto.CreateCommand.class));
    }

    /** 서비스와 스케줄러가 같은 행을 보게 하는 메모리 구현 — 인과를 보이려면 저장소가 하나여야 한다. */
    private static class InMemoryBudgetRepository implements ExpenseBudgetRepository {
        private final List<ExpenseBudget> rows = new ArrayList<>();
        private final AtomicLong sequence = new AtomicLong();

        @Override public Optional<ExpenseBudget> findById(Long rowId) {
            return rows.stream().filter(b -> rowId.equals(b.getRowId())).findFirst();
        }

        @Override public List<ExpenseBudget> findByUser(Long userRowId, Integer year, Integer month) {
            return rows.stream()
                    .filter(b -> b.getUser().getRowId().equals(userRowId))
                    .filter(b -> year == null || year.equals(b.getBudgetYear()))
                    .filter(b -> month == null || month.equals(b.getBudgetMonth()))
                    .toList();
        }

        @Override public List<ExpenseBudget> findAllByYearAndMonth(Integer year, Integer month) {
            return rows.stream()
                    .filter(b -> year.equals(b.getBudgetYear()) && month.equals(b.getBudgetMonth()))
                    .toList();
        }

        @Override public Optional<ExpenseBudget> findByUserAndCategory(
                Long userRowId, Long categoryRowId, Integer year, Integer month) {
            return rows.stream()
                    .filter(b -> b.getUser().getRowId().equals(userRowId))
                    .filter(b -> year.equals(b.getBudgetYear()) && month.equals(b.getBudgetMonth()))
                    // 전체 예산은 category 가 null 이다 — `= null` 비교로는 못 잡는 자리.
                    .filter(b -> categoryRowId == null
                            ? b.getCategory() == null
                            : b.getCategory() != null && categoryRowId.equals(b.getCategory().getRowId()))
                    .findFirst();
        }

        @Override public List<ExpenseBudget> findAllByCategory(Long categoryRowId) {
            return rows.stream()
                    .filter(b -> b.getCategory() != null && categoryRowId.equals(b.getCategory().getRowId()))
                    .toList();
        }

        @Override public ExpenseBudget save(ExpenseBudget entity) {
            ReflectionTestUtils.setField(entity, "rowId", sequence.incrementAndGet());
            rows.add(entity);
            return entity;
        }

        @Override public void delete(ExpenseBudget entity) {
            rows.remove(entity);
        }
    }
}
