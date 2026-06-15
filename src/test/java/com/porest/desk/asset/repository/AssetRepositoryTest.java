package com.porest.desk.asset.repository;

import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asset QueryDsl 리포 슬라이스 테스트 — H2(create-drop)에서 실제 SQL 검증.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        AssetQueryDslRepository.class})
@ActiveProfiles("test")
class AssetRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private AssetRepository repository;

    private User persistUser(String userId) {
        return em.persist(User.createUser(null, userId, "테스터", userId + "@porest.com"));
    }

    private Asset persistAsset(User user, String name, int sortOrder) {
        Asset a = Asset.createAsset(user, name, AssetType.BANK_ACCOUNT, 100_000L, "KRW",
                null, null, null, sortOrder, YNType.Y, null, null, null, null);
        return em.persist(a);
    }

    @Test
    @DisplayName("save 후 findById 로 조회된다")
    void saveAndFindById() {
        User user = persistUser("u1");
        Asset asset = Asset.createAsset(user, "주거래통장", AssetType.BANK_ACCOUNT, 500_000L, "KRW",
                null, null, null, 0, YNType.Y, null, null, null, null);
        repository.save(asset);
        em.flush();
        em.clear();

        Optional<Asset> found = repository.findById(asset.getRowId());

        assertThat(found).isPresent();
        assertThat(found.get().getAssetName()).isEqualTo("주거래통장");
    }

    @Test
    @DisplayName("findByUser 는 본인 자산만 sortOrder 오름차순으로 반환한다")
    void findByUserOrdered() {
        User user = persistUser("u1");
        User other = persistUser("u2");
        persistAsset(user, "투자계좌", 2);
        persistAsset(user, "주거래통장", 0);
        persistAsset(user, "비상금", 1);
        persistAsset(other, "남의통장", 0);
        em.flush();
        em.clear();

        List<Asset> result = repository.findByUser(user.getRowId());

        assertThat(result).hasSize(3);
        assertThat(result).extracting(Asset::getAssetName)
                .containsExactly("주거래통장", "비상금", "투자계좌");
    }

    @Test
    @DisplayName("soft delete 후에는 findById 로 조회되지 않는다")
    void softDeleteExcludedFromFind() {
        User user = persistUser("u1");
        Asset asset = persistAsset(user, "주거래통장", 0);
        em.flush();

        repository.delete(asset);
        em.flush();
        em.clear();

        assertThat(repository.findById(asset.getRowId())).isEmpty();
    }
}
