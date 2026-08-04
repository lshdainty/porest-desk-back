package com.porest.desk.expense.domain;

import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.calendar.domain.CalendarEvent;
import com.porest.desk.common.domain.AuditingFieldsWithIp;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.todo.domain.Todo;
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

import java.time.LocalDateTime;

@Entity
@Table(name = "expense")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Expense extends AuditingFieldsWithIp {
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

    @Enumerated(EnumType.STRING)
    @Column(name = "expense_type", nullable = false, length = 20)
    private ExpenseType expenseType;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "description", length = 500)
    private String description;

    /** [userClock] 사용자·업무가 정한 벽시계 — 타임존 변환 금지(자정 근처 날짜가 밀린다) */
    @Column(name = "expense_date", nullable = false)
    private LocalDateTime expenseDate;

    @Column(name = "merchant", length = 100)
    private String merchant;

    @Column(name = "payment_method", length = 30)
    private String paymentMethod;

    /**
     * 할부 개월 (null·1 = 일시불). 신용카드 결제에만 의미가 있다.
     *
     * <p>카드 청구는 결제일 기준 전월 사용분을 한 번에 잡는데, 할부는 그 금액이 N개월에 나뉘어
     * 청구된다. 이 값이 있으면 청구 회차 계산이 거래 금액을 N등분해 회차별로 잡는다.
     * 통계·예산은 거래 시점에 전액을 인식한다(가계부 관점의 지출 시점은 결제한 날이다).
     */
    @Column(name = "installment_months")
    private Integer installmentMonths;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calendar_event_row_id")
    private CalendarEvent calendarEvent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "todo_row_id")
    private Todo todo;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static Expense createExpense(User user, ExpenseCategory category, Asset asset,
                                        ExpenseType expenseType, Long amount, String description,
                                        LocalDateTime expenseDate, String merchant, String paymentMethod,
                                        Integer installmentMonths) {
        Expense expense = new Expense();
        expense.user = user;
        expense.category = category;
        expense.asset = asset;
        expense.expenseType = expenseType;
        expense.amount = amount;
        expense.description = description;
        expense.expenseDate = expenseDate;
        expense.merchant = merchant;
        expense.paymentMethod = paymentMethod;
        expense.installmentMonths = normalizeInstallment(installmentMonths);
        expense.isDeleted = YNType.N;
        return expense;
    }

    /**
     * 할부 개월 정규화 — 1 이하·null 은 일시불(null)로 통일한다.
     * "1개월 할부"는 일시불과 같아서 두 표기가 섞이면 청구 계산이 갈린다.
     */
    private static Integer normalizeInstallment(Integer months) {
        return (months == null || months <= 1) ? null : months;
    }

    /** 일시불이 아닌가. */
    public boolean isInstallment() {
        return installmentMonths != null && installmentMonths > 1;
    }

    /**
     * 할부 n회차(1-base)에 청구될 금액.
     *
     * <p>나누어떨어지지 않는 금액은 <b>첫 회차에 나머지를 몰아</b> 합이 원금과 정확히 맞게 한다
     * (국내 카드사 관행). 예: 1,000,000원 3개월 → 333,334 / 333,333 / 333,333.
     *
     * @param seq 1..installmentMonths. 범위를 벗어나면 0.
     */
    public long installmentAmountAt(int seq) {
        if (!isInstallment()) {
            return seq == 1 ? amount : 0L;
        }
        if (seq < 1 || seq > installmentMonths) {
            return 0L;
        }
        long base = amount / installmentMonths;
        long remainder = amount % installmentMonths;
        return seq == 1 ? base + remainder : base;
    }

    /** 카테고리만 교체 — 카테고리 재편 시 일괄 이동용(다른 값은 건드리지 않는다). */
    public void changeCategory(ExpenseCategory category) {
        this.category = category;
    }

    public void updateExpense(ExpenseCategory category, Asset asset, ExpenseType expenseType,
                              Long amount, String description, LocalDateTime expenseDate,
                              String merchant, String paymentMethod, Integer installmentMonths) {
        this.installmentMonths = normalizeInstallment(installmentMonths);
        this.category = category;
        this.asset = asset;
        this.expenseType = expenseType;
        this.amount = amount;
        this.description = description;
        this.expenseDate = expenseDate;
        this.merchant = merchant;
        this.paymentMethod = paymentMethod;
    }

    public void setCalendarEvent(CalendarEvent calendarEvent) {
        this.calendarEvent = calendarEvent;
    }

    public void setTodo(Todo todo) {
        this.todo = todo;
    }

    public void deleteExpense() {
        this.isDeleted = YNType.Y;
    }
}
