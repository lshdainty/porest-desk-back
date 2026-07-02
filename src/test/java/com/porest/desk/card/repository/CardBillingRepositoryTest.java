package com.porest.desk.card.repository;

import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.type.AssetType;
import com.porest.desk.card.domain.CardBilling;
import com.porest.desk.card.type.BillingStatus;
import com.porest.desk.common.config.QueryDslConfig;
import com.porest.desk.common.config.database.JpaAuditingConfig;
import com.porest.desk.common.config.database.LoginUserAuditorAware;
import com.porest.desk.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CardBilling QueryDsl 리포 슬라이스 테스트.
 *
 * <p>카드별 청구 이력 조회(카드 격리·결제일 내림차순·soft-delete 제외), COMPLETED 멱등성 체크,
 * 상태별 조회를 검증한다. CardBilling 은 soft-delete 도메인 메서드가 없어 isDeleted 는 리플렉션으로 세팅한다.
 * H2(application-test.yml, create-drop)에서 실제 SQL 로 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        CardBillingQueryDslRepository.class})
@ActiveProfiles("test")
class CardBillingRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private CardBillingRepository repository;

    private User persistUser(String userId) {
        return em.persist(User.createUser(null, userId, "테스터", userId + "@porest.com"));
    }

    private Asset persistCard(User user, String name) {
        return em.persist(Asset.createAsset(user, name, AssetType.CREDIT_CARD, 0L, "KRW",
                null, null, null, 0, YNType.Y, null, null, null, null));
    }

    private CardBilling persistCompleted(Asset card, Long amount, LocalDate paymentDate) {
        return em.persist(CardBilling.completed(card, null, amount,
                paymentDate.minusMonths(1), paymentDate.minusDays(1), paymentDate, null));
    }

    private CardBilling persistFailed(Asset card, Long amount, LocalDate paymentDate) {
        return em.persist(CardBilling.failed(card, null, amount,
                paymentDate.minusMonths(1), paymentDate.minusDays(1), paymentDate, "잔액부족"));
    }

    private CardBilling persistSkipped(Asset card, LocalDate paymentDate) {
        return em.persist(CardBilling.skipped(card, null,
                paymentDate.minusMonths(1), paymentDate.minusDays(1), paymentDate));
    }

    @Test
    @DisplayName("findByCardAssetRowId 는 해당 카드 청구만 결제일 내림차순(동일일자는 rowId 내림차순)으로 반환한다")
    void findByCardOrderedAndIsolated() {
        User user = persistUser("u1");
        Asset cardA = persistCard(user, "카드A");
        Asset cardB = persistCard(user, "카드B");

        persistCompleted(cardA, 1000L, LocalDate.of(2026, 6, 10)); // b1
        persistCompleted(cardA, 2000L, LocalDate.of(2026, 6, 20)); // b2 (동일일자, 먼저 저장)
        persistCompleted(cardA, 3000L, LocalDate.of(2026, 6, 20)); // b3 (동일일자, rowId 큼)
        persistCompleted(cardB, 9999L, LocalDate.of(2026, 6, 15)); // 다른 카드 → 제외
        em.flush();
        em.clear();

        List<CardBilling> result = repository.findByCardAssetRowId(cardA.getRowId());

        assertThat(result).extracting(CardBilling::getBillingAmount)
                .containsExactly(3000L, 2000L, 1000L);
    }

    @Test
    @DisplayName("findByCardAssetRowId 는 soft delete 된 청구를 제외한다")
    void findByCardExcludesSoftDeleted() {
        User user = persistUser("u1");
        Asset card = persistCard(user, "카드A");
        persistCompleted(card, 1000L, LocalDate.of(2026, 6, 10));
        CardBilling deleted = CardBilling.completed(card, null, 5000L,
                LocalDate.of(2026, 5, 20), LocalDate.of(2026, 6, 19), LocalDate.of(2026, 6, 20), null);
        ReflectionTestUtils.setField(deleted, "isDeleted", YNType.Y);
        em.persist(deleted);
        em.flush();
        em.clear();

        List<CardBilling> result = repository.findByCardAssetRowId(card.getRowId());

        assertThat(result).extracting(CardBilling::getBillingAmount).containsExactly(1000L);
    }

    @Test
    @DisplayName("existsCompletedByCardAndPaymentDate 는 해당 카드·결제일에 COMPLETED 가 있으면 true 를 반환한다")
    void existsCompletedTrue() {
        User user = persistUser("u1");
        Asset card = persistCard(user, "카드A");
        persistCompleted(card, 1000L, LocalDate.of(2026, 6, 10));
        em.flush();
        em.clear();

        assertThat(repository.existsCompletedByCardAndPaymentDate(card.getRowId(), LocalDate.of(2026, 6, 10)))
                .isTrue();
    }

    @Test
    @DisplayName("existsCompletedByCardAndPaymentDate 는 다른 결제일·다른 상태·다른 카드·soft delete 에 대해 false 를 반환한다")
    void existsCompletedFalseCases() {
        User user = persistUser("u1");
        Asset cardA = persistCard(user, "카드A");
        Asset cardB = persistCard(user, "카드B");

        persistCompleted(cardA, 1000L, LocalDate.of(2026, 6, 10)); // 정상 COMPLETED
        persistFailed(cardA, 2000L, LocalDate.of(2026, 6, 15));    // FAILED 상태
        persistCompleted(cardB, 3000L, LocalDate.of(2026, 6, 10)); // 다른 카드
        CardBilling deletedCompleted = CardBilling.completed(cardA, null, 4000L,
                LocalDate.of(2026, 5, 20), LocalDate.of(2026, 6, 19), LocalDate.of(2026, 6, 20), null);
        ReflectionTestUtils.setField(deletedCompleted, "isDeleted", YNType.Y);
        em.persist(deletedCompleted);
        em.flush();
        em.clear();

        Long cardAId = cardA.getRowId();
        // 청구 없는 날짜
        assertThat(repository.existsCompletedByCardAndPaymentDate(cardAId, LocalDate.of(2026, 6, 11))).isFalse();
        // 해당 날짜에 청구는 있으나 COMPLETED 아님(FAILED)
        assertThat(repository.existsCompletedByCardAndPaymentDate(cardAId, LocalDate.of(2026, 6, 15))).isFalse();
        // soft delete 된 COMPLETED
        assertThat(repository.existsCompletedByCardAndPaymentDate(cardAId, LocalDate.of(2026, 6, 20))).isFalse();
    }

    @Test
    @DisplayName("findByStatus 는 상태로 필터링하고 결제일 내림차순으로 반환한다")
    void findByStatusFiltersAndOrders() {
        User user = persistUser("u1");
        Asset cardA = persistCard(user, "카드A");
        Asset cardB = persistCard(user, "카드B");

        persistCompleted(cardA, 1000L, LocalDate.of(2026, 6, 10)); // COMPLETED
        persistFailed(cardA, 2000L, LocalDate.of(2026, 6, 20));    // FAILED
        persistSkipped(cardA, LocalDate.of(2026, 6, 15));          // SKIPPED
        persistFailed(cardB, 3000L, LocalDate.of(2026, 6, 25));    // FAILED (다른 카드)
        em.flush();
        em.clear();

        List<CardBilling> result = repository.findByStatus(BillingStatus.FAILED);

        assertThat(result).extracting(CardBilling::getPaymentDate)
                .containsExactly(LocalDate.of(2026, 6, 25), LocalDate.of(2026, 6, 20));
        assertThat(result).allSatisfy(b -> assertThat(b.getStatus()).isEqualTo(BillingStatus.FAILED));
    }
}
