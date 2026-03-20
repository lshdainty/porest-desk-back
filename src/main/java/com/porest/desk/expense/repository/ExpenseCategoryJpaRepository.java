package com.porest.desk.expense.repository;

import com.porest.core.type.YNType;
import com.porest.desk.expense.domain.ExpenseCategory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository("expenseCategoryJpaRepository")
@RequiredArgsConstructor
public class ExpenseCategoryJpaRepository implements ExpenseCategoryRepository {
    private final EntityManager entityManager;

    @Override
    public Optional<ExpenseCategory> findById(Long rowId) {
        return entityManager.createQuery(
            "SELECT c FROM ExpenseCategory c WHERE c.rowId = :rowId AND c.isDeleted = :isDeleted", ExpenseCategory.class)
            .setParameter("rowId", rowId)
            .setParameter("isDeleted", YNType.N)
            .getResultStream()
            .findFirst();
    }

    @Override
    public List<ExpenseCategory> findAllByUser(Long userRowId) {
        return entityManager.createQuery(
            "SELECT c FROM ExpenseCategory c WHERE c.user.rowId = :userRowId AND c.isDeleted = :isDeleted ORDER BY c.sortOrder ASC, c.rowId ASC", ExpenseCategory.class)
            .setParameter("userRowId", userRowId)
            .setParameter("isDeleted", YNType.N)
            .getResultList();
    }

    @Override
    public ExpenseCategory save(ExpenseCategory entity) {
        entityManager.persist(entity);
        return entity;
    }

    @Override
    public void delete(ExpenseCategory entity) {
        entity.deleteCategory();
    }

    @Override
    public boolean hasChildren(Long categoryRowId) {
        Long count = entityManager.createQuery(
            "SELECT COUNT(c) FROM ExpenseCategory c WHERE c.parent.rowId = :parentRowId AND c.isDeleted = :isDeleted", Long.class)
            .setParameter("parentRowId", categoryRowId)
            .setParameter("isDeleted", YNType.N)
            .getSingleResult();
        return count > 0;
    }
}
