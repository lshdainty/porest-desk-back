package com.porest.desk.todo.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.todo.domain.Todo;
import com.porest.desk.todo.domain.TodoProject;
import com.porest.desk.todo.domain.TodoTag;
import com.porest.desk.todo.domain.TodoTagMapping;
import com.porest.desk.todo.repository.TodoProjectRepository;
import com.porest.desk.todo.repository.TodoRepository;
import com.porest.desk.todo.repository.TodoTagMappingRepository;
import com.porest.desk.todo.repository.TodoTagRepository;
import com.porest.desk.todo.service.dto.TodoServiceDto;
import com.porest.desk.todo.type.TodoPriority;
import com.porest.desk.todo.type.TodoStatus;
import com.porest.desk.todo.type.TodoType;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TodoServiceImpl implements TodoService {
    private final TodoRepository todoRepository;
    private final TodoProjectRepository todoProjectRepository;
    private final TodoTagRepository todoTagRepository;
    private final TodoTagMappingRepository todoTagMappingRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public TodoServiceDto.TodoInfo createTodo(TodoServiceDto.CreateCommand command) {
        log.debug("할일 등록 시작: userRowId={}, title={}", command.userRowId(), command.title());

        User user = userRepository.findById(command.userRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

        TodoProject project = null;
        if (command.projectRowId() != null) {
            project = todoProjectRepository.findById(command.projectRowId())
                .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.TODO_PROJECT_NOT_FOUND));
            validateProjectOwnership(project, command.userRowId());
        }

        Todo parent = null;
        if (command.parentRowId() != null) {
            parent = findTodoOrThrow(command.parentRowId());
            validateTodoOwnership(parent, command.userRowId());
        }

        TodoType type = command.type() != null ? command.type() : TodoType.TASK;
        TodoPriority priority = command.priority();
        if (type == TodoType.NOTE) {
            priority = TodoPriority.LOW;
        }

        Todo todo = Todo.createTodo(
            user, command.title(), command.content(), priority,
            command.category(), command.dueDate(), project, parent, type
        );

        todoRepository.save(todo);

        // Handle tags
        if (command.tagIds() != null && !command.tagIds().isEmpty()) {
            List<TodoTag> tags = todoTagRepository.findAllByIds(command.tagIds());
            for (TodoTag tag : tags) {
                todoTagMappingRepository.save(TodoTagMapping.create(todo, tag));
            }
        }

        log.info("할일 등록 완료: todoId={}, userRowId={}, type={}", todo.getRowId(), command.userRowId(), type);

        return buildTodoInfo(todo);
    }

    @Override
    public List<TodoServiceDto.TodoInfo> getTodos(Long userRowId, TodoStatus status, TodoPriority priority, String category, LocalDate startDate, LocalDate endDate, Long projectRowId, TodoType type) {
        log.debug("할일 목록 조회: userRowId={}, status={}, priority={}, type={}", userRowId, status, priority, type);

        List<Todo> todos = todoRepository.findAllByUser(userRowId, status, priority, category, startDate, endDate, projectRowId, type);

        // Batch load tags and subtask counts
        List<Long> todoIds = todos.stream().map(Todo::getRowId).toList();
        Map<Long, List<TodoServiceDto.TagInfo>> tagsMap = loadTagsMap(todoIds);
        Map<Long, int[]> subtaskCountsMap = loadSubtaskCountsMap(todoIds);

        return todos.stream()
            .map(todo -> {
                List<TodoServiceDto.TagInfo> tags = tagsMap.getOrDefault(todo.getRowId(), List.of());
                int[] counts = subtaskCountsMap.getOrDefault(todo.getRowId(), new int[]{0, 0});
                return TodoServiceDto.TodoInfo.from(todo, tags, counts[0], counts[1]);
            })
            .toList();
    }

    @Override
    public TodoServiceDto.TodoInfo getTodo(Long todoId, Long userRowId) {
        log.debug("할일 상세 조회: todoId={}", todoId);

        Todo todo = findTodoOrThrow(todoId);
        validateTodoOwnership(todo, userRowId);

        return buildTodoInfo(todo);
    }

    @Override
    @Transactional
    public TodoServiceDto.TodoInfo updateTodo(Long todoId, Long userRowId, TodoServiceDto.UpdateCommand command) {
        log.debug("할일 수정 시작: todoId={}", todoId);

        Todo todo = findTodoOrThrow(todoId);
        validateTodoOwnership(todo, userRowId);

        TodoProject project = null;
        if (command.projectRowId() != null) {
            project = todoProjectRepository.findById(command.projectRowId())
                .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.TODO_PROJECT_NOT_FOUND));
        }

        todo.updateTodo(
            command.title(), command.content(), command.priority(),
            command.category(), command.dueDate(), project
        );

        // Update tags if provided
        if (command.tagIds() != null) {
            todoTagMappingRepository.deleteByTodoId(todoId);
            List<TodoTag> tags = todoTagRepository.findAllByIds(command.tagIds());
            for (TodoTag tag : tags) {
                todoTagMappingRepository.save(TodoTagMapping.create(todo, tag));
            }
        }

        log.info("할일 수정 완료: todoId={}", todoId);

        return buildTodoInfo(todo);
    }

    @Override
    @Transactional
    public TodoServiceDto.TodoInfo toggleStatus(Long todoId, Long userRowId) {
        log.debug("할일 상태 토글 시작: todoId={}", todoId);

        Todo todo = findTodoOrThrow(todoId);
        validateTodoOwnership(todo, userRowId);
        todo.toggleStatus();

        log.info("할일 상태 토글 완료: todoId={}, newStatus={}", todoId, todo.getStatus());

        return buildTodoInfo(todo);
    }

    @Override
    @Transactional
    public TodoServiceDto.TodoInfo togglePin(Long todoId, Long userRowId) {
        log.debug("할일 고정 토글 시작: todoId={}", todoId);

        Todo todo = findTodoOrThrow(todoId);
        validateTodoOwnership(todo, userRowId);
        todo.togglePin();

        log.info("할일 고정 토글 완료: todoId={}, isPinned={}", todoId, todo.getIsPinned());

        return buildTodoInfo(todo);
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
    public void deleteTodo(Long todoId, Long userRowId) {
        log.debug("할일 삭제 시작: todoId={}", todoId);

        Todo todo = findTodoOrThrow(todoId);
        validateTodoOwnership(todo, userRowId);
        todo.deleteTodo();

        // Also delete subtasks
        List<Todo> subtasks = todoRepository.findSubtasks(todoId);
        for (Todo subtask : subtasks) {
            subtask.deleteTodo();
        }

        log.info("할일 삭제 완료: todoId={}", todoId);
    }

    @Override
    public List<TodoServiceDto.TodoInfo> getSubtasks(Long parentRowId, Long userRowId) {
        log.debug("서브태스크 조회: parentRowId={}", parentRowId);

        Todo parentTodo = findTodoOrThrow(parentRowId);
        validateTodoOwnership(parentTodo, userRowId);

        List<Todo> subtasks = todoRepository.findSubtasks(parentRowId);

        List<Long> subtaskIds = subtasks.stream().map(Todo::getRowId).toList();
        Map<Long, List<TodoServiceDto.TagInfo>> tagsMap = loadTagsMap(subtaskIds);

        return subtasks.stream()
            .map(todo -> TodoServiceDto.TodoInfo.from(todo, tagsMap.getOrDefault(todo.getRowId(), List.of()), 0, 0))
            .toList();
    }

    @Override
    @Transactional
    public void updateTags(Long todoId, Long userRowId, List<Long> tagIds) {
        log.debug("태그 업데이트 시작: todoId={}, tagIds={}", todoId, tagIds);

        Todo todo = findTodoOrThrow(todoId);
        validateTodoOwnership(todo, userRowId);
        todoTagMappingRepository.deleteByTodoId(todoId);

        if (tagIds != null && !tagIds.isEmpty()) {
            List<TodoTag> tags = todoTagRepository.findAllByIds(tagIds);
            for (TodoTag tag : tags) {
                todoTagMappingRepository.save(TodoTagMapping.create(todo, tag));
            }
        }

        log.info("태그 업데이트 완료: todoId={}", todoId);
    }

    @Override
    public TodoServiceDto.TodoStats getStats(Long userRowId) {
        log.debug("할일 통계 조회: userRowId={}", userRowId);

        List<Todo> allTodos = todoRepository.findAllByUser(userRowId, null, null, null, null, null, null, null);

        long totalCount = allTodos.stream().filter(t -> t.getType() == TodoType.TASK).count();
        long pendingCount = allTodos.stream().filter(t -> t.getType() == TodoType.TASK && t.getStatus() == TodoStatus.PENDING).count();
        long inProgressCount = allTodos.stream().filter(t -> t.getType() == TodoType.TASK && t.getStatus() == TodoStatus.IN_PROGRESS).count();
        long completedCount = allTodos.stream().filter(t -> t.getType() == TodoType.TASK && t.getStatus() == TodoStatus.COMPLETED).count();
        long noteCount = allTodos.stream().filter(t -> t.getType() == TodoType.NOTE).count();

        LocalDate today = LocalDate.now();
        long todayDueCount = allTodos.stream()
            .filter(t -> t.getType() == TodoType.TASK && t.getDueDate() != null && t.getDueDate().isEqual(today))
            .count();
        long overDueCount = allTodos.stream()
            .filter(t -> t.getType() == TodoType.TASK && t.getDueDate() != null && t.getDueDate().isBefore(today) && t.getStatus() != TodoStatus.COMPLETED)
            .count();

        return new TodoServiceDto.TodoStats(totalCount, pendingCount, inProgressCount, completedCount, todayDueCount, overDueCount, noteCount);
    }

    private void validateTodoOwnership(Todo todo, Long userRowId) {
        if (!todo.getUser().getRowId().equals(userRowId)) {
            log.warn("할일 소유권 검증 실패 - todoId={}, ownerRowId={}, requestUserRowId={}",
                todo.getRowId(), todo.getUser().getRowId(), userRowId);
            throw new ForbiddenException(DeskErrorCode.TODO_ACCESS_DENIED);
        }
    }

    private void validateProjectOwnership(TodoProject project, Long userRowId) {
        if (!project.getUser().getRowId().equals(userRowId)) {
            log.warn("프로젝트 소유권 검증 실패 - projectId={}, ownerRowId={}, requestUserRowId={}",
                project.getRowId(), project.getUser().getRowId(), userRowId);
            throw new ForbiddenException(DeskErrorCode.TODO_ACCESS_DENIED);
        }
    }

    private Todo findTodoOrThrow(Long todoId) {
        return todoRepository.findById(todoId)
            .orElseThrow(() -> {
                log.warn("할일 조회 실패 - 존재하지 않는 할일: todoId={}", todoId);
                return new EntityNotFoundException(DeskErrorCode.TODO_NOT_FOUND);
            });
    }

    private TodoServiceDto.TodoInfo buildTodoInfo(Todo todo) {
        List<TodoTagMapping> mappings = todoTagMappingRepository.findByTodoId(todo.getRowId());
        List<TodoServiceDto.TagInfo> tags = mappings.stream()
            .map(m -> new TodoServiceDto.TagInfo(m.getTag().getRowId(), m.getTag().getTagName(), m.getTag().getColor()))
            .toList();

        List<Todo> subtasks = todoRepository.findSubtasks(todo.getRowId());
        int subtaskCount = subtasks.size();
        int subtaskCompletedCount = (int) subtasks.stream().filter(s -> s.getStatus() == TodoStatus.COMPLETED).count();

        return TodoServiceDto.TodoInfo.from(todo, tags, subtaskCount, subtaskCompletedCount);
    }

    private Map<Long, List<TodoServiceDto.TagInfo>> loadTagsMap(List<Long> todoIds) {
        if (todoIds.isEmpty()) return Map.of();

        List<TodoTagMapping> allMappings = todoTagMappingRepository.findByTodoIds(todoIds);
        return allMappings.stream()
            .collect(Collectors.groupingBy(
                m -> m.getTodo().getRowId(),
                Collectors.mapping(
                    m -> new TodoServiceDto.TagInfo(m.getTag().getRowId(), m.getTag().getTagName(), m.getTag().getColor()),
                    Collectors.toList()
                )
            ));
    }

    private Map<Long, int[]> loadSubtaskCountsMap(List<Long> todoIds) {
        if (todoIds.isEmpty()) return Map.of();

        return todoIds.stream()
            .collect(Collectors.toMap(
                id -> id,
                id -> {
                    List<Todo> subtasks = todoRepository.findSubtasks(id);
                    int total = subtasks.size();
                    int completed = (int) subtasks.stream().filter(s -> s.getStatus() == TodoStatus.COMPLETED).count();
                    return new int[]{total, completed};
                }
            ));
    }
}
