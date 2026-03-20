package com.porest.desk.calendar.repository;

import com.porest.core.type.YNType;
import com.porest.desk.calendar.domain.QUserCalendar;
import com.porest.desk.calendar.domain.UserCalendar;
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
public class UserCalendarQueryDslRepository implements UserCalendarRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QUserCalendar userCalendar = QUserCalendar.userCalendar;

    @Override
    public Optional<UserCalendar> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(userCalendar)
                .where(userCalendar.rowId.eq(rowId), userCalendar.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public List<UserCalendar> findAllByUser(Long userRowId) {
        return queryFactory.selectFrom(userCalendar)
            .where(userCalendar.user.rowId.eq(userRowId), userCalendar.isDeleted.eq(YNType.N))
            .orderBy(userCalendar.sortOrder.asc(), userCalendar.rowId.asc())
            .fetch();
    }

    @Override
    public Optional<UserCalendar> findDefaultByUser(Long userRowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(userCalendar)
                .where(
                    userCalendar.user.rowId.eq(userRowId),
                    userCalendar.isDefault.eq(YNType.Y),
                    userCalendar.isDeleted.eq(YNType.N)
                )
                .fetchOne()
        );
    }

    @Override
    public UserCalendar save(UserCalendar entity) {
        entityManager.persist(entity);
        return entity;
    }
}
