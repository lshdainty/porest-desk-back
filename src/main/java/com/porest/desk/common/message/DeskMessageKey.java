package com.porest.desk.common.message;

import com.porest.core.message.MessageKey;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DeskMessageKey implements MessageKey {
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

    // Timer
    TIMER_SESSION_SAVED("timer.session.saved"),

    // Expense
    EXPENSE_RECORDED("expense.recorded"),
    ;

    private final String key;
}
