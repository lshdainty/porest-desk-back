package com.porest.desk.asset.repository;

import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.domain.AssetTransfer;
import com.porest.desk.asset.type.AssetType;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AssetTransfer QueryDsl 리포 슬라이스 테스트 — H2 에서 이체 내역 조회(기간 필터·정렬·소유권·soft-delete) 검증.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        AssetTransferQueryDslRepository.class})
@ActiveProfiles("test")
class AssetTransferRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private AssetTransferRepository repository;

    private User persistUser(String userId) {
        return em.persist(User.createUser(null, userId, "테스터", userId + "@porest.com"));
    }

    private Asset persistAsset(User user, String name) {
        return em.persist(Asset.createAsset(user, name, AssetType.BANK_ACCOUNT, 100_000L, "KRW",
                null, null, null, 0, YNType.Y, null, null, null, null));
    }

    private AssetTransfer persistTransfer(User user, Asset from, Asset to, long amount, LocalDate date) {
        return em.persist(AssetTransfer.createTransfer(user, from, to, amount, 0L, "이체", date.atStartOfDay()));
    }

    @Test
    @DisplayName("findByUser — 본인 이체만 transferDate 내림차순(같은 날짜는 최신 등록 우선)으로 반환")
    void findByUserOrderedDesc() {
        User user = persistUser("u1");
        User other = persistUser("u2");
        Asset from = persistAsset(user, "출금계좌");
        Asset to = persistAsset(user, "입금계좌");
        persistTransfer(user, from, to, 1000L, LocalDate.of(2026, 6, 1));
        persistTransfer(user, from, to, 2000L, LocalDate.of(2026, 6, 20));
        persistTransfer(user, from, to, 3000L, LocalDate.of(2026, 6, 10)); // 같은 날 먼저 등록 → rowId 작음
        persistTransfer(user, from, to, 4000L, LocalDate.of(2026, 6, 10)); // 같은 날 나중 등록 → rowId 큼
        persistTransfer(other, from, to, 9999L, LocalDate.of(2026, 6, 15)); // 타인 → 제외
        em.flush();
        em.clear();

        List<AssetTransfer> result = repository.findByUser(user.getRowId(), null, null);

        assertThat(result).hasSize(4);
        // transferDate desc, 동일 날짜(6/10)는 rowId desc → 4000(나중 등록)이 3000보다 먼저
        assertThat(result).extracting(AssetTransfer::getAmount)
                .containsExactly(2000L, 4000L, 3000L, 1000L);
    }

    @Test
    @DisplayName("findByUser — 기간(start~end) 경계는 포함, 범위 밖은 제외")
    void findByUserDateRangeBoundaryInclusive() {
        User user = persistUser("u1");
        Asset from = persistAsset(user, "출금계좌");
        Asset to = persistAsset(user, "입금계좌");
        persistTransfer(user, from, to, 100L, LocalDate.of(2026, 6, 1));  // start 경계 포함
        persistTransfer(user, from, to, 200L, LocalDate.of(2026, 6, 30)); // end 경계 포함
        persistTransfer(user, from, to, 300L, LocalDate.of(2026, 5, 31)); // 범위 전 → 제외
        persistTransfer(user, from, to, 400L, LocalDate.of(2026, 7, 1));  // 범위 후 → 제외
        em.flush();
        em.clear();

        List<AssetTransfer> result = repository.findByUser(
                user.getRowId(), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(result).extracting(AssetTransfer::getAmount)
                .containsExactly(200L, 100L); // desc → 6/30 먼저
    }

    @Test
    @DisplayName("soft delete 된 이체는 findByUser / findById 모두에서 제외된다")
    void softDeleteExcluded() {
        User user = persistUser("u1");
        Asset from = persistAsset(user, "출금계좌");
        Asset to = persistAsset(user, "입금계좌");
        AssetTransfer live = persistTransfer(user, from, to, 100L, LocalDate.of(2026, 6, 1));
        AssetTransfer removed = persistTransfer(user, from, to, 200L, LocalDate.of(2026, 6, 2));
        em.flush();

        repository.delete(removed); // deleteTransfer() → isDeleted = Y
        em.flush();
        em.clear();

        assertThat(repository.findByUser(user.getRowId(), null, null))
                .extracting(AssetTransfer::getRowId).containsExactly(live.getRowId());
        assertThat(repository.findById(removed.getRowId())).isEmpty();
        assertThat(repository.findById(live.getRowId())).isPresent();
    }
}
