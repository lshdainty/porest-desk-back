package com.porest.desk.calendar.repository;

import com.porest.core.type.YNType;
import com.porest.desk.calendar.domain.CalendarEvent;
import com.porest.desk.calendar.domain.QCalendarEvent;
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
public class CalendarEventQueryDslRepository implements CalendarEventRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QCalendarEvent calendarEvent = QCalendarEvent.calendarEvent;

    @Override
    public Optional<CalendarEvent> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(calendarEvent)
                .leftJoin(calendarEvent.user).fetchJoin()
                .leftJoin(calendarEvent.label).fetchJoin()
                .leftJoin(calendarEvent.calendar).fetchJoin()
                .leftJoin(calendarEvent.group).fetchJoin()
                .where(calendarEvent.rowId.eq(rowId), calendarEvent.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public List<CalendarEvent> findByUserAndDateRange(Long userRowId, LocalDateTime startDate, LocalDateTime endDate) {
        return queryFactory.selectFrom(calendarEvent)
            .leftJoin(calendarEvent.label).fetchJoin()
            .leftJoin(calendarEvent.calendar).fetchJoin()
            .leftJoin(calendarEvent.group).fetchJoin()
            .where(
                calendarEvent.user.rowId.eq(userRowId),
                calendarEvent.isDeleted.eq(YNType.N),
                calendarEvent.startDate.loe(endDate),
                calendarEvent.endDate.goe(startDate)
            )
            .orderBy(calendarEvent.startDate.asc())
            .fetch();
    }

    @Override
    public List<CalendarEvent> findByCalendarId(Long calendarRowId) {
        return queryFactory.selectFrom(calendarEvent)
            .leftJoin(calendarEvent.user).fetchJoin()
            .leftJoin(calendarEvent.label).fetchJoin()
            .leftJoin(calendarEvent.calendar).fetchJoin()
            .where(
                calendarEvent.calendar.rowId.eq(calendarRowId),
                calendarEvent.isDeleted.eq(YNType.N)
            )
            .fetch();
    }

    @Override
    public List<CalendarEvent> findByGroupsAndDateRange(List<Long> groupRowIds, LocalDateTime startDate, LocalDateTime endDate) {
        if (groupRowIds.isEmpty()) return List.of();
        return queryFactory.selectFrom(calendarEvent)
            .leftJoin(calendarEvent.user).fetchJoin()
            .leftJoin(calendarEvent.label).fetchJoin()
            .leftJoin(calendarEvent.calendar).fetchJoin()
            .leftJoin(calendarEvent.group).fetchJoin()
            .where(
                calendarEvent.group.rowId.in(groupRowIds),
                calendarEvent.isDeleted.eq(YNType.N),
                calendarEvent.startDate.loe(endDate),
                calendarEvent.endDate.goe(startDate)
            )
            .orderBy(calendarEvent.startDate.asc())
            .fetch();
    }

    @Override
    public CalendarEvent save(CalendarEvent entity) {
        if (entity.getRowId() == null) {
            entityManager.persist(entity);
        } else {
            entityManager.merge(entity);
        }
        return entity;
    }
}
