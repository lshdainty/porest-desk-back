package com.porest.desk.calendar.repository;

import com.porest.core.type.YNType;
import com.porest.desk.calendar.domain.EventLabel;
import com.porest.desk.calendar.domain.QEventLabel;
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
public class EventLabelQueryDslRepository implements EventLabelRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QEventLabel eventLabel = QEventLabel.eventLabel;

    @Override
    public Optional<EventLabel> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(eventLabel)
                .where(eventLabel.rowId.eq(rowId), eventLabel.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public List<EventLabel> findAllByUser(Long userRowId) {
        return queryFactory.selectFrom(eventLabel)
            .where(eventLabel.user.rowId.eq(userRowId), eventLabel.isDeleted.eq(YNType.N))
            .orderBy(eventLabel.sortOrder.asc(), eventLabel.rowId.asc())
            .fetch();
    }

    @Override
    public EventLabel save(EventLabel entity) {
        entityManager.persist(entity);
        return entity;
    }

    @Override
    public void delete(EventLabel entity) {
        entity.deleteLabel();
    }
}
