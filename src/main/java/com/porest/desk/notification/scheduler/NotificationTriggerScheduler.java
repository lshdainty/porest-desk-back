package com.porest.desk.notification.scheduler;

import com.porest.desk.calendar.domain.EventReminder;
import com.porest.desk.calendar.repository.EventReminderRepository;
import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.domain.ExpenseBudget;
import com.porest.desk.expense.repository.ExpenseBudgetRepository;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.service.ExpenseService;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.notification.repository.NotificationRepository;
import com.porest.desk.notification.service.NotificationService;
import com.porest.desk.notification.service.dto.NotificationServiceDto;
import com.porest.desk.notification.type.NotificationType;
import com.porest.desk.notification.type.ReferenceType;
import com.porest.desk.todo.domain.Todo;
import com.porest.desk.todo.repository.TodoRepository;
import com.porest.desk.user.service.UserService;
import com.porest.desk.common.time.ServiceClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationTriggerScheduler {
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final EventReminderRepository eventReminderRepository;
    private final ExpenseBudgetRepository expenseBudgetRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseService expenseService;
    private final ServiceClock serviceClock;
    private final TodoRepository todoRepository;
    private final UserService userService;

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void checkEventReminders() {
        List<EventReminder> reminders = eventReminderRepository.findUnsentDueReminders(LocalDateTime.now());

        for (EventReminder reminder : reminders) {
            try {
                NotificationServiceDto.CreateCommand command = new NotificationServiceDto.CreateCommand(
                    reminder.getEvent().getUser().getRowId(),
                    NotificationType.EVENT_REMINDER,
                    reminder.getEvent().getTitle() + " 알림",
                    reminder.getMinutesBefore() + "분 전 알림",
                    ReferenceType.CALENDAR_EVENT,
                    reminder.getEvent().getRowId()
                );
                notificationService.createNotification(command);
                reminder.markSent();
                log.info("이벤트 리마인더 알림 전송 완료: reminderId={}, eventId={}",
                    reminder.getRowId(), reminder.getEvent().getRowId());
            } catch (Exception e) {
                log.error("이벤트 리마인더 알림 전송 실패: reminderId={}", reminder.getRowId(), e);
            }
        }
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "${app.scheduler.zone:Asia/Seoul}")
    public void checkBudgetAlerts() {
        log.info("예산 알림 스케줄러 실행 시작");
        // 배치 — 서비스 운영 기준 날짜
        LocalDate now = serviceClock.today();
        int year = now.getYear();
        int month = now.getMonthValue();

        List<ExpenseBudget> budgets = expenseBudgetRepository.findAllByYearAndMonth(year, month);

        // 사용자별 split-aware 카테고리 지출(leaf+부모 롤업) 캐시 — 같은 사용자 예산이 여럿이어도 1회만 집계.
        Map<Long, Map<Long, Long>> spendByUser = new HashMap<>();

        for (ExpenseBudget budget : budgets) {
            try {
                if (budget.getBudgetAmount() == 0) {
                    continue;
                }

                // 0/음수 예산은 사용률 계산에서 0 나눗셈(ArithmeticException)을 유발 — 방어적으로 건너뜀.
                if (budget.getBudgetAmount() == null || budget.getBudgetAmount() <= 0) {
                    continue;
                }

                Long userRowId = budget.getUser().getRowId();
                Long categoryRowId = budget.getCategory() != null ? budget.getCategory().getRowId() : null;

                LocalDate startDate = LocalDate.of(year, month, 1);
                LocalDate endDate = startDate.plusMonths(1).minusDays(1);

                // 카테고리 예산은 자식 지출까지 합산(roll-up, split-aware), 전체(=null)는 월 전체 지출.
                // 분할이 있는 거래는 분할 항목 카테고리로 귀속(거래의 단일 카테고리가 아님).
                long totalSpending;
                if (categoryRowId != null) {
                    Map<Long, Long> userSpend = spendByUser.computeIfAbsent(
                        userRowId, u -> expenseService.getMonthlyExpenseSpendByCategory(u, year, month));
                    totalSpending = userSpend.getOrDefault(categoryRowId, 0L);
                } else {
                    totalSpending = expenseRepository.findByUser(
                        userRowId, null, ExpenseType.EXPENSE, startDate, endDate)
                        .stream().mapToLong(Expense::getAmount).sum();
                }

                // 임계값은 사용자 설정(user.budget_alert_threshold, %) 사용 — 미설정 시 85%.
                int thresholdPct = userService.getBudgetAlertThreshold(userRowId);
                if (totalSpending >= budget.getBudgetAmount() * (thresholdPct / 100.0)) {
                    boolean alreadyNotified = notificationRepository.existsByUserAndReferenceAndCreatedAfter(
                        userRowId, ReferenceType.EXPENSE_BUDGET, budget.getRowId(),
                        startDate.atStartOfDay());

                    if (!alreadyNotified) {
                        long percentage = (totalSpending * 100) / budget.getBudgetAmount();
                        String categoryName = budget.getCategory() != null
                            ? budget.getCategory().getCategoryName() : "전체";

                        NotificationServiceDto.CreateCommand command = new NotificationServiceDto.CreateCommand(
                            userRowId,
                            NotificationType.BUDGET_ALERT,
                            categoryName + " 예산 초과 경고",
                            categoryName + " 카테고리 예산의 " + percentage + "%를 사용했습니다.",
                            ReferenceType.EXPENSE_BUDGET,
                            budget.getRowId()
                        );
                        notificationService.createNotification(command);
                        log.info("예산 알림 전송 완료: userRowId={}, budgetId={}, percentage={}%",
                            userRowId, budget.getRowId(), percentage);
                    }
                }
            } catch (Exception e) {
                log.error("예산 알림 처리 실패: budgetId={}", budget.getRowId(), e);
            }
        }
        log.info("예산 알림 스케줄러 실행 완료");
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "${app.scheduler.zone:Asia/Seoul}")
    public void checkTodoReminders() {
        log.info("할일 리마인더 스케줄러 실행 시작");
        // 배치 — 서비스 운영 기준 날짜
        LocalDate today = serviceClock.today();
        LocalDate tomorrow = today.plusDays(1);

        List<Todo> todos = todoRepository.findDueTodosForReminder(today, tomorrow);

        for (Todo todo : todos) {
            try {
                Long userRowId = todo.getUser().getRowId();

                boolean alreadyNotified = notificationRepository.existsByUserAndReferenceAndCreatedAfter(
                    userRowId, ReferenceType.TODO, todo.getRowId(),
                    today.atStartOfDay());

                if (!alreadyNotified) {
                    String message = todo.getDueDate().equals(today)
                        ? "오늘 마감인 할일이 있습니다."
                        : "내일 마감인 할일이 있습니다.";

                    NotificationServiceDto.CreateCommand command = new NotificationServiceDto.CreateCommand(
                        userRowId,
                        NotificationType.TODO_REMINDER,
                        todo.getTitle(),
                        message,
                        ReferenceType.TODO,
                        todo.getRowId()
                    );
                    notificationService.createNotification(command);
                    log.info("할일 리마인더 알림 전송 완료: userRowId={}, todoId={}", userRowId, todo.getRowId());
                }
            } catch (Exception e) {
                log.error("할일 리마인더 알림 처리 실패: todoId={}", todo.getRowId(), e);
            }
        }
        log.info("할일 리마인더 스케줄러 실행 완료");
    }
}
