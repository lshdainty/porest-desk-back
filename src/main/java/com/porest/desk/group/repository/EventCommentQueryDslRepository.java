package com.porest.desk.group.repository;

import com.porest.core.type.YNType;
import com.porest.desk.group.domain.EventComment;
import com.porest.desk.group.domain.QEventComment;
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
public class EventCommentQueryDslRepository implements EventCommentRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QEventComment comment = QEventComment.eventComment;

    @Override
    public Optional<EventComment> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(comment)
                .where(comment.rowId.eq(rowId), comment.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public List<EventComment> findAllByEvent(Long eventRowId) {
        return queryFactory.selectFrom(comment)
            .where(comment.event.rowId.eq(eventRowId), comment.isDeleted.eq(YNType.N))
            .orderBy(comment.createAt.asc())
            .fetch();
    }

    @Override
    public EventComment save(EventComment entity) {
        if (entity.getRowId() == null) {
            entityManager.persist(entity);
        } else {
            entityManager.merge(entity);
        }
        return entity;
    }
}
