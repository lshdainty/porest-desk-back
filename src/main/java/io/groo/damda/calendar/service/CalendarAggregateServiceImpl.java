package io.groo.damda.calendar.service;

import io.groo.damda.calendar.domain.CalendarEvent;
import io.groo.damda.calendar.repository.CalendarEventRepository;
import io.groo.damda.calendar.repository.EventReminderRepository;
import io.groo.damda.calendar.service.dto.CalendarAggregateDto;
import io.groo.damda.calendar.service.dto.CalendarEventServiceDto;
import io.groo.damda.calendar.service.dto.EventReminderServiceDto;
import io.groo.damda.expense.domain.Expense;
import io.groo.damda.expense.repository.ExpenseRepository;
import io.groo.damda.expense.service.dto.ExpenseServiceDto;
import io.groo.damda.todo.domain.Todo;
import io.groo.damda.todo.repository.TodoRepository;
import io.groo.damda.todo.service.dto.TodoServiceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CalendarAggregateServiceImpl implements CalendarAggregateService {
    private final CalendarEventRepository calendarEventRepository;
    private final EventReminderRepository eventReminderRepository;
    private final TodoRepository todoRepository;
    private final ExpenseRepository expenseRepository;

    @Override
    public CalendarAggregateDto.AggregateData getAggregateData(Long userRowId, LocalDate startDate, LocalDate endDate) {
        log.debug("캘린더 통합 데이터 조회: userRowId={}, startDate={}, endDate={}", userRowId, startDate, endDate);

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        // Calendar Events
        List<CalendarEvent> calendarEvents = calendarEventRepository.findByUserAndDateRange(userRowId, startDateTime, endDateTime);

        List<Long> eventIds = calendarEvents.stream().map(CalendarEvent::getRowId).toList();
        Map<Long, List<EventReminderServiceDto.ReminderInfo>> remindersMap = loadRemindersMap(eventIds);

        List<CalendarEventServiceDto.EventInfo> eventInfos = calendarEvents.stream()
            .map(event -> CalendarEventServiceDto.EventInfo.from(
                event,
                remindersMap.getOrDefault(event.getRowId(), List.of())
            ))
            .toList();

        // Todos by dueDate
        List<Todo> todos = todoRepository.findByUserAndDueDateBetween(userRowId, startDate, endDate);
        List<TodoServiceDto.TodoInfo> todoInfos = todos.stream()
            .map(TodoServiceDto.TodoInfo::from)
            .toList();

        // Expenses by expenseDate
        List<Expense> expenses = expenseRepository.findByUser(userRowId, null, null, startDate, endDate);
        List<ExpenseServiceDto.ExpenseInfo> expenseInfos = expenses.stream()
            .map(ExpenseServiceDto.ExpenseInfo::from)
            .toList();

        log.debug("캘린더 통합 데이터 조회 완료: events={}, todos={}, expenses={}",
            eventInfos.size(), todoInfos.size(), expenseInfos.size());

        return new CalendarAggregateDto.AggregateData(eventInfos, todoInfos, expenseInfos);
    }

    private Map<Long, List<EventReminderServiceDto.ReminderInfo>> loadRemindersMap(List<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        return eventReminderRepository.findByEventIds(eventIds).stream()
            .map(EventReminderServiceDto.ReminderInfo::from)
            .collect(Collectors.groupingBy(EventReminderServiceDto.ReminderInfo::eventRowId));
    }
}
