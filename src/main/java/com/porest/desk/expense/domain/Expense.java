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

import java.math.BigDecimal;
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

    /**
     * 환불 원거래 행 아이디 (null = 환불 아님).
     *
     * <p>환불·취소는 INCOME 으로 기록하는데, 그대로 두면 수입 통계가 부풀려진다
     * (5만원 옷을 사고 환불하면 지출 5만 + 수입 5만). 이 값이 있으면 수입이 아니라
     * <b>지출 상계</b>로 집계한다 — 위 예에서 그 달 지출은 0, 수입도 0 이 된다.
     *
     * <p>FK 를 걸지 않는다: 원거래가 soft delete 되어도 환불 기록은 남아야 하고,
     * 가져오기로 들어온 행처럼 원거래가 없을 수도 있다.
     */
    @Column(name = "refund_of_expense_row_id")
    private Long refundOfExpenseRowId;

    /**
     * 자동 생성 출처 — 시스템이 계산해 만든 거래다. null 이면 사용자가 직접 만든 것.
     *
     * <p>매도 실현손익(TRADE_REALIZED)·대출 이자(TRANSFER_INTEREST)는 원 거래에서 파생된
     * 금액이라 잔액 이력을 따로 남기지 않는다. 그런데 가계부에 그대로 보이고 수정도 됐다 —
     * 사용자가 카테고리를 달고 저장하는 순간 recordExpense 가 돌아 잔액에 flow 가 새로
     * 생겼다(카테고리 하나 달았는데 예수금이 실현손익만큼 늘어난다).
     *
     * <p>그래서 금액·자산·유형·일자는 잠그고 카테고리·메모·거래처는 연다. 전부 막으면
     * 미분류로 남을 수밖에 없어 오히려 불편하다.
     *
     * <p>paymentMethod 문자열로 판정하지 않는 이유 — 그건 사용자가 바꿀 수 있다.
     */
    @Column(name = "auto_source", length = 30)
    private String autoSource;

    /**
     * 원 통화 금액 (해외 결제 시). null 이면 원화 결제 — {@code amount} 가 곧 결제액이다.
     *
     * <p>{@code amount}(원화)만 남기면 "얼마짜리를 어떤 환율로 샀는지" 가 사라져
     * 카드사 청구 환율과 대사할 수 없다. 잔액·통계는 종전대로 {@code amount} 를 쓴다.
     */
    @Column(name = "original_amount", precision = 18, scale = 4)
    private BigDecimal originalAmount;

    /** 원 통화 (ISO 4217, 예: USD). null 이면 원화 결제. */
    @Column(name = "original_currency", length = 10)
    private String originalCurrency;

    /** 적용 환율 (원 통화 1단위당 원화). {@code amount ≈ originalAmount × exchangeRate}. */
    @Column(name = "exchange_rate", precision = 18, scale = 6)
    private BigDecimal exchangeRate;

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
                                        Integer installmentMonths, Long refundOfExpenseRowId,
                                        BigDecimal originalAmount, String originalCurrency,
                                        BigDecimal exchangeRate) {
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
        expense.refundOfExpenseRowId = refundOfExpenseRowId;
        expense.applyForeignCurrency(originalAmount, originalCurrency, exchangeRate);
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

    /**
     * 환불인가 — INCOME 이면서 원거래가 지정된 건.
     *
     * <p>원거래 없이 INCOME 이면 그냥 수입이다(급여·이자 등).
     */
    /** 시스템이 만든 거래인가 — 금액·자산·유형·일자를 사용자가 못 고친다. */
    public boolean isAutoGenerated() {
        return autoSource != null;
    }

    /** 자동 생성 표시 — 매도 실현손익·대출 이자처럼 원 거래에서 파생된 금액에만 붙인다. */
    public void markAutoGenerated(String source) {
        this.autoSource = source;
    }

    /** 사용자가 고칠 수 있는 부분만 반영 — 금액·자산·유형·일자는 원 거래가 정한다. */
    public void updateEditableFields(ExpenseCategory category, String description, String merchant) {
        this.category = category;
        this.description = description;
        this.merchant = merchant;
    }

    public boolean isRefund() {
        return refundOfExpenseRowId != null && expenseType == ExpenseType.INCOME;
    }

    /**
     * 수입 집계에 더할 금액 — 환불은 수입이 아니므로 0.
     */
    public long incomeContribution() {
        return (expenseType == ExpenseType.INCOME && !isRefund()) ? amount : 0L;
    }

    /**
     * 지출 집계에 더할 금액 — 환불은 <b>음수</b>로 상계한다.
     *
     * <p>지출 50,000 + 환불 50,000 → 합 0. 부분 환불(20,000)이면 30,000 이 남는다.
     */
    public long expenseContribution() {
        if (isRefund()) {
            return -amount;
        }
        return expenseType == ExpenseType.EXPENSE ? amount : 0L;
    }

    /**
     * 외화 결제 정보 — 셋이 함께 있어야 의미가 있다. 통화가 없거나 원화면 전부 비운다
     * (반쪽만 남으면 "$? 를 환율 1,400 에" 같은 해석 불가한 기록이 생긴다).
     */
    private void applyForeignCurrency(BigDecimal originalAmount, String originalCurrency,
                                      BigDecimal exchangeRate) {
        boolean foreign = originalCurrency != null
            && !originalCurrency.isBlank()
            && !"KRW".equalsIgnoreCase(originalCurrency)
            && originalAmount != null
            && originalAmount.signum() > 0;
        this.originalAmount = foreign ? originalAmount : null;
        this.originalCurrency = foreign ? originalCurrency.toUpperCase() : null;
        this.exchangeRate = foreign ? exchangeRate : null;
    }

    /** 해외 결제인가. */
    public boolean isForeignCurrency() {
        return originalCurrency != null;
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
                              String merchant, String paymentMethod, Integer installmentMonths,
                              Long refundOfExpenseRowId, BigDecimal originalAmount,
                              String originalCurrency, BigDecimal exchangeRate) {
        this.installmentMonths = normalizeInstallment(installmentMonths);
        this.refundOfExpenseRowId = refundOfExpenseRowId;
        applyForeignCurrency(originalAmount, originalCurrency, exchangeRate);
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
