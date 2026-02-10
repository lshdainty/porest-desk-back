package com.porest.desk.todo.repository;

import com.porest.desk.todo.domain.TodoProject;

import java.util.List;
import java.util.Optional;

public interface TodoProjectRepository {
    Optional<TodoProject> findById(Long rowId);
    List<TodoProject> findAllByUser(Long userRowId);
    TodoProject save(TodoProject project);
    void delete(TodoProject project);
}
