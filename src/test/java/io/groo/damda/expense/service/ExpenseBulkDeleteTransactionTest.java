package io.groo.damda.expense.service;

import io.groo.core.exception.EntityNotFoundException;
import io.groo.core.exception.ForbiddenException;
import io.groo.core.exception.InvalidValueException;
import io.groo.core.time.ServiceClock;
import io.groo.core.time.UserClock;
import io.groo.core.type.YNType;
import io.groo.damda.asset.domain.Asset;
import io.groo.damda.asset.repository.AssetRepository;
import io.groo.damda.asset.service.AssetBalanceHistoryService;
import io.groo.damda.asset.type.AssetType;
import io.groo.damda.card.service.CardPaymentService;
import io.groo.damda.common.exception.DeskErrorCode;
import io.groo.damda.expense.domain.Expense;
import io.groo.damda.expense.domain.ExpenseCategory;
import io.groo.damda.expense.repository.ExpenseCategoryRepository;
import io.groo.damda.expense.repository.ExpenseRepository;
import io.groo.damda.expense.repository.ExpenseSplitRepository;
import io.groo.damda.expense.service.dto.ExpenseServiceDto;
import io.groo.damda.expense.service.dto.ExpenseSplitServiceDto;
import io.groo.damda.expense.type.ExpenseType;
import io.groo.damda.user.domain.User;
import io.groo.damda.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
 * 일괄 삭제는 <b>전부 아니면 전무</b> — 진짜 트랜잭션·진짜 질의로 잠근다.
 *
 * <p>목 리포지토리는 rollback-only 표식도, 커밋 뒤 실제 행 상태도 만들지 않는다. 테스트가 트랜잭션을 들지
 * 않아 서비스가 자기 경계로 커밋·롤백하는 실제 모양 그대로 돈다. 오늘은 {@link UserClock} ·
 * {@link ServiceClock} 을 함께 고정한다(데이터의 {@code now()} 도 같이 고정된다).
 */
@SpringBootTest
@ActiveProfiles("test")
class ExpenseBulkDeleteTransactionTest {

    private static final LocalDateTime FAR = LocalDateTime.of(2100, 1, 1, 0, 0);
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 14);

    @MockitoBean private UserClock userClock;
    @MockitoBean private ServiceClock serviceClock;

    @Autowired private ExpenseService expenseService;
    @Autowired private ExpenseSplitService expenseSplitService;
    @Autowired private CardPaymentService cardPaymentService;
    @Autowired private AssetRepository assetRepository;
    @Autowired private AssetBalanceHistoryService balanceHistoryService;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private ExpenseSplitRepository expenseSplitRepository;
    @Autowired private ExpenseCategoryRepository expenseCategoryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionTemplate tx;
    @Autowired private jakarta.persistence.EntityManager entityManager;

    private User user;
    private Long categoryRowId;
    private Asset bank;

    @BeforeEach
    void setUp() {
        given(userClock.zoneOf(any())).willReturn(ZoneId.of("Asia/Seoul"));
        given(userClock.zoneOfTimezone(any())).willReturn(ZoneId.of("Asia/Seoul"));
        given(serviceClock.zone()).willReturn(ZoneId.of("Asia/Seoul"));
        today(TODAY);
        user = newUser();
        categoryRowId = tx.execute(s -> expenseCategoryRepository.save(ExpenseCategory.createCategory(
            user, "식비", null, null, ExpenseType.EXPENSE, null)).getRowId());
        bank = newAsset(user, "통장", AssetType.BANK_ACCOUNT, null, null);
    }

    // === 픽스처 =================================================================

    private User newUser() {
        String id = "bd" + UUID.randomUUID().toString().substring(0, 8);
        return tx.execute(s -> userRepository.save(User.createUser(null, id, "테스터", id + "@porest.com")));
    }

    private Asset newAsset(User owner, String name, AssetType type, Integer paymentDay, Asset paymentAsset) {
        return tx.execute(s -> assetRepository.save(Asset.createAsset(
            owner, name, type, 0L, "KRW", null, null, null, null, 0, YNType.Y,
            null, type == AssetType.CREDIT_CARD ? 10_000_000L : null, paymentDay, paymentAsset)));
    }

    /** 카드 등록일 — 등록 전 회차 판정의 기준. 감사 컬럼이라 직접 고친다. */
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
        given(serviceClock.today()).willReturn(d);
        given(serviceClock.now()).willReturn(d.atTime(12, 0));
    }

    private Long spend(Asset asset, LocalDate date, long amount) {
        return expenseService.createExpense(new ExpenseServiceDto.CreateCommand(
            user.getRowId(), categoryRowId, asset.getRowId(), ExpenseType.EXPENSE, amount, "테스트",
            date.atTime(10, 0), "가맹점", null, null, null, null, null, null, null)).rowId();
    }

    private long balance(Asset asset) {
        return tx.execute(s -> balanceHistoryService.balanceAt(
            assetRepository.findById(asset.getRowId()).orElseThrow(), FAR).total());
    }

    private boolean exists(Long expenseId) {
        return tx.execute(s -> expenseRepository.findById(expenseId).isPresent());
    }

    private ExpenseServiceDto.BulkDeleteResult bulkDelete(Long... ids) {
        return expenseService.bulkDeleteExpenses(
            new ExpenseServiceDto.BulkDeleteCommand(user.getRowId(), List.of(ids)));
    }

    // === 정상 ====================================================================

    @Test
    @DisplayName("셋을 지우면 셋 다 지워지고 잔액 이력과 분할도 사라진다 — deletedCount 3, 환급 없음")
    void deletesAllWithBalanceAndSplits() {
        Long a = spend(bank, TODAY, 10_000L);
        Long b = spend(bank, TODAY, 20_000L);
        Long c = spend(bank, TODAY, 30_000L);
        Long other = tx.execute(s -> expenseCategoryRepository.save(ExpenseCategory.createCategory(
            user, "교통", null, null, ExpenseType.EXPENSE, null)).getRowId());
        expenseSplitService.replaceSplits(new ExpenseSplitServiceDto.ReplaceCommand(c, user.getRowId(),
            List.of(new ExpenseSplitServiceDto.SplitCommand(null, categoryRowId, 20_000L, "밥", 0),
                new ExpenseSplitServiceDto.SplitCommand(null, other, 10_000L, "차", 1))));
        assertThat(balance(bank)).isEqualTo(-60_000L);

        var result = bulkDelete(a, b, c);

        assertThat(result.deletedCount()).isEqualTo(3);
        assertThat(result.refundedAmount()).isNull();
        assertThat(exists(a) || exists(b) || exists(c)).isFalse();
        assertThat(balance(bank)).as("잔액 이력 제거").isZero();
        List<io.groo.damda.expense.domain.ExpenseSplit> left = tx.execute(s -> expenseSplitRepository.findByExpense(c));
        assertThat(left).as("분할 제거").isEmpty();
    }

    @Test
    @DisplayName("같은 아이디가 여러 번 와도 한 번만 지운다")
    void duplicateIdsDeleteOnce() {
        Long a = spend(bank, TODAY, 10_000L);
        Long b = spend(bank, TODAY, 20_000L);

        var result = bulkDelete(a, a, b, a);

        assertThat(result.deletedCount()).isEqualTo(2);
        assertThat(exists(a) || exists(b)).isFalse();
        assertThat(balance(bank)).isZero();
    }

    // === 카드 ====================================================================

    @Test
    @DisplayName("열린 회차 선결제가 남는 카드 거래 둘 — 환급 합이 단건을 두 번 부른 값과 같다")
    void refundIsSumOfSingleDeletes() {
        Asset cardBulk = newAsset(user, "카드A", AssetType.CREDIT_CARD, 12, bank);
        Asset cardSingle = newAsset(user, "카드B", AssetType.CREDIT_CARD, 12, bank);
        Long b1 = spend(cardBulk, LocalDate.of(2026, 9, 5), 50_000L);
        Long b2 = spend(cardBulk, LocalDate.of(2026, 9, 5), 30_000L);
        Long s1 = spend(cardSingle, LocalDate.of(2026, 9, 5), 50_000L);
        Long s2 = spend(cardSingle, LocalDate.of(2026, 9, 5), 30_000L);
        cardPaymentService.payCard(cardBulk.getRowId(), user.getRowId(), 80_000L, LocalDate.of(2026, 10, 12));
        cardPaymentService.payCard(cardSingle.getRowId(), user.getRowId(), 80_000L, LocalDate.of(2026, 10, 12));
        today(LocalDate.of(2026, 9, 6));

        Long first = expenseService.deleteExpense(s1, user.getRowId());
        Long second = expenseService.deleteExpense(s2, user.getRowId());
        long singles = (first == null ? 0L : first) + (second == null ? 0L : second);
        long bankAfterSingles = balance(bank);

        var result = bulkDelete(b1, b2);

        assertThat(result.deletedCount()).isEqualTo(2);
        assertThat(singles).isEqualTo(80_000L);
        assertThat(result.refundedAmount()).as("건마다 난 환급의 합").isEqualTo(singles);
        assertThat(balance(bank)).as("통장 돈 이동도 단건과 같다 — 둘 다 환급이 들어왔다")
            .isEqualTo(bankAfterSingles + singles).isZero();
        assertThat(balance(cardBulk)).isEqualTo(balance(cardSingle));
    }

    @Test
    @DisplayName("닫힌 회차 카드 거래 — 환급 null, 카드·통장이 그대로(상계)")
    void closedCycleRefundsNothing() {
        Asset card = newAsset(user, "카드", AssetType.CREDIT_CARD, 12, bank);
        registeredOn(card, LocalDate.of(2026, 6, 1));
        today(LocalDate.of(2026, 8, 5));
        Long paid = spend(card, LocalDate.of(2026, 8, 5), 100_000L);
        today(LocalDate.of(2026, 9, 12));
        cardPaymentService.processDueCardPayments(LocalDate.of(2026, 9, 12));
        today(TODAY);
        Long closed = spend(card, LocalDate.of(2026, 8, 20), 25_000L);
        assertThat(balance(bank)).isEqualTo(-100_000L);

        var result = bulkDelete(paid, closed);

        assertThat(result.deletedCount()).isEqualTo(2);
        assertThat(result.refundedAmount()).isNull();
        assertThat(balance(bank)).isEqualTo(-100_000L);
        assertThat(balance(card)).isZero();
    }

    // === 전부 아니면 전무 ============================================================

    @Test
    @DisplayName("하나가 없는 거래 — 404 EXPENSE_NOT_FOUND, 나머지도 안 지워진다")
    void missingRejectsAll() {
        Long a = spend(bank, TODAY, 10_000L);
        Long b = spend(bank, TODAY, 20_000L);

        assertThatThrownBy(() -> bulkDelete(a, 987_654_321L, b))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessage(DeskErrorCode.EXPENSE_NOT_FOUND.getMessageKey());

        assertThat(exists(a) && exists(b)).isTrue();
        assertThat(balance(bank)).isEqualTo(-30_000L);
    }

    @Test
    @DisplayName("하나가 남의 거래 — 403 EXPENSE_ACCESS_DENIED, 나머지도 안 지워진다")
    void othersRejectsAll() {
        Long a = spend(bank, TODAY, 10_000L);
        Long b = spend(bank, TODAY, 20_000L);
        User stranger = newUser();
        Long theirs = tx.execute(s -> expenseRepository.save(Expense.createExpense(stranger, null, null,
            ExpenseType.EXPENSE, 5_000L, "남", TODAY.atTime(9, 0), "남의 가맹점",
            null, null, null, null, null)).getRowId());

        assertThatThrownBy(() -> bulkDelete(a, theirs, b))
            .isInstanceOf(ForbiddenException.class)
            .hasMessage(DeskErrorCode.EXPENSE_ACCESS_DENIED.getMessageKey());

        assertThat(exists(a) && exists(b) && exists(theirs)).isTrue();
        assertThat(balance(bank)).isEqualTo(-30_000L);
    }

    @Test
    @DisplayName("하나가 시스템이 만든 거래 — 400, 나머지도 안 지워진다")
    void autoGeneratedRejectsAll() {
        Asset card = newAsset(user, "카드", AssetType.CREDIT_CARD, 12, bank);
        Long a = spend(bank, TODAY, 10_000L);
        Long carry = tx.execute(s -> {
            Expense e = Expense.createExpense(user, null, card, ExpenseType.EXPENSE, 50_000L,
                "이전 미결제 사용액", LocalDateTime.of(2026, 9, 1, 0, 0), "이전 미결제 사용액",
                null, null, null, null, null);
            e.markAutoGenerated(Expense.AUTO_SOURCE_CARD_CARRYOVER);
            return expenseRepository.save(e).getRowId();
        });
        Long b = spend(bank, TODAY, 20_000L);

        assertThatThrownBy(() -> bulkDelete(a, carry, b))
            .isInstanceOf(InvalidValueException.class)
            .hasMessage(DeskErrorCode.EXPENSE_CARRYOVER_READONLY.getMessageKey());

        assertThat(exists(a) && exists(b) && exists(carry)).isTrue();
        assertThat(balance(bank)).isEqualTo(-30_000L);
    }
}
