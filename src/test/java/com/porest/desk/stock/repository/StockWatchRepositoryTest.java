package com.porest.desk.stock.repository;

import com.porest.desk.common.config.QueryDslConfig;
import com.porest.desk.common.config.database.JpaAuditingConfig;
import com.porest.desk.common.config.database.LoginUserAuditorAware;
import com.porest.desk.stock.client.dto.InstrumentRecord;
import com.porest.desk.stock.type.MasterSource;
import com.porest.desk.stock.domain.StockMaster;
import com.porest.desk.stock.domain.StockWatchGroup;
import com.porest.desk.stock.domain.StockWatchItem;
import com.porest.desk.stock.type.StockMarket;
import com.porest.desk.stock.type.StockSecurityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관심목록 리포 슬라이스 테스트.
 *
 * <p>그룹 소유·활성 필터, 종목-마스터 조인 정렬, 삭제분 포함 재추가 대조 조회,
 * 마스터 심볼 해석(시장 지정/미지정)을 H2 실제 SQL 로 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        StockWatchGroupQueryDslRepository.class, StockWatchItemQueryDslRepository.class,
        StockMasterQueryDslRepository.class})
@ActiveProfiles("test")
class StockWatchRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private StockWatchGroupRepository groupRepository;
    @Autowired private StockWatchItemRepository itemRepository;
    @Autowired private StockMasterRepository stockMasterRepository;

    private StockMaster persistStock(StockMarket market, String symbol, String nameKr) {
        return em.persist(StockMaster.create(MasterSource.KIS,
            InstrumentRecord.kis(market, symbol, null, null, nameKr, null, StockSecurityType.STOCK,
                market.getCountryCode().equals("KR") ? "KRW" : "USD")));
    }

    private StockWatchGroup persistGroup(long user, String name, int order) {
        return em.persist(StockWatchGroup.create(user, name, order));
    }

    private StockWatchItem persistItem(StockWatchGroup group, StockMaster stock, int order) {
        return em.persist(StockWatchItem.create(group.getRowId(), stock.getRowId(), order));
    }

    @Test
    @DisplayName("그룹 조회 — 본인 활성 그룹만 정렬 순으로, 삭제 그룹·타인 그룹 제외")
    void findsOwnActiveGroupsOnly() {
        persistGroup(1L, "관심", 1);
        persistGroup(1L, "미국 기술주", 0);
        StockWatchGroup deleted = persistGroup(1L, "삭제됨", 2);
        deleted.delete();
        persistGroup(2L, "남의 그룹", 0);
        em.flush();
        em.clear();

        List<StockWatchGroup> groups = groupRepository.findAllActiveByUser(1L);

        assertThat(groups).extracting(StockWatchGroup::getGroupName)
            .containsExactly("미국 기술주", "관심");
        assertThat(groupRepository.countActiveByUser(1L)).isEqualTo(2);
        assertThat(groupRepository.existsActiveByUserAndName(1L, "관심")).isTrue();
        assertThat(groupRepository.existsActiveByUserAndName(1L, "삭제됨")).isFalse();
    }

    @Test
    @DisplayName("종목 조회 — 마스터와 조인해 그룹 정렬 → 종목 정렬 순으로, 삭제 종목 제외")
    void findsItemsJoinedWithStockInOrder() {
        StockMaster apple = persistStock(StockMarket.NAS, "AAPL", "애플");
        StockMaster samsung = persistStock(StockMarket.KOSPI, "005930", "삼성전자");
        StockWatchGroup first = persistGroup(1L, "관심", 0);
        StockWatchGroup second = persistGroup(1L, "미국", 1);
        persistItem(first, samsung, 0);
        StockWatchItem deleted = persistItem(first, apple, 1);
        deleted.delete();
        persistItem(second, apple, 0);
        em.flush();
        em.clear();

        List<StockWatchItemRepository.ItemWithStock> rows = itemRepository.findAllActiveByUserWithStock(1L);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).stock().getNameKr()).isEqualTo("삼성전자");
        assertThat(rows.get(0).item().getGroupRowId()).isEqualTo(first.getRowId());
        assertThat(rows.get(1).stock().getNameKr()).isEqualTo("애플");
        assertThat(rows.get(1).item().getGroupRowId()).isEqualTo(second.getRowId());
    }

    @Test
    @DisplayName("재추가 대조 조회는 삭제 행을 포함한다 — (group, stock) 유니크 제약 방어")
    void findsDeletedItemForRestore() {
        StockMaster apple = persistStock(StockMarket.NAS, "AAPL", "애플");
        StockWatchGroup group = persistGroup(1L, "관심", 0);
        StockWatchItem item = persistItem(group, apple, 0);
        item.delete();
        em.flush();
        em.clear();

        assertThat(itemRepository.findByGroupAndStockIncludingDeleted(group.getRowId(), apple.getRowId()))
            .isPresent()
            .get()
            .satisfies(found -> assertThat(found.isDeleted()).isTrue());
        assertThat(itemRepository.findActiveById(item.getRowId())).isEmpty();
        assertThat(itemRepository.countActiveByGroup(group.getRowId())).isZero();
    }

    @Test
    @DisplayName("마스터 심볼 해석 — 시장 지정 시 정확 일치, 미지정 시 심볼 일치 전부 (비활성 제외)")
    void resolvesStockBySymbol() {
        persistStock(StockMarket.KOSPI, "005930", "삼성전자");
        persistStock(StockMarket.SHS, "600519", "귀주모태주");
        StockMaster delisted = persistStock(StockMarket.NAS, "GONE", "상장폐지");
        delisted.deactivate();
        em.flush();
        em.clear();

        assertThat(stockMasterRepository.findActiveByMarketAndSymbol(StockMarket.KOSPI, "005930"))
            .isPresent()
            .get()
            .satisfies(s -> assertThat(s.getNameKr()).isEqualTo("삼성전자"));
        assertThat(stockMasterRepository.findActiveByMarketAndSymbol(StockMarket.KOSDAQ, "005930")).isEmpty();
        assertThat(stockMasterRepository.findAllActiveBySymbol("600519")).hasSize(1);
        assertThat(stockMasterRepository.findAllActiveBySymbol("GONE")).isEmpty();
    }
}
