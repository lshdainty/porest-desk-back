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
    TODO_PROJECT_NOT_FOUND("TODO_002", "error.notfound.todo.project", HttpStatus.NOT_FOUND),
    TODO_TAG_NOT_FOUND("TODO_003", "error.notfound.todo.tag", HttpStatus.NOT_FOUND),

    // Calendar
    CALENDAR_EVENT_NOT_FOUND("CAL_001", "error.notfound.calendar.event", HttpStatus.NOT_FOUND),
    CALENDAR_INVALID_DATE_RANGE("CAL_002", "error.calendar.invalid.date.range", HttpStatus.BAD_REQUEST),
    EVENT_LABEL_NOT_FOUND("CAL_003", "error.notfound.event.label", HttpStatus.NOT_FOUND),
    EVENT_REMINDER_NOT_FOUND("CAL_004", "error.notfound.event.reminder", HttpStatus.NOT_FOUND),

    // Memo
    MEMO_NOT_FOUND("MEMO_001", "error.notfound.memo", HttpStatus.NOT_FOUND),
    MEMO_FOLDER_NOT_FOUND("MEMO_002", "error.notfound.memo.folder", HttpStatus.NOT_FOUND),

    // Timer
    TIMER_SESSION_NOT_FOUND("TIMER_001", "error.notfound.timer.session", HttpStatus.NOT_FOUND),

    // Expense
    EXPENSE_NOT_FOUND("EXP_001", "error.notfound.expense", HttpStatus.NOT_FOUND),
    EXPENSE_CATEGORY_NOT_FOUND("EXP_002", "error.notfound.expense.category", HttpStatus.NOT_FOUND),
    EXPENSE_BUDGET_NOT_FOUND("EXP_003", "error.notfound.expense.budget", HttpStatus.NOT_FOUND),
    EXPENSE_TEMPLATE_NOT_FOUND("EXP_004", "error.notfound.expense.template", HttpStatus.NOT_FOUND),
    RECURRING_TRANSACTION_NOT_FOUND("EXP_005", "error.notfound.recurring.transaction", HttpStatus.NOT_FOUND),

    // Asset
    ASSET_NOT_FOUND("ASSET_001", "error.notfound.asset", HttpStatus.NOT_FOUND),
    ASSET_TRANSFER_NOT_FOUND("ASSET_002", "error.notfound.asset.transfer", HttpStatus.NOT_FOUND),
    ASSET_TRANSFER_SAME_ASSET("ASSET_003", "error.asset.transfer.same", HttpStatus.BAD_REQUEST),

    // Dutch Pay
    DUTCH_PAY_NOT_FOUND("DUTCH_001", "error.notfound.dutch.pay", HttpStatus.NOT_FOUND),
    DUTCH_PAY_PARTICIPANT_NOT_FOUND("DUTCH_002", "error.notfound.dutch.pay.participant", HttpStatus.NOT_FOUND),

    // Notification
    NOTIFICATION_NOT_FOUND("NOTI_001", "error.notfound.notification", HttpStatus.NOT_FOUND),

    // Group
    GROUP_NOT_FOUND("GROUP_001", "error.notfound.group", HttpStatus.NOT_FOUND),
    GROUP_MEMBER_NOT_FOUND("GROUP_002", "error.notfound.group.member", HttpStatus.NOT_FOUND),
    EVENT_COMMENT_NOT_FOUND("GROUP_003", "error.notfound.event.comment", HttpStatus.NOT_FOUND),

    // File
    FILE_NOT_FOUND("FILE_001", "error.notfound.file", HttpStatus.NOT_FOUND),

    // Album
    ALBUM_NOT_FOUND("ALBUM_001", "error.notfound.album", HttpStatus.NOT_FOUND),
    ALBUM_PHOTO_NOT_FOUND("ALBUM_002", "error.notfound.album.photo", HttpStatus.NOT_FOUND),
    ;

    private final String code;
    private final String messageKey;
    private final HttpStatus httpStatus;
}
