package com.porest.desk.todo.repository;

import com.porest.core.type.YNType;
import com.porest.desk.todo.domain.QTodoProject;
import com.porest.desk.todo.domain.TodoProject;
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
public class TodoProjectQueryDslRepository implements TodoProjectRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QTodoProject project = QTodoProject.todoProject;

    @Override
    public Optional<TodoProject> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(project)
                .where(project.rowId.eq(rowId), project.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public List<TodoProject> findAllByUser(Long userRowId) {
        return queryFactory.selectFrom(project)
            .where(project.user.rowId.eq(userRowId), project.isDeleted.eq(YNType.N))
            .orderBy(project.sortOrder.asc(), project.rowId.asc())
            .fetch();
    }

    @Override
    public TodoProject save(TodoProject entity) {
        if (entity.getRowId() == null) {
            entityManager.persist(entity);
        } else {
            entityManager.merge(entity);
        }
        return entity;
    }

    @Override
    public void delete(TodoProject entity) {
        entity.deleteProject();
    }
}
