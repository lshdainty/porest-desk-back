package com.porest.desk.todo.repository;

import com.porest.desk.todo.domain.TodoTag;

import java.util.List;
import java.util.Optional;

public interface TodoTagRepository {
    Optional<TodoTag> findById(Long rowId);
    List<TodoTag> findAllByUser(Long userRowId);
    List<TodoTag> findAllByIds(List<Long> ids);
    TodoTag save(TodoTag tag);
    void delete(TodoTag tag);
}
