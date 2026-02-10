package com.porest.desk.calendar.repository;

import com.porest.desk.calendar.domain.EventReminder;
import com.porest.desk.calendar.domain.QEventReminder;
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
public class EventReminderQueryDslRepository implements EventReminderRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QEventReminder eventReminder = QEventReminder.eventReminder;

    @Override
    public Optional<EventReminder> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(eventReminder)
                .where(eventReminder.rowId.eq(rowId))
                .fetchOne()
        );
    }

    @Override
    public List<EventReminder> findByEventId(Long eventRowId) {
        return queryFactory.selectFrom(eventReminder)
            .where(eventReminder.event.rowId.eq(eventRowId))
            .orderBy(eventReminder.minutesBefore.asc())
            .fetch();
    }

    @Override
    public List<EventReminder> findByEventIds(List<Long> eventRowIds) {
        if (eventRowIds == null || eventRowIds.isEmpty()) {
            return List.of();
        }
        return queryFactory.selectFrom(eventReminder)
            .where(eventReminder.event.rowId.in(eventRowIds))
            .orderBy(eventReminder.minutesBefore.asc())
            .fetch();
    }

    @Override
    public EventReminder save(EventReminder entity) {
        if (entity.getRowId() == null) {
            entityManager.persist(entity);
        } else {
            entityManager.merge(entity);
        }
        return entity;
    }

    @Override
    public void deleteByEventId(Long eventRowId) {
        queryFactory.delete(eventReminder)
            .where(eventReminder.event.rowId.eq(eventRowId))
            .execute();
    }

    @Override
    public void deleteById(Long rowId) {
        queryFactory.delete(eventReminder)
            .where(eventReminder.rowId.eq(rowId))
            .execute();
    }
}
