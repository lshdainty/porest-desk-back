package com.porest.desk.stock.repository;

import com.porest.core.type.YNType;
import com.porest.desk.common.config.QueryDslConfig;
import com.porest.desk.common.config.database.JpaAuditingConfig;
import com.porest.desk.common.config.database.LoginUserAuditorAware;
import com.porest.desk.stock.client.dto.InstrumentRecord;
import com.porest.desk.stock.type.MasterSource;
import com.porest.desk.stock.domain.StockMaster;
import com.porest.desk.stock.type.StockMarket;
import com.porest.desk.stock.type.StockSecurityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StockMaster QueryDsl 리포 슬라이스 테스트.
 *
 * <p>search 의 키워드(한글명·영문명·심볼)·국가/유형 필터·일치 강도 정렬·비활성/삭제 제외와,
 * 동기화 대조용 전체 조회가 비활성 행을 포함하는지를 H2 실제 SQL 로 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        StockMasterQueryDslRepository.class})
@ActiveProfiles("test")
class StockMasterRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private StockMasterRepository repository;

    private StockMaster persist(StockMarket market, String symbol, String nameKr, String nameEn,
                                StockSecurityType type, String currency) {
        StockMaster stock = StockMaster.create(MasterSource.KIS,
            InstrumentRecord.kis(market, symbol, null, market.name() + symbol, nameKr, nameEn, type, currency));
        return em.persist(stock);
    }

    private StockMasterSearchCondition keyword(String kw) {
        return new StockMasterSearchCondition(kw, null, null);
    }

    @Test
    @DisplayName("한글명·영문명·심볼 어느 쪽으로도 부분 일치 검색된다")
    void searchesByNameKrNameEnAndSymbol() {
        persist(StockMarket.NAS, "AAPL", "애플", "APPLE INC", StockSecurityType.STOCK, "USD");
        persist(StockMarket.KOSPI, "005930", "삼성전자", null, StockSecurityType.STOCK, "KRW");
        em.flush();
        em.clear();

        assertThat(repository.search(keyword("애플"), PageRequest.of(0, 10)).getContent())
            .singleElement().extracting(StockMaster::getSymbol).isEqualTo("AAPL");
        assertThat(repository.search(keyword("apple"), PageRequest.of(0, 10)).getContent())
            .singleElement().extracting(StockMaster::getSymbol).isEqualTo("AAPL");
        assertThat(repository.search(keyword("aapl"), PageRequest.of(0, 10)).getContent())
            .singleElement().extracting(StockMaster::getSymbol).isEqualTo("AAPL");
        assertThat(repository.search(keyword("005930"), PageRequest.of(0, 10)).getContent())
            .singleElement().extracting(StockMaster::getNameKr).isEqualTo("삼성전자");
    }

    @Test
    @DisplayName("정확 일치가 부분 일치보다 먼저 온다 — '애플' 검색 시 애플(AAPL)이 최상단")
    void ranksExactMatchFirst() {
        persist(StockMarket.NYS, "APLE", "애플 하스피탤리티 리츠", "APPLE HOSPITALITY REIT", StockSecurityType.STOCK, "USD");
        persist(StockMarket.NAS, "AAPL", "애플", "APPLE INC", StockSecurityType.STOCK, "USD");
        em.flush();
        em.clear();

        Page<StockMaster> page = repository.search(keyword("애플"), PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(StockMaster::getSymbol)
            .containsExactly("AAPL", "APLE");
    }

    @Test
    @DisplayName("국가·종목유형 필터가 함께 적용된다")
    void filtersByCountryAndSecurityType() {
        persist(StockMarket.NAS, "QQQ", "인베스코 QQQ", "INVESCO QQQ", StockSecurityType.ETF, "USD");
        persist(StockMarket.KOSPI, "069500", "KODEX 200", null, StockSecurityType.ETF, "KRW");
        persist(StockMarket.NAS, "AAPL", "애플", "APPLE INC", StockSecurityType.STOCK, "USD");
        em.flush();
        em.clear();

        Page<StockMaster> page = repository.search(
            new StockMasterSearchCondition(null, "us", StockSecurityType.ETF), PageRequest.of(0, 10));

        assertThat(page.getContent()).singleElement()
            .extracting(StockMaster::getSymbol).isEqualTo("QQQ");
    }

    @Test
    @DisplayName("비활성(상장폐지)·삭제 종목은 검색에서 빠진다")
    void excludesInactiveAndDeleted() {
        StockMaster delisted = persist(StockMarket.NAS, "GONE", "상장폐지종목", "GONE INC", StockSecurityType.STOCK, "USD");
        delisted.deactivate();
        StockMaster deleted = persist(StockMarket.NAS, "DEL", "삭제종목", "DEL INC", StockSecurityType.STOCK, "USD");
        ReflectionTestUtils.setField(deleted, "isDeleted", YNType.Y);
        persist(StockMarket.NAS, "AAPL", "애플", "APPLE INC", StockSecurityType.STOCK, "USD");
        em.flush();
        em.clear();

        Page<StockMaster> page = repository.search(keyword(null), PageRequest.of(0, 10));

        assertThat(page.getContent()).singleElement()
            .extracting(StockMaster::getSymbol).isEqualTo("AAPL");
    }

    @Test
    @DisplayName("동기화 대조 조회는 비활성 행까지 포함하고, countAll 은 전체 건수를 센다")
    void findAllByMarketIncludesInactive() {
        StockMaster delisted = persist(StockMarket.NAS, "GONE", "상장폐지종목", "GONE INC", StockSecurityType.STOCK, "USD");
        delisted.deactivate();
        persist(StockMarket.NAS, "AAPL", "애플", "APPLE INC", StockSecurityType.STOCK, "USD");
        persist(StockMarket.KOSPI, "005930", "삼성전자", null, StockSecurityType.STOCK, "KRW");
        em.flush();
        em.clear();

        assertThat(repository.findAllByMarketIncludingInactive(StockMarket.NAS))
            .extracting(StockMaster::getSymbol)
            .containsExactlyInAnyOrder("GONE", "AAPL");
        assertThat(repository.countAll()).isEqualTo(3);
    }

    @Test
    @DisplayName("같은 심볼이라도 시장이 다르면 함께 저장된다 — (market, symbol) 복합 키")
    void allowsSameSymbolAcrossMarkets() {
        persist(StockMarket.KOSPI, "000001", "국내종목", null, StockSecurityType.STOCK, "KRW");
        persist(StockMarket.SHS, "000001", "평안은행", "PING AN BANK", StockSecurityType.STOCK, "CNY");
        em.flush();
        em.clear();

        Page<StockMaster> page = repository.search(keyword("000001"), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
    }
}
