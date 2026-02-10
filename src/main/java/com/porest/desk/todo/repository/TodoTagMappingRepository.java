package com.porest.desk.todo.repository;

import com.porest.desk.todo.domain.TodoTagMapping;

import java.util.List;

public interface TodoTagMappingRepository {
    List<TodoTagMapping> findByTodoId(Long todoRowId);
    List<TodoTagMapping> findByTodoIds(List<Long> todoRowIds);
    TodoTagMapping save(TodoTagMapping mapping);
    void deleteByTodoId(Long todoRowId);
}
