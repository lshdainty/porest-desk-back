package com.porest.desk.group.repository;

import com.porest.core.type.YNType;
import com.porest.desk.group.domain.GroupType;
import com.porest.desk.group.domain.QGroupType;
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
public class GroupTypeQueryDslRepository implements GroupTypeRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QGroupType groupType = QGroupType.groupType;

    @Override
    public Optional<GroupType> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(groupType)
                .where(groupType.rowId.eq(rowId), groupType.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public List<GroupType> findAllByUser(Long userRowId) {
        return queryFactory.selectFrom(groupType)
            .where(
                groupType.user.rowId.eq(userRowId),
                groupType.isDeleted.eq(YNType.N)
            )
            .orderBy(groupType.sortOrder.asc())
            .fetch();
    }

    @Override
    public GroupType save(GroupType entity) {
        if (entity.getRowId() == null) {
            entityManager.persist(entity);
        } else {
            entityManager.merge(entity);
        }
        return entity;
    }
}
