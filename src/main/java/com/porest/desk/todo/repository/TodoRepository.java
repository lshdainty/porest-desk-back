package com.porest.desk.todo.repository;

import com.porest.desk.todo.domain.Todo;
import com.porest.desk.todo.type.TodoPriority;
import com.porest.desk.todo.type.TodoStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TodoRepository {
    Optional<Todo> findById(Long rowId);
    List<Todo> findAllByUser(Long userRowId, TodoStatus status, TodoPriority priority, String category, LocalDate startDate, LocalDate endDate);
    List<Todo> findByUserAndDueDateBetween(Long userRowId, LocalDate startDate, LocalDate endDate);
    Todo save(Todo todo);
    void delete(Todo todo);
}
