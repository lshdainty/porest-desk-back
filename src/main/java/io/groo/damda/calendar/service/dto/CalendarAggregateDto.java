package io.groo.damda.calendar.service.dto;

import io.groo.damda.expense.service.dto.ExpenseServiceDto;
import io.groo.damda.todo.service.dto.TodoServiceDto;

import java.util.List;

public class CalendarAggregateDto {

    public record AggregateData(
        List<CalendarEventServiceDto.EventInfo> events,
        List<TodoServiceDto.TodoInfo> todos,
        List<ExpenseServiceDto.ExpenseInfo> expenses
    ) {}
}
