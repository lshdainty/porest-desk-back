package com.porest.desk.calendar.service.dto;

import com.porest.desk.expense.service.dto.ExpenseServiceDto;
import com.porest.desk.todo.service.dto.TodoServiceDto;

import java.util.List;

public class CalendarAggregateDto {

    public record AggregateData(
        List<CalendarEventServiceDto.EventInfo> events,
        List<TodoServiceDto.TodoInfo> todos,
        List<ExpenseServiceDto.ExpenseInfo> expenses
    ) {}
}
