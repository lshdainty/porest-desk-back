package com.porest.desk.calendar.repository;

import com.porest.core.type.YNType;
import com.porest.desk.calendar.domain.EventReminder;
import com.porest.desk.calendar.domain.QCalendarEvent;
import com.porest.desk.calendar.domain.QEventReminder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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
    public List<EventReminder> findUnsentDueReminders(LocalDateTime now) {
        QCalendarEvent calendarEvent = QCalendarEvent.calendarEvent;

        // startDate - minutesBefore <= now  →  startDate <= now + minutesBefore
        return queryFactory.selectFrom(eventReminder)
            .leftJoin(eventReminder.event, calendarEvent).fetchJoin()
            .where(
                eventReminder.isSent.eq(YNType.N),
                calendarEvent.isDeleted.eq(YNType.N),
                calendarEvent.startDate.loe(
                    Expressions.dateTimeTemplate(LocalDateTime.class,
                        "timestampadd(minute, {0}, {1})",
                        eventReminder.minutesBefore, Expressions.constant(now)))
            )
            .fetch();
    }

    @Override
    public EventReminder save(EventReminder entity) {
        entityManager.persist(entity);
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
