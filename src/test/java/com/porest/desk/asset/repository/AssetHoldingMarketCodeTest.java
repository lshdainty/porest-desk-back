package com.porest.desk.asset.repository;

import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.domain.AssetHolding;
import com.porest.desk.asset.type.AssetType;
import com.porest.desk.asset.type.HoldingType;
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

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시장코드가 <b>컬럼까지</b> 간다는 걸 못 박는다.
 *
 * <p>{@code asset.market_code}·{@code asset_holding.market_code} 는 마이그레이션
 * ({@code V2026.08.24_04})으로 생겼는데 한동안 <b>쓰는 코드가 없었다</b> — 백필이 유일한
 * 기록자였다. 엔티티에 필드만 있고 저장 경로가 없어도 서비스 단위 테스트는 초록불이라,
 * "넣었다" 가 아니라 "넣은 게 DB 로 갔다" 를 여기서 확인한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class})
@ActiveProfiles("test")
class AssetHoldingMarketCodeTest {

    @Autowired private TestEntityManager em;
    @Autowired private AssetHoldingRepository holdingRepository;

    private Asset persistInvestment() {
        User user = em.persist(User.createUser(null, "mc-user", "테스터", "mc@porest.com"));
        return em.persist(Asset.createAsset(user, "증권계좌", AssetType.INVESTMENT, 0L, "KRW",
            null, null, null, null, 0, YNType.Y, null, null, null, null));
    }

    private Object rawMarketCode(String table, Long rowId) {
        return em.getEntityManager()
            .createNativeQuery("select market_code from " + table + " where row_id = :id")
            .setParameter("id", rowId)
            .getSingleResult();
    }

    @Test
    @DisplayName("보유 저장 — 시장코드가 asset_holding.market_code 컬럼에 남는다")
    void holdingMarketCodeReachesColumn() {
        Asset asset = persistInvestment();

        AssetHolding saved = holdingRepository.save(AssetHolding.create(
            asset, HoldingType.STOCK, YNType.Y, "NAS", "SPY",
            BigDecimal.valueOf(3), null, null, 0L, 0));
        em.flush();
        em.clear();

        assertThat(rawMarketCode("asset_holding", saved.getRowId())).isEqualTo("NAS");
        assertThat(holdingRepository.findById(saved.getRowId()))
            .get()
            .extracting(AssetHolding::getMarketCode, AssetHolding::getSymbol)
            .containsExactly("NAS", "SPY");
    }

    @Test
    @DisplayName("보유 수정 — 시장이 바뀌면 컬럼도 따라 바뀐다")
    void updateHoldingRewritesColumn() {
        Asset asset = persistInvestment();
        AssetHolding holding = holdingRepository.save(AssetHolding.create(
            asset, HoldingType.STOCK, YNType.Y, "AMS", "SPY",
            BigDecimal.valueOf(3), null, null, 0L, 0));
        em.flush();

        holding.updateHolding(HoldingType.STOCK, YNType.Y, "NAS", "SPY",
            BigDecimal.valueOf(5), null, null, null, 0);
        em.flush();
        em.clear();

        assertThat(rawMarketCode("asset_holding", holding.getRowId())).isEqualTo("NAS");
    }

    @Test
    @DisplayName("시장을 확정 못 하면 NULL 로 남는다 — 추측한 값을 눌러 두지 않는다")
    void ambiguousStaysNull() {
        Asset asset = persistInvestment();

        AssetHolding saved = holdingRepository.save(AssetHolding.create(
            asset, HoldingType.STOCK, YNType.Y, null, "SPY",
            BigDecimal.ONE, null, null, 0L, 0));
        em.flush();
        em.clear();

        assertThat(rawMarketCode("asset_holding", saved.getRowId())).isNull();
    }

    @Test
    @DisplayName("레거시 단일 연동 — linkSecurities 가 asset.market_code 에 쓴다")
    void linkSecuritiesReachesColumn() {
        Asset asset = persistInvestment();

        asset.linkSecurities("KOSPI", "005930", 10L);
        em.flush();
        em.clear();

        assertThat(rawMarketCode("asset", asset.getRowId())).isEqualTo("KOSPI");

        Asset reloaded = em.find(Asset.class, asset.getRowId());
        reloaded.unlinkSecurities();
        em.flush();
        em.clear();

        assertThat(rawMarketCode("asset", asset.getRowId())).isNull();
    }
}
