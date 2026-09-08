package com.porest.desk.todo.service;

import com.porest.desk.todo.service.dto.TodoServiceDto;
import com.porest.desk.todo.type.TodoPriority;
import com.porest.desk.todo.type.TodoStatus;
import com.porest.desk.todo.type.TodoType;

import java.time.LocalDate;
import java.util.List;

public interface TodoService {
    TodoServiceDto.TodoInfo createTodo(TodoServiceDto.CreateCommand command);
    List<TodoServiceDto.TodoInfo> getTodos(Long userRowId, TodoStatus status, TodoPriority priority, String category, LocalDate startDate, LocalDate endDate, TodoType type);
    TodoServiceDto.TodoInfo getTodo(Long todoId, Long userRowId);
    TodoServiceDto.TodoInfo updateTodo(Long todoId, Long userRowId, TodoServiceDto.UpdateCommand command);
    /**
     * 상태 변경 — {@code status} 가 <b>null 이면 종전 토글</b>(완료 ↔ 대기).
     *
     * <p>본문 없이 부르는 옛 클라이언트(웹 {@code todoApi.toggleTodoStatus})가 있어 그 뜻을 남긴다.
     * 값을 실은 요청은 그 상태로 간다 — 그래야 {@code IN_PROGRESS} 에 닿을 방법이 생긴다(QA #93).
     */
    TodoServiceDto.TodoInfo changeStatus(Long todoId, Long userRowId, TodoStatus status);
    TodoServiceDto.TodoInfo togglePin(Long todoId, Long userRowId);
    void reorderTodos(Long userRowId, TodoServiceDto.ReorderCommand command);
    void deleteTodo(Long todoId, Long userRowId);
    void updateTags(Long todoId, Long userRowId, List<Long> tagIds);
    TodoServiceDto.TodoStats getStats(Long userRowId);
}
