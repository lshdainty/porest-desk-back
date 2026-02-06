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

    @Override
    public Optional<Memo> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(memo)
                .where(memo.rowId.eq(rowId), memo.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public List<Memo> findAllByUser(Long userRowId, Long folderId, String search) {
        BooleanBuilder builder = new BooleanBuilder();
        builder.and(memo.user.rowId.eq(userRowId));
        builder.and(memo.isDeleted.eq(YNType.N));

        if (folderId != null) {
            builder.and(memo.folder.rowId.eq(folderId));
        }
        if (search != null && !search.isBlank()) {
            builder.and(
                memo.title.contains(search)
                    .or(memo.content.contains(search))
            );
        }

        return queryFactory.selectFrom(memo)
            .where(builder)
            .orderBy(memo.isPinned.desc(), memo.modifyAt.desc())
            .fetch();
    }

    @Override
    public Memo save(Memo entity) {
        if (entity.getRowId() == null) {
            entityManager.persist(entity);
        } else {
            entityManager.merge(entity);
        }
        return entity;
    }

    @Override
    public void delete(Memo entity) {
        entity.deleteMemo();
    }
}
