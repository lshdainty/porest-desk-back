package com.porest.desk.card.repository;

import com.porest.desk.card.domain.CardBenefitCategoryMapping;
import com.porest.desk.common.config.QueryDslConfig;
import com.porest.desk.common.config.database.JpaAuditingConfig;
import com.porest.desk.common.config.database.LoginUserAuditorAware;
import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.desk.expense.type.ExpenseType;
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
 * CardBenefitCategoryMapping QueryDsl 리포 슬라이스 테스트.
 *
 * <p>공용(user IS NULL) 기본 매핑과 사용자 커스텀 매핑의 merge(커스텀 우선), 소유권/soft-delete 필터를 검증한다.
 * H2(application-test.yml, create-drop)에서 실제 SQL 로 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        CardBenefitCategoryMappingQueryDslRepository.class})
@ActiveProfiles("test")
class CardBenefitCategoryMappingRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private CardBenefitCategoryMappingRepository repository;

    private User persistUser(String userId) {
        return em.persist(User.createUser(null, userId, "테스터", userId + "@porest.com"));
    }

    private ExpenseCategory persistCategory(User user, String name) {
        return em.persist(ExpenseCategory.createCategory(user, name, "tag", "#fff", ExpenseType.EXPENSE, null));
    }

    private CardBenefitCategoryMapping persistMapping(User user, String category, ExpenseCategory expenseCategory) {
        return em.persist(CardBenefitCategoryMapping.createUserMapping(user, category, expenseCategory));
    }

    private CardBenefitCategoryMapping persistDeletedMapping(User user, String category, ExpenseCategory expenseCategory) {
        CardBenefitCategoryMapping m = CardBenefitCategoryMapping.createUserMapping(user, category, expenseCategory);
        m.deleteMapping();
        return em.persist(m);
    }

    @Test
    @DisplayName("save 후 findById 로 조회된다")
    void saveAndFindById() {
        User user = persistUser("u1");
        ExpenseCategory category = persistCategory(user, "식비");
        CardBenefitCategoryMapping mapping =
                CardBenefitCategoryMapping.createUserMapping(user, "dining", category);
        repository.save(mapping);
        em.flush();
        em.clear();

        Optional<CardBenefitCategoryMapping> found = repository.findById(mapping.getRowId());

        assertThat(found).isPresent();
        assertThat(found.get().getBenefitCategory()).isEqualTo("dining");
    }

    @Test
    @DisplayName("findById 는 soft delete 된 매핑을 제외한다")
    void findByIdExcludesSoftDeleted() {
        User user = persistUser("u1");
        ExpenseCategory category = persistCategory(user, "식비");
        CardBenefitCategoryMapping deleted = persistDeletedMapping(user, "dining", category);
        em.flush();
        em.clear();

        assertThat(repository.findById(deleted.getRowId())).isEmpty();
    }

    @Test
    @DisplayName("findUserMapping 은 user+benefitCategory 로 본인 커스텀만 조회한다")
    void findUserMappingIsolatesByUserAndCategory() {
        User user = persistUser("u1");
        User other = persistUser("u2");
        ExpenseCategory c1 = persistCategory(user, "식비");
        ExpenseCategory c2 = persistCategory(other, "카페");
        persistMapping(user, "dining", c1);
        persistMapping(other, "dining", c2);   // 다른 유저의 동일 카테고리 → 조회 안 됨
        em.flush();
        em.clear();

        Optional<CardBenefitCategoryMapping> found = repository.findUserMapping(user.getRowId(), "dining");

        assertThat(found).isPresent();
        assertThat(found.get().getUser().getRowId()).isEqualTo(user.getRowId());
        // 존재하지 않는 카테고리는 empty
        assertThat(repository.findUserMapping(user.getRowId(), "shopping")).isEmpty();
    }

    @Test
    @DisplayName("findEffectiveMappings 는 공용+커스텀을 merge 하되 커스텀이 우선하고 benefitCategory 오름차순으로 반환한다")
    void findEffectiveMappingsMergesCustomOverPublic() {
        User user = persistUser("u1");
        User other = persistUser("u2");
        ExpenseCategory publicCafe = persistCategory(user, "공용카페");
        ExpenseCategory publicShopping = persistCategory(user, "공용쇼핑");
        ExpenseCategory customCafe = persistCategory(user, "커스텀카페");
        ExpenseCategory customTravel = persistCategory(user, "커스텀여행");
        ExpenseCategory otherShopping = persistCategory(other, "타인쇼핑");

        persistMapping(null, "cafe", publicCafe);         // 공용
        persistMapping(null, "shopping", publicShopping);  // 공용
        persistMapping(user, "cafe", customCafe);          // 커스텀 → cafe 덮어씀
        persistMapping(user, "travel", customTravel);      // 커스텀 전용
        persistMapping(other, "shopping", otherShopping);  // 타인 커스텀 → 제외
        em.flush();
        em.clear();

        List<CardBenefitCategoryMapping> result = repository.findEffectiveMappings(user.getRowId());

        assertThat(result).extracting(CardBenefitCategoryMapping::getBenefitCategory)
                .containsExactly("cafe", "shopping", "travel");
        // cafe 는 커스텀 우선(user 존재), shopping 은 공용(user null), travel 은 커스텀
        assertThat(result.get(0).getUser()).isNotNull();
        assertThat(result.get(0).getUser().getRowId()).isEqualTo(user.getRowId());
        assertThat(result.get(1).getUser()).isNull();
        assertThat(result.get(2).getUser()).isNotNull();
    }

    @Test
    @DisplayName("findEffectiveMappings 는 soft delete 된 매핑을 제외한다")
    void findEffectiveMappingsExcludesSoftDeleted() {
        User user = persistUser("u1");
        ExpenseCategory active = persistCategory(user, "활성");
        ExpenseCategory removed = persistCategory(user, "삭제");
        persistMapping(null, "cafe", active);
        persistDeletedMapping(null, "old", removed);
        em.flush();
        em.clear();

        List<CardBenefitCategoryMapping> result = repository.findEffectiveMappings(user.getRowId());

        assertThat(result).extracting(CardBenefitCategoryMapping::getBenefitCategory)
                .containsExactly("cafe");
    }

    @Test
    @DisplayName("findAllDefaultMappings 는 공용 매핑만 benefitCategory 오름차순으로 반환한다")
    void findAllDefaultMappingsReturnsPublicOnly() {
        User user = persistUser("u1");
        ExpenseCategory pShopping = persistCategory(user, "공용쇼핑");
        ExpenseCategory pCafe = persistCategory(user, "공용카페");
        ExpenseCategory custom = persistCategory(user, "커스텀");
        ExpenseCategory removed = persistCategory(user, "삭제");
        persistMapping(null, "shopping", pShopping);   // 공용
        persistMapping(null, "cafe", pCafe);           // 공용
        persistMapping(user, "cafe", custom);          // 사용자 커스텀 → 제외
        persistDeletedMapping(null, "old", removed);   // soft delete → 제외
        em.flush();
        em.clear();

        List<CardBenefitCategoryMapping> result = repository.findAllDefaultMappings();

        assertThat(result).extracting(CardBenefitCategoryMapping::getBenefitCategory)
                .containsExactly("cafe", "shopping");
        assertThat(result).allSatisfy(m -> assertThat(m.getUser()).isNull());
    }
}
