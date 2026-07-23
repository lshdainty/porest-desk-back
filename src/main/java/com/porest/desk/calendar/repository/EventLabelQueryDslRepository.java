package com.porest.desk.calendar.repository;

import com.porest.core.type.YNType;
import com.porest.desk.calendar.domain.EventLabel;
import com.porest.desk.calendar.domain.QCalendarEvent;
import com.porest.desk.calendar.domain.QEventLabel;
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
public class EventLabelQueryDslRepository implements EventLabelRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QEventLabel eventLabel = QEventLabel.eventLabel;
    private static final QCalendarEvent calendarEvent = QCalendarEvent.calendarEvent;

    @Override
    public Optional<EventLabel> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(eventLabel)
                .where(eventLabel.rowId.eq(rowId), eventLabel.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public boolean existsActiveByUserAndName(Long userRowId, String labelName, Long excludeRowId) {
        return queryFactory.selectOne()
            .from(eventLabel)
            .where(
                eventLabel.user.rowId.eq(userRowId),
                eventLabel.labelName.eq(labelName),
                eventLabel.isDeleted.eq(YNType.N),
                excludeRowId != null ? eventLabel.rowId.ne(excludeRowId) : null
            )
            .fetchFirst() != null;
    }

    @Override
    public List<EventLabel> findAllByUser(Long userRowId) {
        return queryFactory.selectFrom(eventLabel)
            .where(eventLabel.user.rowId.eq(userRowId), eventLabel.isDeleted.eq(YNType.N))
            .orderBy(eventLabel.sortOrder.asc(), eventLabel.rowId.asc())
            .fetch();
    }

    @Override
    public Map<Long, Long> countEventsByLabel(Long userRowId) {
        return queryFactory
            .select(calendarEvent.label.rowId, calendarEvent.count())
            .from(calendarEvent)
            .where(
                calendarEvent.label.isNotNull(),
                calendarEvent.label.user.rowId.eq(userRowId),
                calendarEvent.isDeleted.eq(YNType.N)
            )
            .groupBy(calendarEvent.label.rowId)
            .fetch()
            .stream()
            .collect(Collectors.toMap(
                t -> t.get(calendarEvent.label.rowId),
                t -> t.get(calendarEvent.count()) == null ? 0L : t.get(calendarEvent.count())
            ));
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
