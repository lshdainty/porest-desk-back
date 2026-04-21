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
    USER_PASSWORD_CHANGE_FAILED("USER_003", "error.user.password.change.failed", HttpStatus.BAD_REQUEST),

    // SSO
    SSO_SERVICE_ERROR("SSO_001", "error.sso.service.error", HttpStatus.BAD_GATEWAY),

    // Todo
    TODO_NOT_FOUND("TODO_001", "error.notfound.todo", HttpStatus.NOT_FOUND),
    TODO_PROJECT_NOT_FOUND("TODO_002", "error.notfound.todo.project", HttpStatus.NOT_FOUND),
    TODO_TAG_NOT_FOUND("TODO_003", "error.notfound.todo.tag", HttpStatus.NOT_FOUND),
    TODO_ACCESS_DENIED("TODO_004", "error.todo.access.denied", HttpStatus.FORBIDDEN),

    // Calendar
    CALENDAR_EVENT_NOT_FOUND("CAL_001", "error.notfound.calendar.event", HttpStatus.NOT_FOUND),
    CALENDAR_INVALID_DATE_RANGE("CAL_002", "error.calendar.invalid.date.range", HttpStatus.BAD_REQUEST),
    EVENT_LABEL_NOT_FOUND("CAL_003", "error.notfound.event.label", HttpStatus.NOT_FOUND),
    EVENT_REMINDER_NOT_FOUND("CAL_004", "error.notfound.event.reminder", HttpStatus.NOT_FOUND),
    USER_CALENDAR_NOT_FOUND("CAL_005", "error.notfound.user.calendar", HttpStatus.NOT_FOUND),
    USER_CALENDAR_DEFAULT_DELETE("CAL_006", "error.calendar.default.delete", HttpStatus.BAD_REQUEST),
    CALENDAR_ACCESS_DENIED("CAL_007", "error.calendar.access.denied", HttpStatus.FORBIDDEN),
    CALENDAR_EVENT_ACCESS_DENIED("CAL_008", "error.calendar.event.access.denied", HttpStatus.FORBIDDEN),
    EVENT_LABEL_ACCESS_DENIED("CAL_009", "error.event.label.access.denied", HttpStatus.FORBIDDEN),

    // Memo
    MEMO_NOT_FOUND("MEMO_001", "error.notfound.memo", HttpStatus.NOT_FOUND),
    MEMO_FOLDER_NOT_FOUND("MEMO_002", "error.notfound.memo.folder", HttpStatus.NOT_FOUND),
    MEMO_ACCESS_DENIED("MEMO_003", "error.memo.access.denied", HttpStatus.FORBIDDEN),

    // Expense
    EXPENSE_NOT_FOUND("EXP_001", "error.notfound.expense", HttpStatus.NOT_FOUND),
    EXPENSE_CATEGORY_NOT_FOUND("EXP_002", "error.notfound.expense.category", HttpStatus.NOT_FOUND),
    EXPENSE_BUDGET_NOT_FOUND("EXP_003", "error.notfound.expense.budget", HttpStatus.NOT_FOUND),
    EXPENSE_TEMPLATE_NOT_FOUND("EXP_004", "error.notfound.expense.template", HttpStatus.NOT_FOUND),
    RECURRING_TRANSACTION_NOT_FOUND("EXP_005", "error.notfound.recurring.transaction", HttpStatus.NOT_FOUND),
    EXPENSE_CATEGORY_MAX_DEPTH("EXP_006", "error.expense.category.max.depth", HttpStatus.BAD_REQUEST),
    EXPENSE_CATEGORY_HAS_CHILDREN("EXP_007", "error.expense.category.has.children", HttpStatus.BAD_REQUEST),
    EXPENSE_CATEGORY_TYPE_MISMATCH("EXP_008", "error.expense.category.type.mismatch", HttpStatus.BAD_REQUEST),
    EXPENSE_CATEGORY_NOT_LEAF("EXP_009", "error.expense.category.not.leaf", HttpStatus.BAD_REQUEST),
    EXPENSE_ACCESS_DENIED("EXP_010", "error.expense.access.denied", HttpStatus.FORBIDDEN),

    // Asset
    ASSET_NOT_FOUND("ASSET_001", "error.notfound.asset", HttpStatus.NOT_FOUND),
    ASSET_TRANSFER_NOT_FOUND("ASSET_002", "error.notfound.asset.transfer", HttpStatus.NOT_FOUND),
    ASSET_TRANSFER_SAME_ASSET("ASSET_003", "error.asset.transfer.same", HttpStatus.BAD_REQUEST),
    ASSET_ACCESS_DENIED("ASSET_004", "error.asset.access.denied", HttpStatus.FORBIDDEN),

    // Saving Goal
    SAVING_GOAL_NOT_FOUND("SAVING_001", "error.notfound.saving.goal", HttpStatus.NOT_FOUND),
    SAVING_GOAL_ACCESS_DENIED("SAVING_002", "error.saving.goal.access.denied", HttpStatus.FORBIDDEN),

    // Dutch Pay
    DUTCH_PAY_NOT_FOUND("DUTCH_001", "error.notfound.dutch.pay", HttpStatus.NOT_FOUND),
    DUTCH_PAY_PARTICIPANT_NOT_FOUND("DUTCH_002", "error.notfound.dutch.pay.participant", HttpStatus.NOT_FOUND),
    DUTCHPAY_ACCESS_DENIED("DUTCH_003", "error.dutchpay.access.denied", HttpStatus.FORBIDDEN),

    // Notification
    NOTIFICATION_NOT_FOUND("NOTI_001", "error.notfound.notification", HttpStatus.NOT_FOUND),
    NOTIFICATION_ACCESS_DENIED("NOTI_002", "error.notification.access.denied", HttpStatus.FORBIDDEN),

    // Group
    GROUP_NOT_FOUND("GROUP_001", "error.notfound.group", HttpStatus.NOT_FOUND),
    GROUP_MEMBER_NOT_FOUND("GROUP_002", "error.notfound.group.member", HttpStatus.NOT_FOUND),
    EVENT_COMMENT_NOT_FOUND("GROUP_003", "error.notfound.event.comment", HttpStatus.NOT_FOUND),
    EVENT_COMMENT_ACCESS_DENIED("GROUP_004", "error.event.comment.access.denied", HttpStatus.FORBIDDEN),
    GROUP_TYPE_NOT_FOUND("GROUP_005", "error.notfound.group.type", HttpStatus.NOT_FOUND),
    GROUP_ACCESS_DENIED("GROUP_006", "error.group.access.denied", HttpStatus.FORBIDDEN),

    // Holiday
    HOLIDAY_NOT_FOUND("HOLIDAY_001", "error.notfound.holiday", HttpStatus.NOT_FOUND),
    HOLIDAY_DUPLICATE("HOLIDAY_002", "error.duplicate.holiday", HttpStatus.CONFLICT),

    // File
    FILE_NOT_FOUND("FILE_001", "error.notfound.file", HttpStatus.NOT_FOUND),
    FILE_ACCESS_DENIED("FILE_002", "error.file.access.denied", HttpStatus.FORBIDDEN),
    FILE_INVALID_TYPE("FILE_003", "error.file.invalid.type", HttpStatus.BAD_REQUEST),
    FILE_TOO_LARGE("FILE_004", "error.file.too.large", HttpStatus.BAD_REQUEST),

    // Card
    CARD_CATALOG_NOT_FOUND("CARD_001", "error.notfound.card.catalog", HttpStatus.NOT_FOUND),
    CARD_BENEFIT_MAPPING_NOT_FOUND("CARD_002", "error.notfound.card.benefit.mapping", HttpStatus.NOT_FOUND),
    CARD_BENEFIT_MAPPING_ACCESS_DENIED("CARD_003", "error.card.benefit.mapping.access.denied", HttpStatus.FORBIDDEN),
    CARD_BENEFIT_MAPPING_DUPLICATE("CARD_004", "error.duplicate.card.benefit.mapping", HttpStatus.CONFLICT),
    ;

    private final String code;
    private final String messageKey;
    private final HttpStatus httpStatus;
}
