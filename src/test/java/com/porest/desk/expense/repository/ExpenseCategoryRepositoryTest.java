package com.porest.desk.expense.repository;

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
 * ExpenseCategory QueryDsl 리포 슬라이스 테스트.
 *
 * <p>@DataJpaTest 는 커스텀 @Repository 를 스캔하지 않으므로 @Primary QueryDsl 구현과
 * QueryDslConfig(JPAQueryFactory), 그리고 auditing(createBy 비-null) 을 위한 설정을 명시 import 한다.
 * H2(application-test.yml, create-drop)에서 실제 SQL 로 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        ExpenseCategoryQueryDslRepository.class})
@ActiveProfiles("test")
class ExpenseCategoryRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private ExpenseCategoryRepository repository;

    private User persistUser(String userId) {
        return em.persist(User.createUser(null, userId, "테스터", userId + "@porest.com"));
    }

    private ExpenseCategory persistCategory(User user, String name, ExpenseCategory parent, int sortOrder) {
        ExpenseCategory c = ExpenseCategory.createCategory(user, name, "tag", "#fff", ExpenseType.EXPENSE, parent);
        c.updateSortOrder(sortOrder);
        return em.persist(c);
    }

    @Test
    @DisplayName("save 후 findById 로 조회된다")
    void saveAndFindById() {
        User user = persistUser("u1");
        ExpenseCategory category =
                ExpenseCategory.createCategory(user, "식비", "utensils", "#fff", ExpenseType.EXPENSE, null);
        repository.save(category);
        em.flush();
        em.clear();

        Optional<ExpenseCategory> found = repository.findById(category.getRowId());

        assertThat(found).isPresent();
        assertThat(found.get().getCategoryName()).isEqualTo("식비");
    }

    @Test
    @DisplayName("findAllByUser 는 본인 카테고리만 sortOrder 오름차순으로 반환한다")
    void findAllByUserOrdered() {
        User user = persistUser("u1");
        User other = persistUser("u2");
        persistCategory(user, "교통", null, 2);
        persistCategory(user, "식비", null, 0);
        persistCategory(user, "문화", null, 1);
        persistCategory(other, "남의카테고리", null, 0);
        em.flush();
        em.clear();

        List<ExpenseCategory> result = repository.findAllByUser(user.getRowId());

        assertThat(result).hasSize(3);
        assertThat(result).extracting(ExpenseCategory::getCategoryName)
                .containsExactly("식비", "문화", "교통");
    }

    @Test
    @DisplayName("hasChildren 은 자식 보유 여부를 반환한다")
    void hasChildren() {
        User user = persistUser("u1");
        ExpenseCategory parent = persistCategory(user, "건강/외모", null, 0);
        ExpenseCategory leaf = persistCategory(user, "식비", null, 1);
        persistCategory(user, "의료비", parent, 0);
        em.flush();
        em.clear();

        assertThat(repository.hasChildren(parent.getRowId())).isTrue();
        assertThat(repository.hasChildren(leaf.getRowId())).isFalse();
    }

    @Test
    @DisplayName("soft delete 후에는 findById 로 조회되지 않는다")
    void softDeleteExcludedFromFind() {
        User user = persistUser("u1");
        ExpenseCategory category = persistCategory(user, "식비", null, 0);
        em.flush();

        repository.delete(category);
        em.flush();
        em.clear();

        assertThat(repository.findById(category.getRowId())).isEmpty();
    }
}
