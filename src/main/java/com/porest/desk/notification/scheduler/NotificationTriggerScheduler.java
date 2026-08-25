package com.porest.desk.notification.scheduler;

import com.porest.desk.calendar.domain.CalendarEvent;
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
import com.porest.core.time.ServiceClock;
import com.porest.core.time.UserClock;
import com.porest.core.util.TimeUtils;
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
    private final UserClock userClock;
    private final TodoRepository todoRepository;
    private final UserService userService;

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void checkEventReminders() {
        // startDate 는 사용자가 쓴 벽시계([userClock])다. UTC 인 LocalDateTime.now() 와 SQL 에서
        // 직접 비교하면 KST 사용자의 리마인더가 9시간 늦게 나간다 — 후보만 넓게 가져와
        // 소유자 타임존의 "지금"으로 도래를 판정한다.
        List<EventReminder> reminders =
            eventReminderRepository.findUnsentRemindersStartingBefore(serviceClock.now().plusDays(2));

        for (EventReminder reminder : reminders) {
            try {
                CalendarEvent event = reminder.getEvent();
                LocalDateTime nowWall = LocalDateTime.now(
                    userClock.zoneOfTimezone(event.getUser().getTimezone()));
                int minutesBefore = reminder.getMinutesBefore() != null ? reminder.getMinutesBefore() : 0;
                if (event.getStartDate().minusMinutes(minutesBefore).isAfter(nowWall)) {
                    continue;
                }
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
                // 85% 같은 임계를 double 로 만들면(85/100.0) 이진 오차 때문에 경계에서 한 발 빨리·늦게 터진다.
                // 양변에 100 을 곱해 정수만으로 비교한다 — 금액도 임계도 정수라 이걸로 충분하다.
                if (totalSpending * 100L >= (long) budget.getBudgetAmount() * thresholdPct) {
                    // create_at 은 [UTC] — 서비스 기준 자정(벽시계)을 UTC 로 옮겨 비교한다.
                    // naive 하게 넘기면 경계가 9시간 밀려, 크론 시각이 바뀌면 중복 알림이 샌다.
                    boolean alreadyNotified = notificationRepository.existsByUserAndReferenceAndCreatedAfter(
                        userRowId, ReferenceType.EXPENSE_BUDGET, budget.getRowId(),
                        TimeUtils.toUtc(startDate.atStartOfDay(), serviceClock.zone().getId()));

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

                // create_at 은 [UTC] — 서비스 기준 자정(벽시계)을 UTC 로 옮겨 비교한다.
                boolean alreadyNotified = notificationRepository.existsByUserAndReferenceAndCreatedAfter(
                    userRowId, ReferenceType.TODO, todo.getRowId(),
                    TimeUtils.toUtc(today.atStartOfDay(), serviceClock.zone().getId()));

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
