package com.porest.desk.card.repository;

import com.porest.core.type.YNType;
import com.porest.desk.card.domain.CardCatalog;
import com.porest.desk.card.domain.CardCatalogBenefit;
import com.porest.desk.card.domain.CardCompany;
import com.porest.desk.card.type.CardBenefitKind;
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
 * CardCatalogBenefit QueryDsl 리포 슬라이스 테스트.
 *
 * <p>카탈로그 하위 엔티티는 seed 로만 적재되어 도메인 팩토리가 없으므로 리플렉션으로 필드를 채워 저장한다.
 * 카탈로그별 혜택 조회의 정렬(kind·sortOrder·rowId), 카테고리 필터(kind=BENEFIT + category IN)를 검증한다.
 * H2(application-test.yml, create-drop)에서 실제 SQL 로 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        CardCatalogBenefitQueryDslRepository.class})
@ActiveProfiles("test")
class CardCatalogBenefitRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private CardCatalogBenefitRepository repository;

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

    private CardCatalogBenefit persistBenefit(CardCatalog catalog, CardBenefitKind kind,
                                              String category, String title, int sortOrder) {
        CardCatalogBenefit b = BeanUtils.instantiateClass(CardCatalogBenefit.class);
        ReflectionTestUtils.setField(b, "cardCatalog", catalog);
        ReflectionTestUtils.setField(b, "kind", kind);
        ReflectionTestUtils.setField(b, "category", category);
        ReflectionTestUtils.setField(b, "title", title);
        ReflectionTestUtils.setField(b, "sortOrder", sortOrder);
        return em.persist(b);
    }

    @Test
    @DisplayName("findAllByCardCatalog 는 해당 카탈로그 혜택만 kind·sortOrder·rowId 순으로 반환한다")
    void findAllByCardCatalogOrderedAndIsolated() {
        CardCatalog catalog = persistCatalog("카드A");
        CardCatalog other = persistCatalog("카드B");
        persistBenefit(catalog, CardBenefitKind.CAUTION, "유의", "주의", 0);
        persistBenefit(catalog, CardBenefitKind.BENEFIT, "쇼핑", "혜택B", 1);
        persistBenefit(catalog, CardBenefitKind.BENEFIT, "식당", "혜택A", 0);
        persistBenefit(other, CardBenefitKind.BENEFIT, "식당", "타인혜택", 0);
        em.flush();
        em.clear();

        List<CardCatalogBenefit> result = repository.findAllByCardCatalog(catalog.getRowId());

        // kind asc("BENEFIT" < "CAUTION") → BENEFIT 먼저(sortOrder asc), 그다음 CAUTION
        assertThat(result).extracting(CardCatalogBenefit::getTitle)
                .containsExactly("혜택A", "혜택B", "주의");
    }

    @Test
    @DisplayName("findBenefitsByCardAndCategories 는 카테고리가 비어있으면 빈 목록을 반환한다")
    void findBenefitsByCardAndCategoriesEmpty() {
        CardCatalog catalog = persistCatalog("카드A");
        persistBenefit(catalog, CardBenefitKind.BENEFIT, "식당", "혜택", 0);
        em.flush();
        em.clear();

        assertThat(repository.findBenefitsByCardAndCategories(catalog.getRowId(), List.of())).isEmpty();
        assertThat(repository.findBenefitsByCardAndCategories(catalog.getRowId(), null)).isEmpty();
    }

    @Test
    @DisplayName("findBenefitsByCardAndCategories 는 BENEFIT 종류 중 지정 카테고리만 sortOrder 순으로 반환한다")
    void findBenefitsByCardAndCategoriesFilters() {
        CardCatalog catalog = persistCatalog("카드A");
        CardCatalog other = persistCatalog("카드B");
        persistBenefit(catalog, CardBenefitKind.BENEFIT, "식당", "식당B", 1);
        persistBenefit(catalog, CardBenefitKind.BENEFIT, "식당", "식당A", 0);
        persistBenefit(catalog, CardBenefitKind.BENEFIT, "카페", "카페", 0);   // 카테고리 불일치 → 제외
        persistBenefit(catalog, CardBenefitKind.CAUTION, "식당", "주의", 0);   // kind 불일치 → 제외
        persistBenefit(other, CardBenefitKind.BENEFIT, "식당", "타인", 0);     // 다른 카드 → 제외
        em.flush();
        em.clear();

        List<CardCatalogBenefit> result =
                repository.findBenefitsByCardAndCategories(catalog.getRowId(), List.of("식당"));

        assertThat(result).extracting(CardCatalogBenefit::getTitle)
                .containsExactly("식당A", "식당B");
    }
}
