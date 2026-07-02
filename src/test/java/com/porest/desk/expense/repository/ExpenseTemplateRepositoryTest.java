package com.porest.desk.expense.repository;

import com.porest.desk.common.config.QueryDslConfig;
import com.porest.desk.common.config.database.JpaAuditingConfig;
import com.porest.desk.common.config.database.LoginUserAuditorAware;
import com.porest.desk.expense.domain.ExpenseTemplate;
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
 * ExpenseTemplate QueryDsl 리포 슬라이스 테스트 — H2(create-drop)에서 실제 SQL 로 검증.
 *
 * <p>템플릿의 soft-delete(isDeleted=N) 필터와 useCount 내림차순·sortOrder 오름차순 정렬을 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        ExpenseTemplateQueryDslRepository.class})
@ActiveProfiles("test")
class ExpenseTemplateRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private ExpenseTemplateRepository repository;

    private User persistUser(String userId) {
        return em.persist(User.createUser(null, userId, "테스터", userId + "@porest.com"));
    }

    private ExpenseTemplate persistTemplate(User user, String name, int sortOrder, int useCount) {
        ExpenseTemplate t = ExpenseTemplate.createTemplate(user, name, null, null,
                ExpenseType.EXPENSE, 10_000L, "설명", "가게", "CARD", sortOrder, null);
        for (int i = 0; i < useCount; i++) {
            t.incrementUseCount();
        }
        return em.persist(t);
    }

    @Test
    @DisplayName("save 후 findById 로 조회된다")
    void saveAndFindById() {
        User user = persistUser("u1");
        ExpenseTemplate template = ExpenseTemplate.createTemplate(user, "월세", null, null,
                ExpenseType.EXPENSE, 500_000L, "월세", "집주인", "TRANSFER", 0, null);
        repository.save(template);
        em.flush();
        em.clear();

        Optional<ExpenseTemplate> found = repository.findById(template.getRowId());

        assertThat(found).isPresent();
        assertThat(found.get().getTemplateName()).isEqualTo("월세");
    }

    @Test
    @DisplayName("findByUser — 본인 템플릿만 useCount 내림차순·sortOrder 오름차순으로 반환한다")
    void findByUserOrderedAndScoped() {
        User user = persistUser("u1");
        User other = persistUser("u2");
        persistTemplate(user, "A", 5, 2); // useCount 2, sortOrder 5
        persistTemplate(user, "B", 1, 2); // useCount 2, sortOrder 1 → A 보다 앞
        persistTemplate(user, "C", 0, 0); // useCount 0 → 뒤로
        persistTemplate(other, "남의템플릿", 0, 9); // 타인 → 제외
        em.flush();
        em.clear();

        List<ExpenseTemplate> result = repository.findByUser(user.getRowId());

        assertThat(result).extracting(ExpenseTemplate::getTemplateName)
                .containsExactly("B", "A", "C");
    }

    @Test
    @DisplayName("soft delete 후에는 findById·findByUser 에서 제외된다")
    void softDeleteExcluded() {
        User user = persistUser("u1");
        ExpenseTemplate keep = persistTemplate(user, "유지", 0, 0);
        ExpenseTemplate removed = persistTemplate(user, "삭제", 1, 0);
        em.flush();

        repository.delete(removed); // deleteTemplate() → isDeleted=Y
        em.flush();
        em.clear();

        assertThat(repository.findById(removed.getRowId())).isEmpty();
        assertThat(repository.findByUser(user.getRowId()))
                .extracting(ExpenseTemplate::getTemplateName)
                .containsExactly("유지");
        assertThat(repository.findById(keep.getRowId())).isPresent();
    }
}
