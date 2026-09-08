package com.porest.desk.memo.repository;

import com.porest.core.type.YNType;
import com.porest.desk.memo.domain.Memo;
import com.porest.desk.memo.domain.QMemo;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Primary
@RequiredArgsConstructor
public class MemoQueryDslRepository implements MemoRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QMemo memo = QMemo.memo;

    /**
     * 태그 마스터를 함께 끌고 온다({@code fetchJoin}) — 응답이 {@code memoTagRowId} 를 싣고
     * 저장 경로가 지금 붙은 태그 이름을 읽으므로, 지연 로딩으로 두면 메모마다 조회가 한 번씩 붙는다.
     * {@code LEFT} 라 태그 없는 메모도 그대로 나온다.
     */
    @Override
    public Optional<Memo> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(memo)
                .leftJoin(memo.memoTag).fetchJoin()
                .where(memo.rowId.eq(rowId), memo.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public List<Memo> findAllByUser(Long userRowId, String search) {
        BooleanBuilder builder = new BooleanBuilder();
        builder.and(memo.user.rowId.eq(userRowId));
        builder.and(memo.isDeleted.eq(YNType.N));

        if (search != null && !search.isBlank()) {
            builder.and(
                memo.title.contains(search)
                    .or(memo.content.contains(search))
            );
        }

        // 목록도 태그를 함께 끌고 온다 — 여기가 N+1 이 가장 크게 나는 자리다(메모 수만큼).
        return queryFactory.selectFrom(memo)
            .leftJoin(memo.memoTag).fetchJoin()
            .where(builder)
            .orderBy(memo.isPinned.desc(), memo.modifyAt.desc())
            .fetch();
    }

    /**
     * 벌크 UPDATE 라 더티 체킹·감사 필드({@code modify_at})를 타지 않는다 — 의도한 것이다
     * (할 일의 {@code renameCategory} 와 같은 판단). 바뀐 것은 태그 이름이지 메모가 아니고,
     * 한 사용자의 같은 태그가 수백 행일 수 있어 엔티티를 다 올려 고칠 자리가 아니다.
     *
     * <p>WHERE 가 <b>FK 이거나 이름</b>인 이유는 인터페이스 주석에 적어 뒀다(백필 전 옛 행).
     * {@code memo.memoTag.rowId} 는 연관의 식별자라 조인 없이 FK 컬럼으로 나간다.
     */
    @Override
    public long renameTag(Long userRowId, Long tagRowId, String fromTagName, String toTagName) {
        return queryFactory.update(memo)
            .set(memo.tag, toTagName)
            .where(
                memo.user.rowId.eq(userRowId),
                memo.isDeleted.eq(YNType.N),
                memo.memoTag.rowId.eq(tagRowId).or(memo.tag.eq(fromTagName))
            )
            .execute();
    }

    @Override
    public long clearTag(Long userRowId, Long tagRowId, String tagName) {
        return queryFactory.update(memo)
            .setNull(memo.tag)
            .setNull(memo.memoTag)
            .where(
                memo.user.rowId.eq(userRowId),
                memo.isDeleted.eq(YNType.N),
                memo.memoTag.rowId.eq(tagRowId).or(memo.tag.eq(tagName))
            )
            .execute();
    }

    @Override
    public Memo save(Memo entity) {
        entityManager.persist(entity);
        return entity;
    }

    @Override
    public void delete(Memo entity) {
        entity.deleteMemo();
    }
}
