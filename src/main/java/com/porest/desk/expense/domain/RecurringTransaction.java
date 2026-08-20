package com.porest.desk.expense.domain;

import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.common.domain.AuditingFieldsWithIp;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.expense.type.RecurringFrequency;
import com.porest.desk.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "recurring_transaction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecurringTransaction extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_row_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_row_id")
    private ExpenseCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_row_id")
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_expense_row_id")
    private Expense sourceExpense;

    @Enumerated(EnumType.STRING)
    @Column(name = "expense_type", nullable = false, length = 20)
    private ExpenseType expenseType;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "auto_log", nullable = false, length = 1)
    private YNType autoLog;

    @Enumerated(EnumType.STRING)
    @Column(name = "notify_day_before", nullable = false, length = 1)
    private YNType notifyDayBefore;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "merchant", length = 100)
    private String merchant;

    @Column(name = "payment_method", length = 30)
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 20)
    private RecurringFrequency frequency;

    @Column(name = "interval_value", nullable = false)
    private Integer intervalValue;

    @Column(name = "day_of_week")
    private Integer dayOfWeek;

    @Column(name = "day_of_month")
    private Integer dayOfMonth;

    /**
     * [userClock] 실행분을 만들 시각. 사용자가 고른 벽시계라 타임존 변환 금지.
     *
     * 예전에는 스케줄러가 09:00 을 하드코딩했다. 기존 행은 DDL 기본값이 '09:00:00' 이라
     * 동작이 바뀌지 않는다.
     */
    @Column(name = "execution_time", nullable = false)
    private LocalTime executionTime;

    /** [userClock] 사용자·업무가 정한 벽시계 — 타임존 변환 금지(자정 근처 날짜가 밀린다) */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** [userClock] 사용자·업무가 정한 벽시계 — 타임존 변환 금지(자정 근처 날짜가 밀린다) */
    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "max_occurrences")
    private Integer maxOccurrences;

    @Column(name = "executed_count", nullable = false)
    private Integer executedCount;

    /** [serviceClock] 배치가 서비스 기준(Asia/Seoul)으로 산출 */
    @Column(name = "next_execution_date", nullable = false)
    private LocalDate nextExecutionDate;

    /** [UTC] 시스템 기록 시각 — 저장·비교 UTC, 표시할 때만 사용자 타임존 변환 */
    @Column(name = "last_executed_at")
    private LocalDateTime lastExecutedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_active", nullable = false, length = 1)
    private YNType isActive;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    /** 실행 시각을 주지 않았을 때의 기본값 — 컬럼 DDL 기본값과 같아야 한다. */
    public static final LocalTime DEFAULT_EXECUTION_TIME = LocalTime.of(9, 0);

    public static RecurringTransaction createRecurring(User user, ExpenseCategory category, Asset asset,
                                                        Expense sourceExpense,
                                                        ExpenseType expenseType, Long amount, String description,
                                                        String merchant, String paymentMethod,
                                                        RecurringFrequency frequency, Integer intervalValue,
                                                        Integer dayOfWeek, Integer dayOfMonth,
                                                        LocalTime executionTime,
                                                        LocalDate startDate, LocalDate endDate,
                                                        Integer maxOccurrences,
                                                        LocalDate nextExecutionDate,
                                                        Boolean autoLog, Boolean notifyDayBefore) {
        RecurringTransaction recurring = new RecurringTransaction();
        recurring.user = user;
        recurring.category = category;
        recurring.asset = asset;
        recurring.sourceExpense = sourceExpense;
        recurring.expenseType = expenseType;
        recurring.amount = amount;
        recurring.description = description;
        recurring.merchant = merchant;
        recurring.paymentMethod = paymentMethod;
        recurring.frequency = frequency;
        recurring.intervalValue = intervalValue != null ? intervalValue : 1;
        recurring.dayOfWeek = dayOfWeek;
        recurring.dayOfMonth = dayOfMonth;
        // 안 주면 예전 고정값(09:00)을 그대로 쓴다 — 기존 클라이언트가 깨지지 않는다.
        recurring.executionTime = executionTime != null ? executionTime : DEFAULT_EXECUTION_TIME;
        recurring.startDate = startDate;
        recurring.endDate = endDate;
        recurring.maxOccurrences = maxOccurrences;
        recurring.executedCount = 0;
        recurring.nextExecutionDate = nextExecutionDate;
        recurring.autoLog = (autoLog == null || autoLog) ? YNType.Y : YNType.N;
        recurring.notifyDayBefore = (notifyDayBefore == null || notifyDayBefore) ? YNType.Y : YNType.N;
        recurring.isActive = YNType.Y;
        recurring.isDeleted = YNType.N;
        return recurring;
    }

    /** 카테고리만 교체 — 카테고리 재편 시 일괄 이동용. */
    public void changeCategory(ExpenseCategory category) {
        this.category = category;
    }

    public void updateRecurring(ExpenseCategory category, Asset asset, ExpenseType expenseType,
                                 Long amount, String description, String merchant, String paymentMethod,
                                 RecurringFrequency frequency, Integer intervalValue,
                                 Integer dayOfWeek, Integer dayOfMonth,
                                 LocalTime executionTime,
                                 LocalDate startDate, LocalDate endDate, Integer maxOccurrences,
                                 LocalDate nextExecutionDate,
                                 Boolean autoLog, Boolean notifyDayBefore) {
        this.category = category;
        this.asset = asset;
        this.expenseType = expenseType;
        this.amount = amount;
        this.description = description;
        this.merchant = merchant;
        this.paymentMethod = paymentMethod;
        this.frequency = frequency;
        this.intervalValue = intervalValue;
        this.dayOfWeek = dayOfWeek;
        this.dayOfMonth = dayOfMonth;
        // null 이면 기존 값을 유지한다 — 시간을 안 보내는 클라이언트가 09:00 으로 되돌리지 않게.
        if (executionTime != null) this.executionTime = executionTime;
        this.startDate = startDate;
        this.endDate = endDate;
        this.maxOccurrences = maxOccurrences;
        this.nextExecutionDate = nextExecutionDate;
        if (autoLog != null) this.autoLog = autoLog ? YNType.Y : YNType.N;
        if (notifyDayBefore != null) this.notifyDayBefore = notifyDayBefore ? YNType.Y : YNType.N;
    }

    public void markExecuted(LocalDateTime executedAt, LocalDate nextDate) {
        this.lastExecutedAt = executedAt;
        this.nextExecutionDate = nextDate;
        this.executedCount = (this.executedCount == null ? 0 : this.executedCount) + 1;
        // 횟수 지정 종료: 목표 횟수 도달 시 자동 비활성화
        if (this.maxOccurrences != null && this.executedCount >= this.maxOccurrences) {
            this.isActive = YNType.N;
        }
    }

    public void toggleActive() {
        this.isActive = this.isActive == YNType.Y ? YNType.N : YNType.Y;
    }

    public void deleteRecurring() {
        this.isDeleted = YNType.Y;
    }
}
