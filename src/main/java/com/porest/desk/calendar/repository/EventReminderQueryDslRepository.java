package com.porest.desk.calendar.repository;

import com.porest.core.type.YNType;
import com.porest.desk.calendar.domain.EventReminder;
import com.porest.desk.calendar.domain.QCalendarEvent;
import com.porest.desk.calendar.domain.QEventReminder;
import com.porest.desk.user.domain.QUser;
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
    public List<EventReminder> findUnsentRemindersStartingBefore(LocalDateTime startBound) {
        QCalendarEvent calendarEvent = QCalendarEvent.calendarEvent;
        QUser user = QUser.user;

        // startDate 는 [userClock] 벽시계라 SQL 에서 UTC now 와 직접 비교하면 KST 사용자의
        // 리마인더가 9시간 늦게 잡힌다. 여기서는 후보(시작이 bound 이전인 미발송분)만 추리고,
        // 도래 판정(벽시계 due vs 소유자 타임존의 지금)은 스케줄러가 한다. user 를 fetchJoin
        // 하는 것도 그 판정에 소유자 timezone 이 필요해서다.
        return queryFactory.selectFrom(eventReminder)
            .join(eventReminder.event, calendarEvent).fetchJoin()
            .join(calendarEvent.user, user).fetchJoin()
            .where(
                eventReminder.isSent.eq(YNType.N),
                calendarEvent.isDeleted.eq(YNType.N),
                calendarEvent.startDate.loe(startBound)
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
