package com.porest.desk.todo.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.todo.domain.Todo;
import com.porest.desk.todo.repository.TodoRepository;
import com.porest.desk.todo.service.dto.TodoServiceDto;
import com.porest.desk.todo.type.TodoPriority;
import com.porest.desk.todo.type.TodoStatus;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TodoServiceImpl implements TodoService {
    private final TodoRepository todoRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public TodoServiceDto.TodoInfo createTodo(TodoServiceDto.CreateCommand command) {
        log.debug("할일 등록 시작: userRowId={}, title={}", command.userRowId(), command.title());

        User user = userRepository.findById(command.userRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

        Todo todo = Todo.createTodo(
            user,
            command.title(),
            command.content(),
            command.priority(),
            command.category(),
            command.dueDate()
        );

        todoRepository.save(todo);
        log.info("할일 등록 완료: todoId={}, userRowId={}", todo.getRowId(), command.userRowId());

        return TodoServiceDto.TodoInfo.from(todo);
    }

    @Override
    public List<TodoServiceDto.TodoInfo> getTodos(Long userRowId, TodoStatus status, TodoPriority priority, String category, LocalDate startDate, LocalDate endDate) {
        log.debug("할일 목록 조회: userRowId={}, status={}, priority={}", userRowId, status, priority);

        List<Todo> todos = todoRepository.findAllByUser(userRowId, status, priority, category, startDate, endDate);

        return todos.stream()
            .map(TodoServiceDto.TodoInfo::from)
            .toList();
    }

    @Override
    public TodoServiceDto.TodoInfo getTodo(Long todoId) {
        log.debug("할일 상세 조회: todoId={}", todoId);

        Todo todo = findTodoOrThrow(todoId);

        return TodoServiceDto.TodoInfo.from(todo);
    }

    @Override
    @Transactional
    public TodoServiceDto.TodoInfo updateTodo(Long todoId, TodoServiceDto.UpdateCommand command) {
        log.debug("할일 수정 시작: todoId={}", todoId);

        Todo todo = findTodoOrThrow(todoId);

        todo.updateTodo(
            command.title(),
            command.content(),
            command.priority(),
            command.category(),
            command.dueDate()
        );

        log.info("할일 수정 완료: todoId={}", todoId);

        return TodoServiceDto.TodoInfo.from(todo);
    }

    @Override
    @Transactional
    public TodoServiceDto.TodoInfo toggleStatus(Long todoId) {
        log.debug("할일 상태 토글 시작: todoId={}", todoId);

        Todo todo = findTodoOrThrow(todoId);
        todo.toggleStatus();

        log.info("할일 상태 토글 완료: todoId={}, newStatus={}", todoId, todo.getStatus());

        return TodoServiceDto.TodoInfo.from(todo);
    }

    @Override
    @Transactional
    public void reorderTodos(Long userRowId, TodoServiceDto.ReorderCommand command) {
        log.debug("할일 순서 변경 시작: userRowId={}, items={}", userRowId, command.items().size());

        for (TodoServiceDto.ReorderCommand.ReorderItem item : command.items()) {
            Todo todo = findTodoOrThrow(item.todoId());
            todo.updateSortOrder(item.sortOrder());
        }

        log.info("할일 순서 변경 완료: userRowId={}", userRowId);
    }

    @Override
    @Transactional
    public void deleteTodo(Long todoId) {
        log.debug("할일 삭제 시작: todoId={}", todoId);

        Todo todo = findTodoOrThrow(todoId);
        todo.deleteTodo();

        log.info("할일 삭제 완료: todoId={}", todoId);
    }

    private Todo findTodoOrThrow(Long todoId) {
        return todoRepository.findById(todoId)
            .orElseThrow(() -> {
                log.warn("할일 조회 실패 - 존재하지 않는 할일: todoId={}", todoId);
                return new EntityNotFoundException(DeskErrorCode.TODO_NOT_FOUND);
            });
    }
}
