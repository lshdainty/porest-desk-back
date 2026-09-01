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
                .where(calendarEvent.rowId.eq(rowId), calendarEvent.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public List<CalendarEvent> findByUserAndDateRange(Long userRowId, LocalDateTime startDate, LocalDateTime endDate) {
        return queryFactory.selectFrom(calendarEvent)
            .leftJoin(calendarEvent.label).fetchJoin()
            .leftJoin(calendarEvent.calendar).fetchJoin()
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
    public List<CalendarEvent> findByCalendarIdsAndDateRange(List<Long> calendarRowIds, LocalDateTime startDate, LocalDateTime endDate) {
        if (calendarRowIds == null || calendarRowIds.isEmpty()) return List.of();
        return queryFactory.selectFrom(calendarEvent)
            .leftJoin(calendarEvent.user).fetchJoin()
            .leftJoin(calendarEvent.label).fetchJoin()
            .leftJoin(calendarEvent.calendar).fetchJoin()
            .where(
                calendarEvent.calendar.rowId.in(calendarRowIds),
                calendarEvent.isDeleted.eq(YNType.N),
                calendarEvent.startDate.loe(endDate),
                // 반복 이벤트는 원본이 구간보다 앞서 시작해도 이번 구간에 발생이
                // 떨어질 수 있다 — endDate(원본 1회차의 끝) 조건을 면제하고
                // 서비스의 RecurrenceExpander 가 구간 안 발생만 남긴다.
                calendarEvent.endDate.goe(startDate).or(calendarEvent.rrule.isNotNull())
            )
            .orderBy(calendarEvent.startDate.asc())
            .fetch();
    }

    @Override
    public CalendarEvent save(CalendarEvent entity) {
        entityManager.persist(entity);
        return entity;
    }
}
