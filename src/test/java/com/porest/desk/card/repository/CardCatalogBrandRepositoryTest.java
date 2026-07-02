package com.porest.desk.card.repository;

import com.porest.core.type.YNType;
import com.porest.desk.card.domain.CardCatalog;
import com.porest.desk.card.domain.CardCatalogBrand;
import com.porest.desk.card.domain.CardCompany;
import com.porest.desk.card.type.CardBenefitType;
import com.porest.desk.card.type.CardType;
import com.porest.desk.common.config.QueryDslConfig;
import com.porest.desk.common.config.database.JpaAuditingConfig;
import com.porest.desk.common.config.database.LoginUserAuditorAware;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CardCatalogBrand QueryDsl 리포 슬라이스 테스트.
 *
 * <p>카탈로그 하위 엔티티는 seed 로만 적재되어 도메인 팩토리가 없으므로 리플렉션으로 필드를 채워 저장한다.
 * 카탈로그별 브랜드 조회의 정렬(sortOrder·rowId)과 카드 격리를 검증한다.
 * H2(application-test.yml, create-drop)에서 실제 SQL 로 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        CardCatalogBrandQueryDslRepository.class})
@ActiveProfiles("test")
class CardCatalogBrandRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private CardCatalogBrandRepository repository;

    private int seq = 0;

    private CardCatalog persistCatalog(String cardName) {
        CardCompany company = BeanUtils.instantiateClass(CardCompany.class);
        ReflectionTestUtils.setField(company, "name", "회사-" + seq);
        ReflectionTestUtils.setField(company, "nameEng", "eng-" + (seq++));
        ReflectionTestUtils.setField(company, "isDeleted", YNType.N);
        em.persist(company);

        CardCatalog c = BeanUtils.instantiateClass(CardCatalog.class);
        ReflectionTestUtils.setField(c, "externalCardId", (long) (seq++));
        ReflectionTestUtils.setField(c, "company", company);
        ReflectionTestUtils.setField(c, "cardName", cardName);
        ReflectionTestUtils.setField(c, "cardType", CardType.CREDIT);
        ReflectionTestUtils.setField(c, "benefitType", CardBenefitType.DISCOUNT);
        ReflectionTestUtils.setField(c, "isDiscontinued", YNType.N);
        ReflectionTestUtils.setField(c, "onlyOnline", YNType.N);
        ReflectionTestUtils.setField(c, "annualFeeAmount", 10000);
        ReflectionTestUtils.setField(c, "performanceRequiredAmount", 300000);
        ReflectionTestUtils.setField(c, "performanceIsRequired", YNType.Y);
        ReflectionTestUtils.setField(c, "isDeleted", YNType.N);
        return em.persist(c);
    }

    private CardCatalogBrand persistBrand(CardCatalog catalog, String brand, int sortOrder) {
        CardCatalogBrand b = BeanUtils.instantiateClass(CardCatalogBrand.class);
        ReflectionTestUtils.setField(b, "cardCatalog", catalog);
        ReflectionTestUtils.setField(b, "brand", brand);
        ReflectionTestUtils.setField(b, "sortOrder", sortOrder);
        return em.persist(b);
    }

    @Test
    @DisplayName("findAllByCardCatalog 는 해당 카탈로그 브랜드만 sortOrder·rowId 순으로 반환한다")
    void findAllByCardCatalogOrderedAndIsolated() {
        CardCatalog catalog = persistCatalog("카드A");
        CardCatalog other = persistCatalog("카드B");
        persistBrand(catalog, "VISA", 1);
        persistBrand(catalog, "MASTER", 0);
        persistBrand(catalog, "AMEX", 0);     // MASTER 와 동일 sortOrder → rowId 큰 쪽이 뒤
        persistBrand(other, "타인브랜드", 0);   // 다른 카드 → 제외
        em.flush();
        em.clear();

        List<CardCatalogBrand> result = repository.findAllByCardCatalog(catalog.getRowId());

        assertThat(result).extracting(CardCatalogBrand::getBrand)
                .containsExactly("MASTER", "AMEX", "VISA");
    }
}
