package com.porest.desk.card.repository;

import com.porest.core.type.YNType;
import com.porest.desk.card.domain.CardCatalog;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CardCatalog QueryDsl 리포 슬라이스 테스트.
 *
 * <p>카탈로그 엔티티는 seed 로만 적재되어 도메인 팩토리가 없으므로 리플렉션으로 필드를 채워 저장한다.
 * findById 의 회사 조인/soft-delete, search 의 키워드(카드명·회사명)·타입 필터·단종 포함 여부·정렬·페이징을 검증한다.
 * H2(application-test.yml, create-drop)에서 실제 SQL 로 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        CardCatalogQueryDslRepository.class})
@ActiveProfiles("test")
class CardCatalogRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private CardCatalogRepository repository;

    private int seq = 0;

    private CardCompany persistCompany(String name) {
        CardCompany c = BeanUtils.instantiateClass(CardCompany.class);
        ReflectionTestUtils.setField(c, "name", name);
        ReflectionTestUtils.setField(c, "nameEng", "eng-" + (seq++));
        ReflectionTestUtils.setField(c, "isDeleted", YNType.N);
        return em.persist(c);
    }

    private CardCatalog persistCatalog(CardCompany company, String cardName, CardType cardType,
                                       CardBenefitType benefitType, YNType discontinued, YNType deleted) {
        CardCatalog c = BeanUtils.instantiateClass(CardCatalog.class);
        ReflectionTestUtils.setField(c, "externalCardId", (long) (seq++));
        ReflectionTestUtils.setField(c, "company", company);
        ReflectionTestUtils.setField(c, "cardName", cardName);
        ReflectionTestUtils.setField(c, "cardType", cardType);
        ReflectionTestUtils.setField(c, "benefitType", benefitType);
        ReflectionTestUtils.setField(c, "isDiscontinued", discontinued);
        ReflectionTestUtils.setField(c, "onlyOnline", YNType.N);
        ReflectionTestUtils.setField(c, "annualFeeAmount", 10000);
        ReflectionTestUtils.setField(c, "performanceRequiredAmount", 300000);
        ReflectionTestUtils.setField(c, "performanceIsRequired", YNType.Y);
        ReflectionTestUtils.setField(c, "isDeleted", deleted);
        return em.persist(c);
    }

    private CardCatalog persistActive(CardCompany company, String cardName) {
        return persistCatalog(company, cardName, CardType.CREDIT, CardBenefitType.DISCOUNT, YNType.N, YNType.N);
    }

    private static CardCatalogSearchCondition condition(String keyword, CardType cardType,
                                                        CardBenefitType benefitType, Boolean includeDiscontinued) {
        return new CardCatalogSearchCondition(keyword, cardType, benefitType, includeDiscontinued);
    }

    @Test
    @DisplayName("findById 는 회사 조인과 함께 조회하고 soft delete 는 제외한다")
    void findByIdJoinsCompanyAndExcludesSoftDeleted() {
        CardCompany company = persistCompany("우리카드");
        CardCatalog active = persistActive(company, "카드의정석");
        CardCatalog deleted = persistCatalog(company, "삭제된카드",
                CardType.CREDIT, CardBenefitType.DISCOUNT, YNType.N, YNType.Y);
        em.flush();
        em.clear();

        Optional<CardCatalog> found = repository.findById(active.getRowId());
        assertThat(found).isPresent();
        assertThat(found.get().getCardName()).isEqualTo("카드의정석");
        assertThat(found.get().getCompany().getName()).isEqualTo("우리카드");

        assertThat(repository.findById(deleted.getRowId())).isEmpty();
    }

    @Test
    @DisplayName("search 는 키워드로 카드명 또는 회사명을 부분검색한다")
    void searchByKeywordMatchesCardNameOrCompanyName() {
        CardCompany shinhan = persistCompany("신한카드");
        CardCompany samsung = persistCompany("삼성카드");
        persistActive(shinhan, "신한 딥드림");     // 카드명에 '신한'
        persistActive(samsung, "탭탭 에디션2");    // 카드명엔 '삼성' 없음 → 회사명으로만 매칭
        em.flush();
        em.clear();

        Page<CardCatalog> byCardName = repository.search(condition("신한", null, null, null), PageRequest.of(0, 10));
        assertThat(byCardName.getContent()).extracting(CardCatalog::getCardName).containsExactly("신한 딥드림");

        Page<CardCatalog> byCompanyName = repository.search(condition("삼성", null, null, null), PageRequest.of(0, 10));
        assertThat(byCompanyName.getContent()).extracting(CardCatalog::getCardName).containsExactly("탭탭 에디션2");
    }

    @Test
    @DisplayName("search 는 cardType 과 benefitType 으로 필터링한다")
    void searchFiltersByCardTypeAndBenefitType() {
        CardCompany company = persistCompany("현대카드");
        persistCatalog(company, "신용-할인", CardType.CREDIT, CardBenefitType.DISCOUNT, YNType.N, YNType.N);
        persistCatalog(company, "체크-포인트", CardType.CHECK, CardBenefitType.POINT, YNType.N, YNType.N);
        em.flush();
        em.clear();

        Page<CardCatalog> credit = repository.search(condition(null, CardType.CREDIT, null, null), PageRequest.of(0, 10));
        assertThat(credit.getContent()).extracting(CardCatalog::getCardName).containsExactly("신용-할인");

        Page<CardCatalog> point = repository.search(condition(null, null, CardBenefitType.POINT, null), PageRequest.of(0, 10));
        assertThat(point.getContent()).extracting(CardCatalog::getCardName).containsExactly("체크-포인트");
    }

    @Test
    @DisplayName("search 는 includeDiscontinued 기본은 단종 제외, true 면 포함한다")
    void searchIncludeDiscontinued() {
        CardCompany company = persistCompany("롯데카드");
        persistCatalog(company, "활성카드", CardType.CREDIT, CardBenefitType.DISCOUNT, YNType.N, YNType.N);
        persistCatalog(company, "단종카드", CardType.CREDIT, CardBenefitType.DISCOUNT, YNType.Y, YNType.N);
        em.flush();
        em.clear();

        Page<CardCatalog> defaultResult = repository.search(condition(null, null, null, null), PageRequest.of(0, 10));
        assertThat(defaultResult.getContent()).extracting(CardCatalog::getCardName).containsExactly("활성카드");

        Page<CardCatalog> included = repository.search(condition(null, null, null, true), PageRequest.of(0, 10));
        assertThat(included.getContent()).extracting(CardCatalog::getCardName)
                .containsExactlyInAnyOrder("활성카드", "단종카드");
    }

    @Test
    @DisplayName("search 는 단종 카드를 뒤로(isDiscontinued asc, rowId asc) 정렬한다")
    void searchOrdersDiscontinuedLast() {
        CardCompany company = persistCompany("BC카드");
        persistCatalog(company, "단종1", CardType.CREDIT, CardBenefitType.DISCOUNT, YNType.Y, YNType.N);
        persistCatalog(company, "활성1", CardType.CREDIT, CardBenefitType.DISCOUNT, YNType.N, YNType.N);
        persistCatalog(company, "활성2", CardType.CREDIT, CardBenefitType.DISCOUNT, YNType.N, YNType.N);
        persistCatalog(company, "단종2", CardType.CREDIT, CardBenefitType.DISCOUNT, YNType.Y, YNType.N);
        em.flush();
        em.clear();

        Page<CardCatalog> result = repository.search(condition(null, null, null, true), PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(CardCatalog::getCardName)
                .containsExactly("활성1", "활성2", "단종1", "단종2");
    }

    @Test
    @DisplayName("search 는 페이징을 적용한다")
    void searchPaging() {
        CardCompany company = persistCompany("하나카드");
        persistActive(company, "카드1");
        persistActive(company, "카드2");
        persistActive(company, "카드3");
        em.flush();
        em.clear();

        Pageable pageable = PageRequest.of(0, 2);
        Page<CardCatalog> page = repository.search(condition(null, null, null, null), pageable);

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.hasNext()).isTrue();
    }

    @Test
    @DisplayName("search 는 soft delete 된 카드를 제외한다")
    void searchExcludesSoftDeleted() {
        CardCompany company = persistCompany("IBK카드");
        persistCatalog(company, "정상카드", CardType.CREDIT, CardBenefitType.DISCOUNT, YNType.N, YNType.N);
        persistCatalog(company, "삭제카드", CardType.CREDIT, CardBenefitType.DISCOUNT, YNType.N, YNType.Y);
        em.flush();
        em.clear();

        Page<CardCatalog> result = repository.search(condition(null, null, null, null), PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(CardCatalog::getCardName).containsExactly("정상카드");
    }
}
