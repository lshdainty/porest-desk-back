package com.porest.desk.memo.repository;

import com.porest.desk.common.config.database.JpaAuditingConfig;
import com.porest.desk.common.config.database.LoginUserAuditorAware;
import com.porest.desk.memo.domain.Memo;
import com.porest.desk.memo.domain.MemoTag;
import com.porest.desk.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MemoJpaRepository} — QueryDSL 이 아닌 <b>JPQL 판</b>이 같은 답을 내는지.
 *
 * <p>이 레포는 도메인마다 리포를 두 벌 둔다({@code @Primary} 인 QueryDSL 판과 JPQL 판).
 * 지금 주입되는 것은 QueryDSL 쪽이라 JPQL 쪽은 <b>아무도 실행하지 않는다</b> — 문자열로 짠
 * 쿼리는 컴파일도 안 되므로, 틀려도 그 사실이 드러나지 않는다. 태그 동기화는 새로 짠 JPQL 이라
 * 최소한 <b>파싱되고 같은 행을 고른다</b>는 것만 여기서 붙들어 둔다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, LoginUserAuditorAware.class, MemoJpaRepository.class})
@ActiveProfiles("test")
class MemoJpaRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private MemoJpaRepository repository;

    private User persistUser(String userId) {
        return em.persist(User.createUser(null, userId, "이름_" + userId, userId + "@porest.com"));
    }

    @Test
    @DisplayName("renameTag·clearTag — FK 로 이어진 행과 이름만 같은 행을 함께 고른다(QueryDSL 판과 같은 답)")
    void syncQueriesMatchQueryDslBehaviour() {
        User user = persistUser("u1");
        MemoTag tag = em.persist(MemoTag.createTag(user, "업무", "#ffffff"));
        Memo linked = em.persist(Memo.createMemo(user, "FK", "c", "업무", tag, null));
        Memo nameOnly = em.persist(Memo.createMemo(user, "이름만", "c", "업무", null, null));
        em.flush();
        em.clear();

        assertThat(repository.renameTag(user.getRowId(), tag.getRowId(), "업무", "회사")).isEqualTo(2);
        em.clear();
        assertThat(em.find(Memo.class, nameOnly.getRowId()).getTag()).isEqualTo("회사");

        // 개명 뒤라 이름은 "회사" 지만 FK 는 그대로다 — 삭제는 둘 중 하나만 맞아도 걷는다.
        assertThat(repository.clearTag(user.getRowId(), tag.getRowId(), "회사")).isEqualTo(2);
        em.clear();
        Memo reloaded = em.find(Memo.class, linked.getRowId());
        assertThat(reloaded.getTag()).isNull();
        assertThat(reloaded.getMemoTag()).isNull();
    }

    @Test
    @DisplayName("findById·findAllByUser — LEFT JOIN FETCH 가 태그 없는 메모를 떨어뜨리지 않는다")
    void fetchJoinKeepsUntaggedMemos() {
        User user = persistUser("u1");
        Memo untagged = em.persist(Memo.createMemo(user, "태그 없음", "c", null, null, null));
        em.flush();
        em.clear();

        assertThat(repository.findById(untagged.getRowId())).isPresent();
        assertThat(repository.findAllByUser(user.getRowId(), null)).hasSize(1);
        assertThat(repository.findAllByUser(user.getRowId(), "태그")).hasSize(1);
    }
}
