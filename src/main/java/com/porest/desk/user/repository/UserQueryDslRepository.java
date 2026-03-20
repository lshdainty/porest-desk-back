package com.porest.desk.user.repository;

import com.porest.core.type.YNType;
import com.porest.desk.user.domain.QUser;
import com.porest.desk.user.domain.User;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@Primary
@RequiredArgsConstructor
public class UserQueryDslRepository implements UserRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QUser user = QUser.user;

    @Override
    public Optional<User> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(user)
                .where(user.rowId.eq(rowId), user.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public Optional<User> findByUserId(String userId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(user)
                .where(user.userId.eq(userId), user.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public User save(User entity) {
        entityManager.persist(entity);
        return entity;
    }
}
