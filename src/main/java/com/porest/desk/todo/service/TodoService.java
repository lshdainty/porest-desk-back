package com.porest.desk.todo.service;

import com.porest.desk.todo.service.dto.TodoServiceDto;
import com.porest.desk.todo.type.TodoPriority;
import com.porest.desk.todo.type.TodoStatus;
import com.porest.desk.todo.type.TodoType;

import java.time.LocalDate;
import java.util.List;

public interface TodoService {
    TodoServiceDto.TodoInfo createTodo(TodoServiceDto.CreateCommand command);
    List<TodoServiceDto.TodoInfo> getTodos(Long userRowId, TodoStatus status, TodoPriority priority, String category, LocalDate startDate, LocalDate endDate, Long projectRowId, TodoType type);
    TodoServiceDto.TodoInfo getTodo(Long todoId, Long userRowId);
    TodoServiceDto.TodoInfo updateTodo(Long todoId, Long userRowId, TodoServiceDto.UpdateCommand command);
    TodoServiceDto.TodoInfo toggleStatus(Long todoId, Long userRowId);
    TodoServiceDto.TodoInfo togglePin(Long todoId, Long userRowId);
    void reorderTodos(Long userRowId, TodoServiceDto.ReorderCommand command);
    void deleteTodo(Long todoId, Long userRowId);
    List<TodoServiceDto.TodoInfo> getSubtasks(Long parentRowId, Long userRowId);
    void updateTags(Long todoId, Long userRowId, List<Long> tagIds);
    TodoServiceDto.TodoStats getStats(Long userRowId);
}
