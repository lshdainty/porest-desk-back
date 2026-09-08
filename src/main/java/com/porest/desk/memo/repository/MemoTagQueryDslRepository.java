package com.porest.desk.memo.repository;

import com.porest.core.type.YNType;
import com.porest.desk.memo.domain.MemoTag;
import com.porest.desk.memo.domain.QMemo;
import com.porest.desk.memo.domain.QMemoTag;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@Primary
@RequiredArgsConstructor
public class MemoTagQueryDslRepository implements MemoTagRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QMemoTag tag = QMemoTag.memoTag;
    private static final QMemo memo = QMemo.memo;

    @Override
    public Optional<MemoTag> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(tag)
                .where(tag.rowId.eq(rowId), tag.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public MemoTag getReference(Long rowId) {
        return entityManager.getReference(MemoTag.class, rowId);
    }

    @Override
    public List<MemoTag> findAllByUser(Long userRowId) {
        return queryFactory.selectFrom(tag)
            .where(tag.user.rowId.eq(userRowId), tag.isDeleted.eq(YNType.N))
            .orderBy(tag.tagName.asc())
            .fetch();
    }

    @Override
    public boolean existsActiveByUserAndName(Long userRowId, String tagName, Long excludeRowId) {
        return queryFactory.selectOne()
            .from(tag)
            .where(
                tag.user.rowId.eq(userRowId),
                tag.tagName.eq(tagName),
                tag.isDeleted.eq(YNType.N),
                excludeRowId != null ? tag.rowId.ne(excludeRowId) : null
            )
            .fetchFirst() != null;
    }

    /**
     * {@code fetchOne} 이 아니라 {@code fetchFirst} 인 이유 — UK 는 <b>활성 행 사이에서만</b>
     * 유일하다(생성 컬럼 {@code active_tag_name}). 지금 스키마로는 활성 동명이 둘일 수 없지만,
     * 여기서 예외가 나면 메모 저장 전체가 죽으므로 하나를 골라 쓴다(할 일 태그와 같은 판단).
     */
    @Override
    public Optional<MemoTag> findActiveByUserAndName(Long userRowId, String tagName) {
        return Optional.ofNullable(
            queryFactory.selectFrom(tag)
                .where(
                    tag.user.rowId.eq(userRowId),
                    tag.tagName.eq(tagName),
                    tag.isDeleted.eq(YNType.N)
                )
                .orderBy(tag.rowId.asc())
                .fetchFirst()
        );
    }

    /**
     * 매핑 테이블이 없으므로 <b>메모 한 테이블만</b> 센다 — 할 일이
     * {@code todo_tag_mapping JOIN todo} 로 하는 일을 FK 하나로 끝낸다.
     *
     * <p>{@code memo.memoTag.rowId} 는 조인 없이 FK 컬럼을 그대로 읽는다(대상이 연관의
     * 식별자라 하이버네이트가 조인을 만들지 않는다). 이름으로 세면 태그를 개명하는 순간
     * 집계가 0 이 되므로 <b>FK 로만</b> 센다.
     */
    @Override
    public Map<Long, Long> countMemosByTag(Long userRowId) {
        return queryFactory
            .select(memo.memoTag.rowId, memo.count())
            .from(memo)
            .where(
                memo.user.rowId.eq(userRowId),
                memo.isDeleted.eq(YNType.N),
                memo.memoTag.isNotNull()
            )
            .groupBy(memo.memoTag.rowId)
            .fetch()
            .stream()
            .collect(Collectors.toMap(
                t -> t.get(memo.memoTag.rowId),
                t -> t.get(memo.count()) == null ? 0L : t.get(memo.count())
            ));
    }

    @Override
    public MemoTag save(MemoTag entity) {
        entityManager.persist(entity);
        return entity;
    }

    @Override
    public void delete(MemoTag entity) {
        entity.deleteTag();
    }

    @Override
    public void flush() {
        entityManager.flush();
    }
}
