package com.porest.desk.card.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.InvalidValueException;
import com.porest.core.time.ServiceClock;
import com.porest.core.time.UserClock;
import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.asset.service.AssetBalanceHistoryService;
import com.porest.desk.asset.service.AssetService;
import com.porest.desk.asset.service.dto.AssetServiceDto;
import com.porest.desk.asset.type.AssetType;
import com.porest.desk.card.domain.CardBilling;
import com.porest.desk.card.repository.CardBillingRepository;
import com.porest.desk.card.service.dto.CardPaymentServiceDto;
import com.porest.desk.card.type.BillingStatus;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.common.patch.Patch;
import com.porest.desk.dutchpay.domain.DutchPay;
import com.porest.desk.dutchpay.type.SplitMethod;
import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.desk.expense.domain.RecurringTransaction;
import com.porest.desk.expense.repository.ExpenseCategoryRepository;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.repository.ExpenseSplitRepository;
import com.porest.desk.expense.service.ExpenseService;
import com.porest.desk.expense.service.ExpenseSplitService;
import com.porest.desk.expense.service.dto.ExpenseServiceDto;
import com.porest.desk.expense.service.dto.ExpenseSplitServiceDto;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.expense.type.RecurringFrequency;
import com.porest.desk.expense.type.TxKind;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * "결제가 끝난 회차는 기록만"(2026-09-21 확정 D1~D16) — 진짜 트랜잭션·진짜 질의로 끝까지 돌린다.
 *
 * <p>결제일 12일 카드, 기본 오늘은 9/14. 8월 회차(8/1~8/31)는 9/12 에 결제돼 닫혔고(당일 포함, D2),
 * 9월 회차는 10/12 에 나간다. 닫힌 회차의 변경은 기록만 바뀐다 — 통장·카드 잔액·다음 청구가 그대로다(D1).
 * 돈이 움직이는 건 열린 회차에서 미리 낸 돈이 남을 때의 환급 하나뿐이다(D3).
 *
 * <p>돈의 규칙은 여러 서비스(거래 저장 → 회차 판정 → 이체·상계 → 잔액 집계)를 거쳐야 드러나서
 * mock 으로는 잠글 수 없다. 오늘은 {@link UserClock}·{@link ServiceClock} 을 함께 고정해 정한다 —
 * {@code now()±N일} 로 쓰면 달이 바뀌는 날 회차가 어긋난다.
 *
 * <p>테스트가 트랜잭션을 들지 않는다 — 서비스마다 자기 트랜잭션으로 커밋되는 실제 모양 그대로다.
 * 케이스마다 사용자를 새로 만들어 서로 안 밟게 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ClosedCycleRulesScenarioTest {

    private static final LocalDateTime FAR = LocalDateTime.of(2100, 1, 1, 0, 0);
    private static final LocalDate AUG = LocalDate.of(2026, 8, 1);
    private static final LocalDate JUL = LocalDate.of(2026, 7, 1);
    private static final LocalDate SEP = LocalDate.of(2026, 9, 1);

    @MockitoBean private UserClock userClock;
    @MockitoBean private ServiceClock serviceClock;

    @Autowired private ExpenseService expenseService;
    @Autowired private ExpenseSplitService expenseSplitService;
    @Autowired private CardPaymentService cardPaymentService;
    @Autowired private AssetService assetService;
    @Autowired private AssetRepository assetRepository;
    @Autowired private AssetBalanceHistoryService balanceHistoryService;
    @Autowired private CardBillingRepository cardBillingRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private ExpenseSplitRepository expenseSplitRepository;
    @Autowired private ExpenseCategoryRepository expenseCategoryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionTemplate tx;
    @Autowired private EntityManager entityManager;

    private User user;
    private Long categoryRowId;
    private Asset bank;
    private Asset card;

    @BeforeEach
    void setUp() {
        given(userClock.zoneOf(any())).willReturn(ZoneId.of("Asia/Seoul"));
        given(userClock.zoneOfTimezone(any())).willReturn(ZoneId.of("Asia/Seoul"));
        given(serviceClock.zone()).willReturn(ZoneId.of("Asia/Seoul"));
        user = newUser();
        categoryRowId = newCategory("식비", ExpenseType.EXPENSE);
        bank = newAsset("통장", AssetType.BANK_ACCOUNT, null, null);
        card = newAsset("카드", AssetType.CREDIT_CARD, 12, bank);
        registeredOn(card, LocalDate.of(2026, 6, 1));
    }

    // === 픽스처 =================================================================

    private User newUser() {
        String id = "cc" + UUID.randomUUID().toString().substring(0, 8);
        return tx.execute(s -> userRepository.save(User.createUser(null, id, "테스터", id + "@porest.com")));
    }

    private Long newCategory(String name, ExpenseType type) {
        return tx.execute(s -> expenseCategoryRepository.save(ExpenseCategory.createCategory(
            user, name, null, null, type, null)).getRowId());
    }

    private Asset newAsset(String name, AssetType type, Integer paymentDay, Asset paymentAsset) {
        return tx.execute(s -> assetRepository.save(Asset.createAsset(
            user, name, type, 0L, "KRW", null, null, null, null, 0, YNType.Y,
            null, type == AssetType.CREDIT_CARD ? 10_000_000L : null, paymentDay, paymentAsset)));
    }

    /** 카드 등록일 — 등록 전 회차(R4) 판정의 기준. 감사 컬럼이라 직접 고친다. */
    private void registeredOn(Asset asset, LocalDate date) {
        tx.executeWithoutResult(s -> entityManager.createNativeQuery(
                "UPDATE asset SET create_at = :at WHERE row_id = :id")
            .setParameter("at", date.atStartOfDay())
            .setParameter("id", asset.getRowId())
            .executeUpdate());
    }

    /** 사용자 시계와 서비스(서울) 시계를 같은 날로 — 닫힌 회차 판정은 서비스 시계를 본다(D11). */
    private void today(LocalDate d) {
        given(userClock.today(any())).willReturn(d);
        given(userClock.now(any())).willReturn(d.atTime(12, 0));
        given(userClock.todayIn(any())).willReturn(d);
        given(userClock.nowIn(any())).willReturn(d.atTime(12, 0));
        given(serviceClock.today()).willReturn(d);
        given(serviceClock.now()).willReturn(d.atTime(12, 0));
    }

    /** 그날 자정 배치 — 결제일이면 회차 결제, 아니면 과납 스윕만. */
    private void midnight(LocalDate d) {
        today(d);
        cardPaymentService.processDueCardPayments(d);
    }

    private Long spend(LocalDate on, LocalDate date, long amount) {
        return spend(on, date, amount, null, card);
    }

    private Long spend(LocalDate on, LocalDate date, long amount, Integer months, Asset asset) {
        today(on);
        return expenseService.createExpense(createCmd(date, amount, months, asset, ExpenseType.EXPENSE,
            categoryRowId)).rowId();
    }

    private ExpenseServiceDto.CreateCommand createCmd(LocalDate date, long amount, Integer months, Asset asset,
                                                      ExpenseType type, Long category) {
        return new ExpenseServiceDto.CreateCommand(
            user.getRowId(), category, asset.getRowId(), type, amount, "테스트",
            date.atTime(10, 0), "가맹점", null, months, null, null, null, null, null);
    }

    private ExpenseServiceDto.ExpenseInfo moveTo(Long expenseId, LocalDate date) {
        return expenseService.updateExpense(expenseId, user.getRowId(), new ExpenseServiceDto.UpdateCommand(
            Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(),
            Patch.set(date.atTime(10, 0)), Patch.absent(), Patch.absent(), Patch.absent(),
            Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(), null));
    }

    private ExpenseServiceDto.ExpenseInfo replace(Long expenseId, LocalDate date, long amount) {
        return expenseService.replaceExpense(expenseId, user.getRowId(),
            createCmd(date, amount, null, card, ExpenseType.EXPENSE, categoryRowId), null);
    }

    private long balance(Asset asset) {
        return tx.execute(s -> balanceHistoryService.balanceAt(
            assetRepository.findById(asset.getRowId()).orElseThrow(), FAR).total());
    }

    private LocalDate markOf(Long expenseId) {
        return tx.execute(s -> expenseRepository.findById(expenseId).orElseThrow().getCardSettledThrough());
    }

    private CardPaymentServiceDto.CardBillingInfo billing() {
        return cardPaymentService.getCardBilling(card.getRowId(), user.getRowId());
    }

    private CardPaymentServiceDto.ClosedCycle closed(LocalDate periodStart) {
        return billing().closedCycles().stream()
            .filter(c -> c.periodStart().equals(periodStart))
            .findFirst().orElse(null);
    }

    private List<CardBilling> rows(BillingStatus status) {
        return rows(card, status);
    }

    private List<CardBilling> rows(Asset asset, BillingStatus status) {
        return cardBillingRepository.findByCardAssetRowId(asset.getRowId()).stream()
            .filter(b -> b.getIsDeleted() == YNType.N && b.getStatus() == status)
            .toList();
    }

    private ExpenseServiceDto.ExpenseInfo info(Long expenseId) {
        return expenseService.getExpenses(user.getRowId(), null, null, null, null, null).stream()
            .filter(e -> e.rowId().equals(expenseId)).findFirst().orElseThrow();
    }

    private static void rejectedWith(Runnable call, DeskErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOf(InvalidValueException.class)
            .hasMessage(code.getMessageKey());
    }

    /** 8/5 에 쓴 100,000 을 9/12 에 앱이 결제했다 — 대부분 케이스의 출발점. 오늘은 9/14 로 둔다. */
    private Long augustPaid() {
        Long id = spend(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 8, 5), 100_000L);
        midnight(LocalDate.of(2026, 9, 12));
        assertThat(balance(bank)).isEqualTo(-100_000L);
        assertThat(balance(card)).isZero();
        today(LocalDate.of(2026, 9, 14));
        return id;
    }

    /** 결제 뒤 자정 스윕이 돌아도 통장이 그대로인지 — 카드가 양수로 뜨면 스윕이 통장으로 보낸다. */
    private void assertNoMoneyMovesOvernight(long bankBalance) {
        midnight(LocalDate.of(2026, 9, 15));
        assertThat(balance(bank)).as("다음 자정 스윕이 돌려주지 않는다").isEqualTo(bankBalance);
        assertThat(balance(card)).isZero();
    }

    // === 저장 — U1·R2·R4·R5 ==========================================================

    @Nested
    @DisplayName("저장")
    class Save {

        @Test
        @DisplayName("케이스 1 — 열린 회차 거래는 그대로 청구되고 잠기지 않는다")
        void openCycleIsBilled() {
            augustPaid();
            Long id = spend(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 13), 30_000L);

            assertThat(markOf(id)).isNull();
            assertThat(balance(card)).isEqualTo(-30_000L);
            assertThat(billing().upcomingAmount()).isEqualTo(30_000L);
            assertThat(balance(bank)).isEqualTo(-100_000L);
            assertThat(info(id).moneyLocked()).isFalse();
        }

        @Test
        @DisplayName("케이스 2 — 결제 끝난 회차에 소급 입력하면 기록용: 통장·다음 청구 그대로, 카드 빚도 안 남는다")
        void closedCycleBecomesRecordOnly() {
            augustPaid();
            Long id = spend(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 8, 20), 25_000L);

            assertThat(markOf(id)).isEqualTo(LocalDate.of(2026, 8, 31));
            assertThat(balance(card)).as("현실에선 이미 갚은 돈 — 이체 없이 상계").isZero();
            assertThat(balance(bank)).isEqualTo(-100_000L);
            assertThat(billing().upcomingAmount()).isZero();
            var aug = closed(AUG);
            assertThat(aug.paidAmount()).isEqualTo(100_000L);
            assertThat(aug.recordedAmount()).as("머리 금액 = 지금 기록 합(D10)").isEqualTo(125_000L);
            assertThat(aug.recordedOnlyAmount()).isEqualTo(25_000L);
            assertThat(aug.preRegistration()).isFalse();
            assertThat(aug.refundableUntil()).as("닫힌 회차는 돌려주지 않는다 — 기한이 없다").isNull();
            assertThat(info(id).moneyLocked()).isTrue();
            assertThat(info(id).recordOnlyAmount()).isEqualTo(25_000L);

            assertNoMoneyMovesOvernight(-100_000L);
        }

        @Test
        @DisplayName("케이스 3 — 0원이라 건너뛴 회차도 기록 회차로 보인다")
        void skippedCycleShowsUp() {
            midnight(LocalDate.of(2026, 9, 12));
            assertThat(rows(BillingStatus.SKIPPED)).hasSize(1);

            spend(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 8, 20), 25_000L);

            var aug = closed(AUG);
            assertThat(aug).isNotNull();
            assertThat(aug.paidAmount()).isZero();
            assertThat(aug.recordedAmount()).isEqualTo(25_000L);
            assertThat(aug.recordedOnlyAmount()).isEqualTo(25_000L);
            assertThat(balance(card)).isZero();
        }

        @Test
        @DisplayName("케이스 4 — 카드 등록 전 회차는 기록 회차 + 주의 표시")
        void preRegistrationCycle() {
            registeredOn(card, LocalDate.of(2026, 9, 1));
            Long id = spend(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 7, 15), 40_000L);

            assertThat(markOf(id)).isEqualTo(LocalDate.of(2026, 7, 31));
            assertThat(balance(card)).isZero();
            var jul = closed(JUL);
            assertThat(jul.preRegistration()).as("7월분 결제일 8/12 < 등록일 9/1").isTrue();
            assertThat(jul.recordedOnlyAmount()).isEqualTo(40_000L);
        }

        @Test
        @DisplayName("케이스 5 — 결제일 당일 입력도 닫힌 회차: 기록용, 추가 결제 없음(D2·U7)")
        void paymentDayIsClosed() {
            augustPaid();
            today(LocalDate.of(2026, 9, 12));
            var preview = expenseService.cardSavePreview(user.getRowId(), card.getRowId(), 40_000L,
                LocalDate.of(2026, 8, 30).atTime(10, 0), null);
            assertThat(preview.sameDayExtraPayment()).isZero();
            assertThat(preview.newRecordAmount()).isEqualTo(40_000L);

            Long id = spend(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 8, 30), 40_000L);

            assertThat(markOf(id)).isEqualTo(LocalDate.of(2026, 8, 31));
            assertThat(balance(bank)).isEqualTo(-100_000L);
            assertThat(balance(card)).isZero();
            assertThat(rows(BillingStatus.COMPLETED)).extracting(CardBilling::getBillingAmount)
                .containsExactly(100_000L);
        }

        @Test
        @DisplayName("케이스 6 — 결제계좌 없는 카드도 소급 입력은 카드 빚으로 안 남는다")
        void noAccountCard() {
            Asset bare = newAsset("계좌없는카드", AssetType.CREDIT_CARD, 12, null);
            registeredOn(bare, LocalDate.of(2026, 6, 1));
            Long id = spend(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 8, 20), 25_000L, null, bare);

            assertThat(markOf(id)).isEqualTo(LocalDate.of(2026, 8, 31));
            assertThat(balance(bare)).isZero();
        }

        @Test
        @DisplayName("케이스 7 — 할부는 지난 회차분만 기록용, 남은 회차는 정상 청구(R5) · 닫힌 회차에 회차분 구성")
        void installmentSplitsByCycle() {
            Long id = spend(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 7, 10), 90_000L, 3, card);

            assertThat(markOf(id)).isEqualTo(LocalDate.of(2026, 8, 31));
            assertThat(balance(card)).as("3회차 30,000 만 빚").isEqualTo(-30_000L);
            assertThat(billing().upcomingAmount()).isEqualTo(30_000L);
            assertThat(closed(JUL).recordedOnlyAmount()).isEqualTo(30_000L);
            var aug = closed(AUG);
            assertThat(aug.recordedAmount()).isEqualTo(30_000L);
            assertThat(aug.installmentDues()).singleElement().satisfies(d -> {
                assertThat(d.sequence()).isEqualTo(2);
                assertThat(d.amount()).isEqualTo(30_000L);
                assertThat(d.recordOnly()).isTrue();
            });
            var row = info(id);
            assertThat(row.recordOnlyAmount()).as("일부만 기록용 — 행 배지는 안 붙는다(D10)").isEqualTo(60_000L);
            assertThat(row.moneyLocked()).as("결제일이 된 회차분이 있다(D12)").isTrue();
        }

        @Test
        @DisplayName("닫힌 회차 날짜의 카드 수입 — 적어도 지워도 카드 잔액·통장이 그대로(2-1 ⑤)")
        void closedCycleCardIncomeMovesNothing() {
            augustPaid();
            Long income = newCategory("캐시백", ExpenseType.INCOME);
            today(LocalDate.of(2026, 9, 14));
            Long id = expenseService.createExpense(
                createCmd(LocalDate.of(2026, 8, 20), 10_000L, null, card, ExpenseType.INCOME, income)).rowId();

            assertThat(balance(card)).as("닫힌 회차 수입은 상계로 받친다 — 양수로 뜨면 스윕이 통장으로 보낸다")
                .isZero();
            assertThat(closed(AUG).recordedAmount()).isEqualTo(90_000L);
            assertNoMoneyMovesOvernight(-100_000L);

            today(LocalDate.of(2026, 9, 16));
            expenseService.deleteExpense(id, user.getRowId());
            assertThat(balance(card)).isZero();
            assertThat(balance(bank)).isEqualTo(-100_000L);
        }
    }

    // === 수정 잠금(D12) ============================================================

    @Nested
    @DisplayName("수정 잠금")
    class Lock {

        private ExpenseServiceDto.UpdateCommand sameMoneyNewCategory(ExpenseServiceDto.ExpenseInfo now, Long category,
                                                                    LocalDateTime sentDate, boolean dateOnly) {
            // 옛 웹이 카테고리만 고쳐도 싣는 모양 — 금액·일시·유형·자산·할부·결제수단을 그대로 되돌려 보낸다.
            return new ExpenseServiceDto.UpdateCommand(
                Patch.set(category), Patch.set(now.assetRowId()), Patch.set(now.expenseType()),
                Patch.set(now.amount()), Patch.set("메모"), Patch.set(sentDate), Patch.set("새 가맹점"),
                Patch.set(null), Patch.set(1),
                Patch.set(null), Patch.set(null), Patch.set(null), Patch.absent(), Patch.absent(), null,
                dateOnly);
        }

        @Test
        @DisplayName("잠긴 거래 — 돈 칸이 같으면(분 단위·할부 null≡1·날짜만) 분류·메모·가맹점만 고친다")
        void samePassesAndEditsText() {
            Long id = augustPaid();
            Long other = newCategory("교통", ExpenseType.EXPENSE);
            // 저장값에 초가 있어도 화면은 HH:mm 로 되돌려 보낸다.
            tx.executeWithoutResult(s -> entityManager.createNativeQuery(
                    "UPDATE expense SET expense_date = :d WHERE row_id = :id")
                .setParameter("d", LocalDateTime.of(2026, 8, 5, 10, 0, 37))
                .setParameter("id", id).executeUpdate());
            var now = info(id);
            assertThat(now.moneyLocked()).isTrue();

            var saved = expenseService.updateExpense(id, user.getRowId(),
                sameMoneyNewCategory(now, other, LocalDateTime.of(2026, 8, 5, 10, 0), false));
            assertThat(saved.categoryRowId()).isEqualTo(other);
            assertThat(saved.merchant()).isEqualTo("새 가맹점");
            assertThat(saved.expenseDate()).as("돈 칸은 저장값 그대로").isEqualTo(LocalDateTime.of(2026, 8, 5, 10, 0, 37));

            // 날짜만 오는 형식도 같은 날이면 통과.
            expenseService.updateExpense(id, user.getRowId(),
                sameMoneyNewCategory(now, categoryRowId, LocalDate.of(2026, 8, 5).atStartOfDay(), true));
            assertThat(balance(bank)).isEqualTo(-100_000L);
            assertThat(balance(card)).isZero();
        }

        @Test
        @DisplayName("잠긴 거래 — 금액·날짜·자산이 달라지면 EXP_045")
        void changedMoneyIsRejected() {
            Long id = augustPaid();

            rejectedWith(() -> expenseService.updateExpense(id, user.getRowId(), new ExpenseServiceDto.UpdateCommand(
                Patch.absent(), Patch.absent(), Patch.absent(), Patch.set(70_000L), Patch.absent(),
                Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(),
                Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(), null)),
                DeskErrorCode.EXPENSE_MONEY_LOCKED);
            rejectedWith(() -> moveTo(id, LocalDate.of(2026, 7, 20)), DeskErrorCode.EXPENSE_MONEY_LOCKED);
            rejectedWith(() -> moveTo(id, LocalDate.of(2026, 9, 13)), DeskErrorCode.EXPENSE_MONEY_LOCKED);
            assertThat(balance(bank)).isEqualTo(-100_000L);
        }

        @Test
        @DisplayName("케이스 8 — 열린 회차 거래를 닫힌 회차로 옮기는 수정은 허용: 기록용, 돈 이동 없음, 그 뒤 잠김")
        void openToClosedIsAllowed() {
            augustPaid();
            Long id = spend(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 5), 20_000L);
            today(LocalDate.of(2026, 9, 14));

            var saved = moveTo(id, LocalDate.of(2026, 8, 20));

            assertThat(saved.moneyLocked()).isTrue();
            assertThat(markOf(id)).isEqualTo(LocalDate.of(2026, 8, 31));
            assertThat(balance(card)).isZero();
            assertThat(billing().upcomingAmount()).isZero();
            assertThat(balance(bank)).isEqualTo(-100_000L);
        }

        @Test
        @DisplayName("카드 이월 거래는 가계부에서 못 고친다(23차 10)")
        void carryoverIsReadOnly() {
            Long id = tx.execute(s -> {
                Expense carry = Expense.createExpense(user, null, card, ExpenseType.EXPENSE, 50_000L,
                    "이전 미결제 사용액", LocalDateTime.of(2026, 9, 1, 0, 0), "이전 미결제 사용액",
                    null, null, null, null, null);
                carry.markAutoGenerated(Expense.AUTO_SOURCE_CARD_CARRYOVER);
                return expenseRepository.save(carry).getRowId();
            });
            today(LocalDate.of(2026, 9, 14));

            rejectedWith(() -> moveTo(id, LocalDate.of(2026, 9, 2)), DeskErrorCode.EXPENSE_CARRYOVER_READONLY);
            // 환불·삭제·고쳐 쓰기도 같은 문구로 카드 설정을 가리킨다 — "원래 거래(매도·이체)" 가 아니다(QA 26 2).
            rejectedWith(() -> expenseService.refund(id, user.getRowId(), null),
                DeskErrorCode.EXPENSE_CARRYOVER_READONLY);
            rejectedWith(() -> expenseService.deleteExpense(id, user.getRowId()),
                DeskErrorCode.EXPENSE_CARRYOVER_READONLY);
            rejectedWith(() -> replace(id, LocalDate.of(2026, 9, 1), 50_000L),
                DeskErrorCode.EXPENSE_CARRYOVER_READONLY);
        }
    }

    // === 고쳐 쓰기(D13) ============================================================

    @Nested
    @DisplayName("고쳐 쓰기")
    class Replace {

        @Test
        @DisplayName("닫힌 → 닫힌: 날짜만 바꿔 다시 적어도 돈이 안 움직인다")
        void closedToClosed() {
            Long id = augustPaid();

            var fresh = replace(id, LocalDate.of(2026, 7, 20), 100_000L);

            assertThat(fresh.rowId()).isNotEqualTo(id);
            assertThat(fresh.refundedAmount()).isNull();
            assertThat(fresh.moneyLocked()).isTrue();
            assertThat(balance(bank)).isEqualTo(-100_000L);
            assertThat(balance(card)).isZero();
            assertThat(rows(BillingStatus.REFUNDED)).isEmpty();
            assertThat(closed(AUG).recordedAmount()).isZero();
            assertThat(closed(JUL).recordedAmount()).isEqualTo(100_000L);
            assertNoMoneyMovesOvernight(-100_000L);
        }

        @Test
        @DisplayName("닫힌 회차 안에서 감액 — 환급 없이 기록만(기록 70,000 · 계좌 100,000)")
        void reduceInClosedCycle() {
            Long id = augustPaid();

            var fresh = replace(id, LocalDate.of(2026, 8, 5), 70_000L);

            assertThat(fresh.refundedAmount()).isNull();
            assertThat(balance(bank)).isEqualTo(-100_000L);
            assertThat(balance(card)).isZero();
            var aug = closed(AUG);
            assertThat(aug.recordedAmount()).isEqualTo(70_000L);
            assertThat(aug.paidAmount()).isEqualTo(100_000L);
            assertNoMoneyMovesOvernight(-100_000L);
        }

        @Test
        @DisplayName("닫힌 회차 안에서 증액 — 늘어난 몫은 기록만, 통장 그대로")
        void increaseInClosedCycle() {
            Long id = augustPaid();

            replace(id, LocalDate.of(2026, 8, 5), 130_000L);

            assertThat(balance(bank)).isEqualTo(-100_000L);
            assertThat(balance(card)).isZero();
            assertThat(billing().upcomingAmount()).isZero();
            var aug = closed(AUG);
            assertThat(aug.recordedAmount()).isEqualTo(130_000L);
            assertThat(aug.paidAmount()).isEqualTo(100_000L);
        }

        @Test
        @DisplayName("닫힌 → 열린: 새 거래는 다음 결제일에 정상 청구, 옛 결제분은 기록에서만 빠진다")
        void closedToOpenIsBilled() {
            Long id = augustPaid();

            var fresh = replace(id, LocalDate.of(2026, 9, 13), 100_000L);

            assertThat(fresh.moneyLocked()).isFalse();
            assertThat(fresh.refundedAmount()).isNull();
            assertThat(balance(bank)).as("닫힌 회차에서 빠진 결제분은 돌려주지 않는다(D1)").isEqualTo(-100_000L);
            assertThat(balance(card)).isEqualTo(-100_000L);
            assertThat(billing().upcomingAmount()).isEqualTo(100_000L);
            assertThat(closed(AUG).recordedAmount()).isZero();
        }

        @Test
        @DisplayName("기록용을 열린 회차로 — 받쳐 둔 상계를 걷고 정상 청구")
        void recordToOpen() {
            augustPaid();
            Long id = spend(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 8, 20), 25_000L);

            replace(id, LocalDate.of(2026, 9, 13), 25_000L);

            assertThat(balance(card)).isEqualTo(-25_000L);
            assertThat(billing().upcomingAmount()).isEqualTo(25_000L);
            assertThat(balance(bank)).isEqualTo(-100_000L);
        }

        @Test
        @DisplayName("빚 캡으로 덜 낸 닫힌 회차에서 고쳐 써도 남은 빚이 사라지지 않는다(조사 G2)")
        void debtCappedCycleKeepsDebt() {
            Long id = spend(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 8, 5), 100_000L);
            Long income = newCategory("캐시백", ExpenseType.INCOME);
            today(LocalDate.of(2026, 9, 5));
            expenseService.createExpense(
                createCmd(LocalDate.of(2026, 9, 5), 40_000L, null, card, ExpenseType.INCOME, income));
            midnight(LocalDate.of(2026, 9, 12));
            assertThat(balance(bank)).as("카드 빚이 60,000 이라 이체도 60,000").isEqualTo(-60_000L);
            assertThat(balance(card)).isZero();
            today(LocalDate.of(2026, 9, 14));

            replace(id, LocalDate.of(2026, 8, 6), 100_000L);

            assertThat(balance(card)).isZero();
            assertThat(balance(bank)).isEqualTo(-60_000L);
            assertNoMoneyMovesOvernight(-60_000L);
        }

        @Test
        @DisplayName("분할·더치페이·반복 규칙·일정 연결을 새 거래로 옮긴다")
        void movesLinks() {
            Long id = augustPaid();
            Long other = newCategory("교통", ExpenseType.EXPENSE);
            expenseSplitService.replaceSplits(new ExpenseSplitServiceDto.ReplaceCommand(id, user.getRowId(),
                List.of(new ExpenseSplitServiceDto.SplitCommand(null, categoryRowId, 60_000L, "밥", 0),
                    new ExpenseSplitServiceDto.SplitCommand(null, other, 40_000L, "차", 1))));
            Long dutchId = tx.execute(s -> {
                Expense src = expenseRepository.findById(id).orElseThrow();
                DutchPay d = DutchPay.createDutchPay(src.getUser(), src, "저녁", null, 100_000L, "KRW",
                    SplitMethod.EQUAL, LocalDate.of(2026, 8, 5));
                entityManager.persist(d);
                return d.getRowId();
            });
            Long recurringId = tx.execute(s -> {
                Expense src = expenseRepository.findById(id).orElseThrow();
                RecurringTransaction r = RecurringTransaction.createRecurring(src.getUser(), src.getCategory(),
                    src.getAsset(), null, null, null, src, TxKind.EXPENSE, 100_000L, "구독", null, null,
                    RecurringFrequency.MONTHLY, 1, null, 5, null, LocalDate.of(2026, 8, 5), null, null,
                    LocalDate.of(2026, 10, 5), false, false);
                entityManager.persist(r);
                return r.getRowId();
            });

            var fresh = replace(id, LocalDate.of(2026, 8, 5), 100_000L);

            tx.executeWithoutResult(s -> {
                assertThat(expenseSplitRepository.findByExpense(fresh.rowId()))
                    .extracting(sp -> sp.getAmount()).containsExactlyInAnyOrder(60_000L, 40_000L);
                assertThat(expenseSplitRepository.findByExpense(id)).as("옛 분할은 함께 지운다").isEmpty();
                assertThat(entityManager.find(DutchPay.class, dutchId).getSourceExpense().getRowId())
                    .isEqualTo(fresh.rowId());
                assertThat(entityManager.find(RecurringTransaction.class, recurringId).getSourceExpense().getRowId())
                    .isEqualTo(fresh.rowId());
            });
        }

        @Test
        @DisplayName("분할이 있는데 금액을 바꾸고 분할을 안 보내면 400 — 한 트랜잭션이라 옛 거래도 그대로")
        void splitMismatchRollsBack() {
            Long id = augustPaid();
            Long other = newCategory("교통", ExpenseType.EXPENSE);
            expenseSplitService.replaceSplits(new ExpenseSplitServiceDto.ReplaceCommand(id, user.getRowId(),
                List.of(new ExpenseSplitServiceDto.SplitCommand(null, categoryRowId, 60_000L, "밥", 0),
                    new ExpenseSplitServiceDto.SplitCommand(null, other, 40_000L, "차", 1))));

            rejectedWith(() -> replace(id, LocalDate.of(2026, 8, 5), 90_000L),
                DeskErrorCode.EXPENSE_SPLIT_AMOUNT_MISMATCH);

            assertThat(info(id).amount()).isEqualTo(100_000L);
            assertThat(balance(card)).isZero();
        }

        @Test
        @DisplayName("거절 — 결제 전 거래·환불된 거래·자동 생성·중도 정리 할부, 재시도는 404")
        void rejections() {
            augustPaid();
            Long open = spend(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 13), 10_000L);
            assertThat(info(open).replaceable()).as("잠기지 않은 거래엔 [고쳐 쓰기] 가 없다").isFalse();
            rejectedWith(() -> replace(open, LocalDate.of(2026, 9, 13), 9_000L),
                DeskErrorCode.EXPENSE_REPLACE_NOT_LOCKED);

            Long refunded = spend(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 8, 21), 5_000L);
            expenseService.refund(refunded, user.getRowId(), null);
            rejectedWith(() -> replace(refunded, LocalDate.of(2026, 8, 21), 4_000L),
                DeskErrorCode.REFUNDED_READONLY);

            Long installment = spend(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 8, 10), 90_000L, 3, card);
            assertThat(info(installment).replaceable()).isTrue();
            cardPaymentService.payoffInstallment(card.getRowId(), installment, user.getRowId());
            var paidOff = info(installment);
            assertThat(paidOff.moneyLocked()).isTrue();
            assertThat(paidOff.replaceable()).as("중도 정리한 할부는 잠겼지만 고쳐 쓸 수 없다(QA 26 3)").isFalse();
            rejectedWith(() -> replace(installment, LocalDate.of(2026, 8, 10), 90_000L),
                DeskErrorCode.EXPENSE_REPLACE_PAID_OFF);

            Long paid = spend(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 8, 22), 7_000L);
            assertThat(info(paid).replaceable()).isTrue();
            var fresh = replace(paid, LocalDate.of(2026, 8, 22), 7_000L);
            assertThat(fresh.rowId()).isNotEqualTo(paid);
            assertThatThrownBy(() -> replace(paid, LocalDate.of(2026, 8, 22), 7_000L))
                .as("재시도 — 옛 거래는 이미 지워졌다").isInstanceOf(EntityNotFoundException.class);
        }
    }

    // === 삭제·환불 — D1·D3·D16 ======================================================

    @Nested
    @DisplayName("삭제·환불")
    class Remove {

        @Test
        @DisplayName("케이스 11 — 기록용 삭제는 기록만 정리, 돈 이동 없음")
        void deleteRecord() {
            augustPaid();
            Long id = spend(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 8, 20), 25_000L);

            var preview = expenseService.refundPreview(id, user.getRowId(), null, null, null, null);
            assertThat(preview.refundAmount()).isZero();
            assertThat(preview.reason()).isEqualTo(CardPaymentServiceDto.RefundPreview.NOT_PAID_CYCLE);

            Long refunded = expenseService.deleteExpense(id, user.getRowId());

            assertThat(refunded).isNull();
            assertThat(balance(bank)).isEqualTo(-100_000L);
            assertThat(balance(card)).isZero();
            assertThat(closed(AUG).recordedAmount()).isEqualTo(100_000L);
        }

        @Test
        @DisplayName("케이스 12 — 결제분 삭제도 돌려주지 않는다: 카드·통장 그대로, 스윕도 없다(D1)")
        void deletePaidMovesNothing() {
            Long paid = augustPaid();
            spend(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 8, 20), 25_000L);

            Long refunded = expenseService.deleteExpense(paid, user.getRowId());

            assertThat(refunded).isNull();
            assertThat(balance(bank)).isEqualTo(-100_000L);
            assertThat(balance(card)).isZero();
            var aug = closed(AUG);
            assertThat(aug.recordedAmount()).isEqualTo(25_000L);
            assertThat(aug.paidAmount()).isEqualTo(100_000L);
            assertThat(rows(BillingStatus.REFUNDED)).isEmpty();
            assertNoMoneyMovesOvernight(-100_000L);
        }

        @Test
        @DisplayName("결제분 환불 → 취소: 통계에서만 빠졌다 돌아온다(U5)")
        void refundAndCancelPaid() {
            Long id = augustPaid();

            var info = expenseService.refund(id, user.getRowId(), null);
            assertThat(info.refundedAmount()).isNull();
            assertThat(balance(bank)).isEqualTo(-100_000L);
            assertThat(balance(card)).isZero();
            assertThat(closed(AUG).paidAmount()).isEqualTo(100_000L);

            expenseService.cancelRefund(id, user.getRowId());

            assertThat(balance(bank)).isEqualTo(-100_000L);
            assertThat(balance(card)).isZero();
            assertThat(markOf(id)).isNull();
            assertNoMoneyMovesOvernight(-100_000L);
        }

        @Test
        @DisplayName("기록용 환불 → 취소하면 다시 기록용")
        void refundAndCancelRecord() {
            augustPaid();
            Long id = spend(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 8, 20), 25_000L);

            expenseService.refund(id, user.getRowId(), null);
            assertThat(balance(bank)).isEqualTo(-100_000L);
            assertThat(balance(card)).isZero();

            expenseService.cancelRefund(id, user.getRowId());

            assertThat(balance(bank)).isEqualTo(-100_000L);
            assertThat(balance(card)).isZero();
            assertThat(markOf(id)).isEqualTo(LocalDate.of(2026, 8, 31));
        }

        @Test
        @DisplayName("환불 동안 결제일이 지나면 취소 때 그 회차분은 기록용이 된다")
        void cancelAfterCycleClosed() {
            Long id = spend(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 8, 5), 60_000L);
            today(LocalDate.of(2026, 8, 20));
            expenseService.refund(id, user.getRowId(), null);
            midnight(LocalDate.of(2026, 9, 12));
            assertThat(balance(bank)).as("환불된 거래는 청구에 없다").isZero();
            today(LocalDate.of(2026, 9, 14));

            expenseService.cancelRefund(id, user.getRowId());

            assertThat(markOf(id)).isEqualTo(LocalDate.of(2026, 8, 31));
            assertThat(balance(card)).as("9/12 결제에 빠져 있었다 — 기록용으로 상계").isZero();
            assertThat(billing().upcomingAmount()).isZero();
        }

        @Test
        @DisplayName("환불일은 거래일부터 오늘까지(D16)")
        void refundDateRange() {
            Long id = augustPaid();

            rejectedWith(() -> expenseService.refund(id, user.getRowId(), LocalDateTime.of(2026, 8, 4, 10, 0)),
                DeskErrorCode.REFUND_DATE_OUT_OF_RANGE);
            rejectedWith(() -> expenseService.refund(id, user.getRowId(), LocalDateTime.of(2026, 9, 15, 10, 0)),
                DeskErrorCode.REFUND_DATE_OUT_OF_RANGE);
            assertThat(expenseService.refund(id, user.getRowId(), LocalDateTime.of(2026, 8, 5, 9, 0))
                .refundedAt()).isNotNull();
        }

        @Test
        @DisplayName("삭제하면 분할도 함께 지운다(2-1 ⑥)")
        void deleteRemovesSplits() {
            Long id = augustPaid();
            Long other = newCategory("교통", ExpenseType.EXPENSE);
            expenseSplitService.replaceSplits(new ExpenseSplitServiceDto.ReplaceCommand(id, user.getRowId(),
                List.of(new ExpenseSplitServiceDto.SplitCommand(null, categoryRowId, 60_000L, "밥", 0),
                    new ExpenseSplitServiceDto.SplitCommand(null, other, 40_000L, "차", 1))));

            expenseService.deleteExpense(id, user.getRowId());

            List<com.porest.desk.expense.domain.ExpenseSplit> left =
                tx.execute(s -> expenseSplitRepository.findByExpense(id));
            assertThat(left).isEmpty();
        }

        @Test
        @DisplayName("지운 카드의 거래도 지우고 환불할 수 있다(23차 11)")
        void deletedCardTransactions() {
            Long a = spend(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 5), 10_000L);
            Long b = spend(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 5), 20_000L);
            today(LocalDate.of(2026, 9, 14));
            assetService.deleteAsset(card.getRowId(), user.getRowId());

            expenseService.deleteExpense(a, user.getRowId());
            assertThat(expenseService.refund(b, user.getRowId(), null).refundedAt()).isNotNull();
        }
    }

    // === 열린 회차 선결제 환급 — D3·23차 결함 2 ======================================

    @Nested
    @DisplayName("선결제")
    class Prepaid {

        @Test
        @DisplayName("선결제한 회차에서 거래를 지우면 회차에 묶어 환급하고, 새 지출은 다시 청구된다(결함 2)")
        void prepaidCycleRefundKeepsNetPaid() {
            Long id = spend(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 5), 50_000L);
            cardPaymentService.payCard(card.getRowId(), user.getRowId(), 50_000L, LocalDate.of(2026, 10, 12));
            assertThat(balance(bank)).isEqualTo(-50_000L);

            today(LocalDate.of(2026, 9, 6));
            Long refunded = expenseService.deleteExpense(id, user.getRowId());
            assertThat(refunded).isEqualTo(50_000L);
            assertThat(balance(bank)).isZero();
            assertThat(rows(BillingStatus.REFUNDED)).hasSize(1);

            spend(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 7), 30_000L);

            var next = billing().nextCycle();
            assertThat(next.periodStart()).isEqualTo(SEP);
            assertThat(next.alreadyPaidAmount()).as("순 납부액 = 50,000 − 50,000").isZero();
            assertThat(next.amount()).isEqualTo(30_000L);
        }

        @Test
        @DisplayName("결제일 없는 옛 카드 — 늘 열린 회차라 선결제 뒤 삭제하면 환급된다(D8)")
        void noPaymentDayCardRefundsPrepaid() {
            Asset legacy = newAsset("옛카드", AssetType.CREDIT_CARD, null, bank);
            Long id = spend(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 5), 50_000L, null, legacy);
            cardPaymentService.payCard(legacy.getRowId(), user.getRowId(), 50_000L, null);
            assertThat(balance(bank)).isEqualTo(-50_000L);

            today(LocalDate.of(2026, 9, 6));
            Long refunded = expenseService.deleteExpense(id, user.getRowId());

            assertThat(refunded).isEqualTo(50_000L);
            assertThat(balance(bank)).isZero();
            assertThat(balance(legacy)).isZero();
        }
    }

    // === 결제 취소 — D6 ============================================================

    @Nested
    @DisplayName("결제 취소")
    class CancelPayment {

        @Test
        @DisplayName("결제일이 된 회차의 결제는 무를 수 없다")
        void closedCycleCannotBeCanceled() {
            augustPaid();
            Long billingId = rows(BillingStatus.COMPLETED).get(0).getRowId();

            rejectedWith(() -> cardPaymentService.cancelPayment(billingId, user.getRowId()),
                DeskErrorCode.CARD_BILLING_CYCLE_CLOSED);
        }

        @Test
        @DisplayName("환급이 나간 회차의 선결제는 무를 수 없다")
        void refundedCycleCannotBeCanceled() {
            Long id = spend(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 5), 50_000L);
            spend(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 5), 30_000L);
            cardPaymentService.payCard(card.getRowId(), user.getRowId(), 80_000L, LocalDate.of(2026, 10, 12));
            today(LocalDate.of(2026, 9, 6));
            expenseService.deleteExpense(id, user.getRowId());
            Long billingId = rows(BillingStatus.COMPLETED).get(0).getRowId();

            rejectedWith(() -> cardPaymentService.cancelPayment(billingId, user.getRowId()),
                DeskErrorCode.CARD_BILLING_REFUNDED);
        }

        @Test
        @DisplayName("정리한 회차가 결제된 뒤에는 할부 중도 정리를 되돌릴 수 없다 — 두 번 청구되지 않게")
        void payoffCancelBlockedAfterClosed() {
            Long id = spend(LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 10), 90_000L, 3, card);
            cardPaymentService.payoffInstallment(card.getRowId(), id, user.getRowId());
            midnight(LocalDate.of(2026, 9, 12));
            assertThat(balance(bank)).as("8월 회차에 원금 전액").isEqualTo(-90_000L);
            today(LocalDate.of(2026, 9, 14));

            rejectedWith(() -> cardPaymentService.cancelInstallmentPayoff(card.getRowId(), id, user.getRowId()),
                DeskErrorCode.CARD_INSTALLMENT_PAYOFF_CLOSED);
            assertThat(billing().upcomingAmount()).isZero();
        }
    }

    // === 결제일 변경(D5)·카드 폼(D7·D15) ==============================================

    @Nested
    @DisplayName("카드 설정")
    class CardSettings {

        private Asset createCard(int paymentDay, long carryover, LocalDate on) {
            today(on);
            var info = assetService.createAsset(new AssetServiceDto.CreateAssetCommand(
                user.getRowId(), "새카드", AssetType.CREDIT_CARD, carryover, null, "KRW", null, null, null,
                null, 0, YNType.Y, YNType.N, null, 5_000_000L, paymentDay, bank.getRowId(), null));
            return tx.execute(s -> assetRepository.findById(info.rowId()).orElseThrow());
        }

        private AssetServiceDto.AssetInfo put(Asset asset, Patch<Long> balance, Patch<Integer> day,
                                              Patch<Long> carryover) {
            return assetService.updateAsset(asset.getRowId(), user.getRowId(), new AssetServiceDto.UpdateAssetCommand(
                Patch.absent(), Patch.absent(), balance, Patch.absent(), Patch.absent(), Patch.absent(),
                Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(),
                Patch.absent(), day, Patch.absent(), null, carryover));
        }

        @Test
        @DisplayName("25일 → 5일: 결제 전이던 8월분은 9/25 에, 9월분부터 10/5 에(D5)")
        void changeTo5() {
            Asset c = createCard(25, 0L, LocalDate.of(2026, 7, 1));
            spend(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 8, 5), 100_000L, null, c);
            spend(LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 3), 20_000L, null, c);
            today(LocalDate.of(2026, 9, 21));

            put(c, Patch.absent(), Patch.set(5), Patch.absent());

            var b = cardPaymentService.getCardBilling(c.getRowId(), user.getRowId());
            assertThat(b.nextPaymentDate()).isEqualTo(LocalDate.of(2026, 9, 25));
            assertThat(b.nextCycle().paymentDate()).isEqualTo(LocalDate.of(2026, 10, 5));
            // 자산 응답도 옛 결제일로 나갈 회차의 실제 결제일을 준다 — 화면이 지금 결제일(5일)로 세면 9/5 가 된다(QA 26 4).
            var asset = assetService.getAsset(c.getRowId(), user.getRowId());
            assertThat(asset.paymentDay()).isEqualTo(5);
            assertThat(asset.nextPaymentDate()).isEqualTo(LocalDate.of(2026, 9, 25));
            midnight(LocalDate.of(2026, 9, 25));
            assertThat(balance(bank)).as("8월분은 옛 결제일(9/25)에").isEqualTo(-100_000L);
            midnight(LocalDate.of(2026, 10, 5));
            assertThat(balance(bank)).as("9월분부터 새 결제일(10/5)에").isEqualTo(-120_000L);
            midnight(LocalDate.of(2026, 10, 25));
            assertThat(balance(bank)).as("10/25 에는 결제할 회차가 없다").isEqualTo(-120_000L);
        }

        @Test
        @DisplayName("5일 → 25일: 9월분은 10/5 그대로, 10월분부터 11/25")
        void changeTo25() {
            Asset c = createCard(5, 0L, LocalDate.of(2026, 7, 1));
            spend(LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 3), 20_000L, null, c);
            today(LocalDate.of(2026, 9, 21));

            put(c, Patch.absent(), Patch.set(25), Patch.absent());

            var b = cardPaymentService.getCardBilling(c.getRowId(), user.getRowId());
            assertThat(b.nextPaymentDate()).isEqualTo(LocalDate.of(2026, 10, 5));
            assertThat(b.nextCycle().paymentDate()).isEqualTo(LocalDate.of(2026, 11, 25));
            assertThat(assetService.getAsset(c.getRowId(), user.getRowId()).nextPaymentDate())
                .isEqualTo(LocalDate.of(2026, 10, 5));
            midnight(LocalDate.of(2026, 10, 5));
            assertThat(balance(bank)).isEqualTo(-20_000L);
            var info = assetService.getAsset(c.getRowId(), user.getRowId());
            assertThat(info.cardClosedThrough()).isEqualTo(LocalDate.of(2026, 9, 30));
        }

        @Test
        @DisplayName("신용카드 PUT 의 balance 는 무시 — 이월 금액은 carryoverAmount 로만(D7)")
        void balanceIgnoredCarryoverKey() {
            Asset c = createCard(25, 50_000L, LocalDate.of(2026, 9, 1));
            today(LocalDate.of(2026, 9, 10));

            var afterBalance = put(c, Patch.set(-999_000L), Patch.absent(), Patch.absent());
            assertThat(afterBalance.carryoverAmount()).isEqualTo(50_000L);

            var afterCarry = put(c, Patch.absent(), Patch.absent(), Patch.set(70_000L));
            assertThat(afterCarry.carryoverAmount()).isEqualTo(70_000L);
            assertThat(afterCarry.carryoverLocked()).isFalse();
            assertThat(balance(c)).isEqualTo(-70_000L);
        }

        @Test
        @DisplayName("이월 거래가 든 회차가 결제되면 이월 금액은 못 고친다(D15)")
        void carryoverLockedAfterPayment() {
            Asset c = createCard(12, 50_000L, LocalDate.of(2026, 8, 20));
            today(LocalDate.of(2026, 9, 12));

            var info = assetService.getAsset(c.getRowId(), user.getRowId());
            assertThat(info.carryoverLocked()).isTrue();
            assertThat(info.cardClosedThrough()).isEqualTo(LocalDate.of(2026, 8, 31));
            rejectedWith(() -> put(c, Patch.absent(), Patch.absent(), Patch.set(10_000L)),
                DeskErrorCode.ASSET_CARD_CARRYOVER_LOCKED);
            assertThat(put(c, Patch.absent(), Patch.absent(), Patch.set(50_000L)).carryoverAmount())
                .as("같은 값은 통과").isEqualTo(50_000L);
        }

        @Test
        @DisplayName("신용카드는 결제일이 있어야 한다(D8) — 비우는 수정은 거절, 옛 카드의 다른 칸 수정은 된다")
        void paymentDayRequired() {
            today(LocalDate.of(2026, 9, 14));
            rejectedWith(() -> assetService.createAsset(new AssetServiceDto.CreateAssetCommand(
                    user.getRowId(), "결제일없음", AssetType.CREDIT_CARD, 0L, null, "KRW", null, null, null,
                    null, 0, YNType.Y, YNType.N, null, 1_000_000L, null, bank.getRowId(), null)),
                DeskErrorCode.ASSET_CARD_PAYMENT_DAY_REQUIRED);
            rejectedWith(() -> put(card, Patch.absent(), Patch.set(null), Patch.absent()),
                DeskErrorCode.ASSET_CARD_PAYMENT_DAY_REQUIRED);

            Asset legacy = newAsset("옛카드", AssetType.CREDIT_CARD, null, bank);
            var renamed = assetService.updateAsset(legacy.getRowId(), user.getRowId(),
                new AssetServiceDto.UpdateAssetCommand(
                    Patch.set("이름만"), Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(),
                    Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(),
                    Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(), null));
            assertThat(renamed.assetName()).isEqualTo("이름만");
            assertThat(renamed.cardClosedThrough()).isNull();
        }
    }
}
