package com.porest.desk.user.repository;

import com.porest.core.type.YNType;
import com.porest.desk.user.domain.User;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository("userJpaRepository")
@RequiredArgsConstructor
public class UserJpaRepository implements UserRepository {
    private final EntityManager entityManager;

    @Override
    public Optional<User> findById(Long rowId) {
        return entityManager.createQuery(
            "SELECT u FROM User u WHERE u.rowId = :rowId AND u.isDeleted = :isDeleted", User.class)
            .setParameter("rowId", rowId)
            .setParameter("isDeleted", YNType.N)
            .getResultStream()
            .findFirst();
    }

    @Override
    public Optional<User> findByUserId(String userId) {
        return entityManager.createQuery(
            "SELECT u FROM User u WHERE u.userId = :userId AND u.isDeleted = :isDeleted", User.class)
            .setParameter("userId", userId)
            .setParameter("isDeleted", YNType.N)
            .getResultStream()
            .findFirst();
    }

    @Override
    public User save(User entity) {
        entityManager.persist(entity);
        return entity;
    }
}
