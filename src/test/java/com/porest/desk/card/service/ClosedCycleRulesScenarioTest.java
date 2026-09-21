package com.porest.desk.card.service;

import com.porest.core.time.UserClock;
import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.asset.service.AssetBalanceHistoryService;
import com.porest.desk.asset.type.AssetType;
import com.porest.desk.card.domain.CardBilling;
import com.porest.desk.card.repository.CardBillingRepository;
import com.porest.desk.card.service.dto.CardPaymentServiceDto;
import com.porest.desk.card.type.BillingStatus;
import com.porest.desk.common.patch.Patch;
import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.desk.expense.repository.ExpenseCategoryRepository;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.service.ExpenseService;
import com.porest.desk.expense.service.dto.ExpenseServiceDto;
import com.porest.desk.expense.type.ExpenseType;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * 닫힌 회차 규칙(2026-09-21 확정) — 설계서 4절 케이스를 진짜 트랜잭션·진짜 질의로 끝까지 돌린다.
 *
 * <p>결제일 12일 카드, 기본 오늘은 9/14. 8월 회차(8/1~8/31)는 9/12 에 결제가 끝나 닫혔고
 * 환급 기한은 9/30 이다. 9월 회차는 10/12 에 나간다.
 *
 * <p>돈의 규칙은 여러 서비스(거래 저장 → 회차 판정 → 이체·상계 → 잔액 집계)를 거쳐야 드러나서
 * mock 으로는 잠글 수 없다. 오늘은 {@link UserClock} 을 고정해 정한다 — {@code now()±N일} 로
 * 쓰면 달이 바뀌는 날 회차가 어긋난다.
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

    @Autowired private ExpenseService expenseService;
    @Autowired private CardPaymentService cardPaymentService;
    @Autowired private AssetRepository assetRepository;
    @Autowired private AssetBalanceHistoryService balanceHistoryService;
    @Autowired private CardBillingRepository cardBillingRepository;
    @Autowired private ExpenseRepository expenseRepository;
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
        user = newUser();
        categoryRowId = tx.execute(s -> expenseCategoryRepository.save(ExpenseCategory.createCategory(
            user, "식비", null, null, ExpenseType.EXPENSE, null)).getRowId());
        bank = newAsset("통장", AssetType.BANK_ACCOUNT, null, null);
        card = newAsset("카드", AssetType.CREDIT_CARD, 12, bank);
        registeredOn(card, LocalDate.of(2026, 6, 1));
    }

    // === 픽스처 =================================================================

    private User newUser() {
        String id = "cc" + UUID.randomUUID().toString().substring(0, 8);
        return tx.execute(s -> userRepository.save(User.createUser(null, id, "테스터", id + "@porest.com")));
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

    private void today(LocalDate d) {
        given(userClock.today(any())).willReturn(d);
        given(userClock.now(any())).willReturn(d.atTime(12, 0));
        given(userClock.todayIn(any())).willReturn(d);
        given(userClock.nowIn(any())).willReturn(d.atTime(12, 0));
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
        return expenseService.createExpense(new ExpenseServiceDto.CreateCommand(
            user.getRowId(), categoryRowId, asset.getRowId(), ExpenseType.EXPENSE, amount, "테스트",
            date.atTime(10, 0), "가맹점", null, months, null, null, null, null, null)).rowId();
    }

    private ExpenseServiceDto.ExpenseInfo moveTo(Long expenseId, LocalDate date) {
        return expenseService.updateExpense(expenseId, user.getRowId(), new ExpenseServiceDto.UpdateCommand(
            Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(),
            Patch.set(date.atTime(10, 0)), Patch.absent(), Patch.absent(), Patch.absent(),
            Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(), null));
    }

    private ExpenseServiceDto.ExpenseInfo amountTo(Long expenseId, long amount) {
        return expenseService.updateExpense(expenseId, user.getRowId(), new ExpenseServiceDto.UpdateCommand(
            Patch.absent(), Patch.absent(), Patch.absent(), Patch.set(amount), Patch.absent(),
            Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(),
            Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(), Patch.absent(), null));
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
        return cardBillingRepository.findByCardAssetRowId(card.getRowId()).stream()
            .filter(b -> b.getIsDeleted() == YNType.N && b.getStatus() == status)
            .toList();
    }

    /** 8/5 에 쓴 100,000 을 9/12 에 앱이 결제했다 — 대부분 케이스의 출발점. */
    private Long augustPaid() {
        Long id = spend(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 8, 5), 100_000L);
        midnight(LocalDate.of(2026, 9, 12));
        assertThat(balance(bank)).isEqualTo(-100_000L);
        assertThat(balance(card)).isZero();
        return id;
    }

    // === 저장 — R1·R2·R3·R4·R5 =====================================================

    @Nested
    @DisplayName("저장")
    class Save {

        @Test
        @DisplayName("케이스 1 — 열린 회차 거래는 그대로 청구된다")
        void openCycleIsBilled() {
            augustPaid();
            Long id = spend(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 13), 30_000L);

            assertThat(markOf(id)).isNull();
            assertThat(balance(card)).isEqualTo(-30_000L);
            assertThat(billing().upcomingAmount()).isEqualTo(30_000L);
            assertThat(balance(bank)).isEqualTo(-100_000L);
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
            assertThat(aug.recordedOnlyAmount()).isEqualTo(25_000L);
            assertThat(aug.preRegistration()).isFalse();
            assertThat(aug.refundableUntil()).isEqualTo(LocalDate.of(2026, 9, 30));

            midnight(LocalDate.of(2026, 9, 15));
            assertThat(balance(bank)).as("다음 자정 스윕이 돌려주지 않는다").isEqualTo(-100_000L);
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
        @DisplayName("케이스 5 — 결제일 당일 입력은 그 회차에 얹어 그 자리에서 결제한다(R3)")
        void paymentDayAddsToThatCycle() {
            augustPaid();
            today(LocalDate.of(2026, 9, 12));
            var preview = expenseService.cardSavePreview(user.getRowId(), card.getRowId(), 40_000L,
                LocalDate.of(2026, 8, 30).atTime(10, 0), null);
            assertThat(preview.sameDayExtraPayment()).isEqualTo(40_000L);
            assertThat(preview.newRecordAmount()).isZero();

            Long id = spend(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 8, 30), 40_000L);

            assertThat(markOf(id)).isNull();
            assertThat(balance(bank)).isEqualTo(-140_000L);
            assertThat(balance(card)).isZero();
            assertThat(rows(BillingStatus.COMPLETED)).extracting(CardBilling::getBillingAmount)
                .containsExactlyInAnyOrder(100_000L, 40_000L);
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
        @DisplayName("케이스 7 — 할부는 지난 회차분만 기록용, 남은 회차는 정상 청구(R5)")
        void installmentSplitsByCycle() {
            Long id = spend(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 7, 10), 90_000L, 3, card);

            assertThat(markOf(id)).isEqualTo(LocalDate.of(2026, 8, 31));
            assertThat(balance(card)).as("3회차 30,000 만 빚").isEqualTo(-30_000L);
            assertThat(billing().upcomingAmount()).isEqualTo(30_000L);
            assertThat(closed(JUL).recordedOnlyAmount()).isEqualTo(30_000L);
            assertThat(closed(AUG).recordedOnlyAmount()).isEqualTo(30_000L);
            var info = expenseService.getExpenses(user.getRowId(), null, card.getRowId(), null, null, null)
                .stream().filter(e -> e.rowId().equals(id)).findFirst().orElseThrow();
            assertThat(info.recordOnlyAmount()).isEqualTo(60_000L);
        }

        @Test
        @DisplayName("닫힌 회차 저장 미리보기 — 기록만 남는 금액을 알려 준다")
        void savePreviewForClosedCycle() {
            today(LocalDate.of(2026, 9, 14));
            var preview = expenseService.cardSavePreview(user.getRowId(), card.getRowId(), 25_000L,
                LocalDate.of(2026, 8, 20).atTime(10, 0), null);

            assertThat(preview.newRecordAmount()).isEqualTo(25_000L);
            assertThat(preview.sameDayExtraPayment()).isZero();
        }
    }

    // === 날짜·금액 수정 — R7·R8·케이스 8·10 ==========================================

    @Nested
    @DisplayName("수정")
    class Update {

        @Test
        @DisplayName("케이스 8 — 결제 전 거래를 닫힌 회차로 옮기면 기록용, 돈 이동 없음")
        void unbilledToClosed() {
            augustPaid();
            Long id = spend(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 5), 20_000L);
            today(LocalDate.of(2026, 9, 14));

            moveTo(id, LocalDate.of(2026, 8, 20));

            assertThat(markOf(id)).isEqualTo(LocalDate.of(2026, 8, 31));
            assertThat(balance(card)).isZero();
            assertThat(billing().upcomingAmount()).isZero();
            assertThat(balance(bank)).isEqualTo(-100_000L);
        }

        @Test
        @DisplayName("케이스 9 — 결제 끝난 거래를 다른 닫힌 회차로 옮겨도 환급하지 않는다(23차 결함 4)")
        void closedToClosedMovesNoMoney() {
            Long id = augustPaid();
            today(LocalDate.of(2026, 9, 14));

            var info = moveTo(id, LocalDate.of(2026, 7, 20));

            assertThat(info.refundedAmount()).isNull();
            assertThat(balance(bank)).isEqualTo(-100_000L);
            assertThat(balance(card)).isZero();
            assertThat(rows(BillingStatus.REFUNDED)).isEmpty();

            midnight(LocalDate.of(2026, 9, 15));
            assertThat(balance(bank)).as("다음 자정 스윕도 돌려주지 않는다").isEqualTo(-100_000L);
        }

        @Test
        @DisplayName("케이스 10 — 기록용을 열린 회차로 옮기면 정상 청구로 돌아간다")
        void recordToOpenIsBilled() {
            augustPaid();
            Long id = spend(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 8, 20), 25_000L);

            moveTo(id, LocalDate.of(2026, 9, 13));

            assertThat(markOf(id)).isNull();
            assertThat(balance(card)).isEqualTo(-25_000L);
            assertThat(billing().upcomingAmount()).isEqualTo(25_000L);
        }

        @Test
        @DisplayName("케이스 16 — 환급 기한 지난 결제분을 열린 회차로 옮기면 다시 청구하지 않는다(R8)")
        void expiredPaidToOpenStaysRecord() {
            Long jul = spend(LocalDate.of(2026, 7, 5), LocalDate.of(2026, 7, 5), 50_000L);
            midnight(LocalDate.of(2026, 8, 12));
            midnight(LocalDate.of(2026, 9, 12));
            assertThat(balance(bank)).isEqualTo(-50_000L);
            today(LocalDate.of(2026, 9, 14));

            moveTo(jul, LocalDate.of(2026, 9, 13));

            assertThat(markOf(jul)).isEqualTo(LocalDate.of(2026, 9, 30));
            assertThat(billing().upcomingAmount()).as("이미 낸 돈 — 두 번 청구하지 않는다").isZero();
            assertThat(balance(card)).isZero();
            assertThat(balance(bank)).isEqualTo(-50_000L);
        }

        @Test
        @DisplayName("R8 — 기한 안의 결제분을 열린 회차로 옮기면 환급 뒤 새 회차에서 청구")
        void paidToOpenWithinWindow() {
            Long id = augustPaid();
            today(LocalDate.of(2026, 9, 14));

            var info = moveTo(id, LocalDate.of(2026, 9, 13));

            assertThat(info.refundedAmount()).isEqualTo(100_000L);
            assertThat(balance(bank)).isZero();
            assertThat(balance(card)).isEqualTo(-100_000L);
            assertThat(billing().upcomingAmount()).isEqualTo(100_000L);
        }

        @Test
        @DisplayName("결제된 거래 감액 — 기한 안이면 줄어든 만큼 돌려준다")
        void reducePaidWithinWindow() {
            Long id = augustPaid();
            today(LocalDate.of(2026, 9, 14));

            var info = amountTo(id, 70_000L);

            assertThat(info.refundedAmount()).isEqualTo(30_000L);
            assertThat(balance(bank)).isEqualTo(-70_000L);
            assertThat(balance(card)).isZero();
            assertThat(closed(AUG).paidAmount()).as("순 납부액 = 결제 − 환급").isEqualTo(70_000L);
        }

        @Test
        @DisplayName("결제된 거래 증액 — 닫힌 회차에 늘어난 몫은 기록용, 통장 그대로")
        void increasePaidClosed() {
            Long id = augustPaid();
            today(LocalDate.of(2026, 9, 14));

            amountTo(id, 130_000L);

            assertThat(balance(bank)).isEqualTo(-100_000L);
            assertThat(balance(card)).isZero();
            assertThat(billing().upcomingAmount()).isZero();
        }
    }

    // === 삭제·환불 — R6·케이스 11·12·15 ==============================================

    @Nested
    @DisplayName("삭제·환불")
    class Remove {

        @Test
        @DisplayName("케이스 11 — 기록용 삭제는 기한 안이면 전액 환급")
        void deleteRecordWithinWindow() {
            augustPaid();
            Long id = spend(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 8, 20), 25_000L);

            var preview = expenseService.refundPreview(id, user.getRowId(), null, null, null, null);
            assertThat(preview.reason()).isEqualTo(CardPaymentServiceDto.RefundPreview.RECORD_ONLY_OK);
            assertThat(preview.refundAmount()).isEqualTo(25_000L);

            Long refunded = expenseService.deleteExpense(id, user.getRowId());

            assertThat(refunded).isEqualTo(25_000L);
            assertThat(balance(bank)).isEqualTo(-75_000L);
            assertThat(balance(card)).isZero();
            assertThat(closed(AUG).paidAmount()).as("기록용 환급은 순 납부액을 안 깎는다").isEqualTo(100_000L);
        }

        @Test
        @DisplayName("케이스 11 — 기한(9/30)이 지나면 기록만 정리, 돈 이동 없음")
        void deleteRecordAfterWindow() {
            augustPaid();
            Long id = spend(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 8, 20), 25_000L);
            today(LocalDate.of(2026, 10, 1));

            var preview = expenseService.refundPreview(id, user.getRowId(), null, null, null, null);
            assertThat(preview.reason()).isEqualTo(CardPaymentServiceDto.RefundPreview.REFUND_WINDOW_CLOSED);

            Long refunded = expenseService.deleteExpense(id, user.getRowId());

            assertThat(refunded).isNull();
            assertThat(balance(bank)).isEqualTo(-100_000L);
            assertThat(balance(card)).isZero();
        }

        @Test
        @DisplayName("케이스 12 — 같은 회차에 기록용이 있어도 결제분 환급은 줄지 않는다")
        void paidRefundNotReducedByRecord() {
            Long paid = augustPaid();
            spend(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 8, 20), 25_000L);

            Long refunded = expenseService.deleteExpense(paid, user.getRowId());

            assertThat(refunded).isEqualTo(100_000L);
            assertThat(balance(bank)).isZero();
            assertThat(balance(card)).isZero();
        }

        @Test
        @DisplayName("케이스 12 — 10/1 부터는 결제분도 환급 없이 기록만, 스윕도 돌려주지 않는다")
        void paidAfterWindowIsHeld() {
            Long paid = augustPaid();
            today(LocalDate.of(2026, 10, 1));

            Long refunded = expenseService.deleteExpense(paid, user.getRowId());

            assertThat(refunded).isNull();
            assertThat(balance(card)).as("카드가 양수로 뜨지 않게 붙잡는다").isZero();
            midnight(LocalDate.of(2026, 10, 2));
            assertThat(balance(bank)).isEqualTo(-100_000L);
        }

        @Test
        @DisplayName("케이스 15 — 기한 지난 회차(7월분, 8/31)에 입력하고 바로 지우면 돈 이동 없음")
        void expiredWindowRecordDelete() {
            augustPaid();
            Long id = spend(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 7, 15), 30_000L);

            Long refunded = expenseService.deleteExpense(id, user.getRowId());

            assertThat(refunded).isNull();
            assertThat(balance(bank)).isEqualTo(-100_000L);
            assertThat(balance(card)).isZero();
        }

        @Test
        @DisplayName("결제분 환불 → 취소하면 환불 전 그대로(이체·순 납부액까지)")
        void refundAndCancelPaid() {
            Long id = augustPaid();
            today(LocalDate.of(2026, 9, 14));

            var info = expenseService.refund(id, user.getRowId(), null);
            assertThat(info.refundedAmount()).isEqualTo(100_000L);
            assertThat(balance(bank)).isZero();
            assertThat(closed(AUG).paidAmount()).isZero();

            expenseService.cancelRefund(id, user.getRowId());

            assertThat(balance(bank)).isEqualTo(-100_000L);
            assertThat(balance(card)).isZero();
            assertThat(markOf(id)).isNull();
            assertThat(closed(AUG).paidAmount()).isEqualTo(100_000L);
        }

        @Test
        @DisplayName("기록용 환불 → 취소하면 다시 기록용")
        void refundAndCancelRecord() {
            augustPaid();
            Long id = spend(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 8, 20), 25_000L);

            expenseService.refund(id, user.getRowId(), null);
            assertThat(balance(bank)).isEqualTo(-75_000L);

            expenseService.cancelRefund(id, user.getRowId());

            assertThat(balance(bank)).isEqualTo(-100_000L);
            assertThat(balance(card)).isZero();
            assertThat(markOf(id)).isEqualTo(LocalDate.of(2026, 8, 31));
        }

        @Test
        @DisplayName("기한 뒤 환불(붙잡음) → 취소해도 카드·통장 그대로")
        void refundAfterWindowThenCancel() {
            Long id = augustPaid();
            today(LocalDate.of(2026, 10, 1));

            var info = expenseService.refund(id, user.getRowId(), null);
            assertThat(info.refundedAmount()).isNull();
            assertThat(balance(card)).isZero();

            expenseService.cancelRefund(id, user.getRowId());

            assertThat(balance(card)).isZero();
            assertThat(balance(bank)).isEqualTo(-100_000L);
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
    }

    // === 순 납부액 — 23차 결함 2 ====================================================

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

        spend(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 7), 30_000L);

        var next = billing().nextCycle();
        assertThat(next.periodStart()).isEqualTo(SEP);
        assertThat(next.alreadyPaidAmount()).as("순 납부액 = 50,000 − 50,000").isZero();
        assertThat(next.amount()).isEqualTo(30_000L);
    }
}
