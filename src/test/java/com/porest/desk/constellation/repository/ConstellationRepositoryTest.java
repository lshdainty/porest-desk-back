package com.porest.desk.constellation.repository;

import com.porest.core.type.YNType;
import com.porest.desk.common.config.QueryDslConfig;
import com.porest.desk.common.config.database.JpaAuditingConfig;
import com.porest.desk.common.config.database.LoginUserAuditorAware;
import com.porest.desk.constellation.domain.Constellation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
 * 별자리 마스터 리포 — active 필터/정렬, key 조회, soft-delete 제외 검증.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QueryDslConfig.class, JpaAuditingConfig.class, LoginUserAuditorAware.class,
        ConstellationQueryDslRepository.class})
@ActiveProfiles("test")
class ConstellationRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private ConstellationRepository repository;

    private Constellation persistConstellation(String key, int starCount, int sortOrder) {
        return em.persist(Constellation.createConstellation(
            key, key + "명", "설명", "blue", starCount, "{\"pts\":[],\"edges\":[]}", sortOrder));
    }

    @Test
    @DisplayName("findAllActive — 비활성/삭제 제외, sort_order 오름차순")
    void findAllActiveFiltersAndSorts() {
        persistConstellation("cass", 5, 2);
        persistConstellation("dipper", 7, 1);
        Constellation inactive = persistConstellation("orion", 7, 3);
        ReflectionTestUtils.setField(inactive, "isActive", YNType.N);
        Constellation deleted = persistConstellation("lyra", 5, 4);
        ReflectionTestUtils.setField(deleted, "isDeleted", YNType.Y);
        em.flush();

        List<Constellation> actives = repository.findAllActive();

        assertThat(actives).extracting(Constellation::getConstellationKey)
            .containsExactly("dipper", "cass");
    }

    @Test
    @DisplayName("findByKey — 키로 단건 조회, findAll 은 정렬 전체")
    void findByKeyAndFindAll() {
        persistConstellation("dipper", 7, 1);
        persistConstellation("cass", 5, 2);
        em.flush();

        assertThat(repository.findByKey("dipper")).isPresent();
        assertThat(repository.findByKey("unknown")).isEmpty();
        assertThat(repository.findAll()).hasSize(2);
        assertThat(repository.findById(repository.findByKey("cass").orElseThrow().getRowId())).isPresent();
    }
}
