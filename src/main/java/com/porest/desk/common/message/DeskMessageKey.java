package com.porest.desk.common.message;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DeskMessageKey {
    // Success messages
    SUCCESS_CREATE("success.create"),
    SUCCESS_UPDATE("success.update"),
    SUCCESS_DELETE("success.delete"),

    // Todo
    TODO_COMPLETED("todo.completed"),
    TODO_REOPENED("todo.reopened"),

    // Calendar
    CALENDAR_EVENT_CREATED("calendar.event.created"),

    // Memo
    MEMO_PINNED("memo.pinned"),
    MEMO_UNPINNED("memo.unpinned"),

    // Expense
    EXPENSE_RECORDED("expense.recorded"),
    ;

    private final String key;
}
