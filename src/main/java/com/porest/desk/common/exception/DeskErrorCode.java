package com.porest.desk.common.exception;

import com.porest.core.exception.ErrorCodeProvider;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum DeskErrorCode implements ErrorCodeProvider {
    // Common
    INTERNAL_SERVER_ERROR("COMMON_500", "error.common.internal", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_INPUT("COMMON_400", "error.common.invalid.input", HttpStatus.BAD_REQUEST),
    VALIDATION_FAILED("COMMON_422", "error.common.validation.failed", HttpStatus.UNPROCESSABLE_ENTITY),

    // Auth
    AUTH_INVALID_TOKEN("AUTH_001", "error.auth.invalid.token", HttpStatus.UNAUTHORIZED),
    AUTH_EXPIRED_TOKEN("AUTH_002", "error.auth.expired.token", HttpStatus.UNAUTHORIZED),
    AUTH_ACCESS_DENIED("AUTH_003", "error.auth.access.denied", HttpStatus.FORBIDDEN),
    AUTH_EXCHANGE_FAILED("AUTH_004", "error.auth.exchange.failed", HttpStatus.UNAUTHORIZED),

    // User
    USER_NOT_FOUND("USER_001", "error.notfound.user", HttpStatus.NOT_FOUND),
    USER_ALREADY_EXISTS("USER_002", "error.duplicate.user", HttpStatus.CONFLICT),

    // Todo
    TODO_NOT_FOUND("TODO_001", "error.notfound.todo", HttpStatus.NOT_FOUND),

    // Calendar
    CALENDAR_EVENT_NOT_FOUND("CAL_001", "error.notfound.calendar.event", HttpStatus.NOT_FOUND),
    CALENDAR_INVALID_DATE_RANGE("CAL_002", "error.calendar.invalid.date.range", HttpStatus.BAD_REQUEST),

    // Memo
    MEMO_NOT_FOUND("MEMO_001", "error.notfound.memo", HttpStatus.NOT_FOUND),
    MEMO_FOLDER_NOT_FOUND("MEMO_002", "error.notfound.memo.folder", HttpStatus.NOT_FOUND),

    // Timer
    TIMER_SESSION_NOT_FOUND("TIMER_001", "error.notfound.timer.session", HttpStatus.NOT_FOUND),

    // Expense
    EXPENSE_NOT_FOUND("EXP_001", "error.notfound.expense", HttpStatus.NOT_FOUND),
    EXPENSE_CATEGORY_NOT_FOUND("EXP_002", "error.notfound.expense.category", HttpStatus.NOT_FOUND),
    EXPENSE_BUDGET_NOT_FOUND("EXP_003", "error.notfound.expense.budget", HttpStatus.NOT_FOUND),
    ;

    private final String code;
    private final String messageKey;
    private final HttpStatus httpStatus;
}
