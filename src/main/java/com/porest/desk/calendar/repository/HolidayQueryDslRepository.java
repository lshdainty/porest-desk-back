package com.porest.desk.calendar.repository;

import com.porest.core.type.YNType;
import com.porest.desk.calendar.domain.Holiday;
import com.porest.desk.calendar.domain.QHoliday;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
@Primary
@RequiredArgsConstructor
public class HolidayQueryDslRepository implements HolidayRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QHoliday holiday = QHoliday.holiday;

    @Override
    public Optional<Holiday> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(holiday)
                .where(holiday.rowId.eq(rowId), holiday.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public List<Holiday> findByDateRange(LocalDate startDate, LocalDate endDate) {
        return queryFactory.selectFrom(holiday)
            .where(
                holiday.isDeleted.eq(YNType.N),
                holiday.isRecurring.eq(YNType.N),
                holiday.holidayDate.goe(startDate),
                holiday.holidayDate.loe(endDate)
            )
            .orderBy(holiday.holidayDate.asc())
            .fetch();
    }

    @Override
    public List<Holiday> findAllRecurring() {
        return queryFactory.selectFrom(holiday)
            .where(
                holiday.isDeleted.eq(YNType.N),
                holiday.isRecurring.eq(YNType.Y)
            )
            .orderBy(holiday.holidayDate.asc())
            .fetch();
    }

    @Override
    public Holiday save(Holiday entity) {
        if (entity.getRowId() == null) {
            entityManager.persist(entity);
        } else {
            entityManager.merge(entity);
        }
        return entity;
    }
}
