package io.groo.damda.calendar.controller.dto;

import io.groo.damda.calendar.service.dto.CalendarAggregateDto;
import io.groo.damda.expense.controller.dto.ExpenseApiDto;
import io.groo.damda.todo.controller.dto.TodoApiDto;

import java.util.List;

public class CalendarAggregateApiDto {

    public record AggregateResponse(
        List<CalendarEventApiDto.Response> events,
        List<TodoApiDto.Response> todos,
        List<ExpenseApiDto.Response> expenses
    ) {
        public static AggregateResponse from(CalendarAggregateDto.AggregateData data) {
            List<CalendarEventApiDto.Response> eventResponses = data.events().stream()
                .map(CalendarEventApiDto.Response::from)
                .toList();
            List<TodoApiDto.Response> todoResponses = data.todos().stream()
                .map(TodoApiDto.Response::from)
                .toList();
            List<ExpenseApiDto.Response> expenseResponses = data.expenses().stream()
                .map(ExpenseApiDto.Response::from)
                .toList();
            return new AggregateResponse(eventResponses, todoResponses, expenseResponses);
        }
    }
}
