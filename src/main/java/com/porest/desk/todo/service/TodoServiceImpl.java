package com.porest.desk.todo.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.constellation.service.StarlightService;
import com.porest.desk.todo.domain.Todo;
import com.porest.desk.todo.domain.TodoTag;
import com.porest.desk.todo.domain.TodoTagMapping;
import com.porest.desk.todo.repository.TodoRepository;
import com.porest.desk.todo.repository.TodoTagMappingRepository;
import com.porest.desk.todo.repository.TodoTagRepository;
import com.porest.desk.todo.service.dto.TodoServiceDto;
import com.porest.desk.todo.type.TodoPriority;
import com.porest.desk.todo.type.TodoStatus;
import com.porest.desk.todo.type.TodoType;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import com.porest.core.time.UserClock;
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
    private final UserClock userClock;
    private final TodoTagRepository todoTagRepository;
    private final TodoTagMappingRepository todoTagMappingRepository;
    private final UserRepository userRepository;
    private final StarlightService starlightService;

    @Override
    @Transactional
    public TodoServiceDto.TodoInfo createTodo(TodoServiceDto.CreateCommand command) {
        log.debug("할일 등록 시작: userRowId={}, title={}", command.userRowId(), command.title());

        User user = userRepository.findById(command.userRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

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
            command.category(), command.dueDate(), parent, type
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
    public List<TodoServiceDto.TodoInfo> getTodos(Long userRowId, TodoStatus status, TodoPriority priority, String category, LocalDate startDate, LocalDate endDate, TodoType type) {
        log.debug("할일 목록 조회: userRowId={}, status={}, priority={}, type={}", userRowId, status, priority, type);

        List<Todo> todos = todoRepository.findAllByUser(userRowId, status, priority, category, startDate, endDate, type);

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

        todo.updateTodo(
            command.title(), command.content(), command.priority(),
            command.category(), command.dueDate()
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
        // 별자리 게이미피케이션 — 완료 전이면 별빛 적립(당일 회수분은 복원), 해제면 당일 회수
        // (같은 트랜잭션). 실제 적립량을 응답에 실어 화면 "+N" 토스트가 거짓이 되지 않게 한다.
        int earnedStarlight = starlightService.onTodoStatusToggled(todo);

        log.info("할일 상태 토글 완료: todoId={}, newStatus={}, earnedStarlight={}",
            todoId, todo.getStatus(), earnedStarlight);

        return buildTodoInfo(todo).withEarnedStarlight(earnedStarlight);
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
            validateTodoOwnership(todo, userRowId); // 남의 할일 순서 조작 차단
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

        LocalDate today = userClock.today(userRowId);
        // 단일 집계 쿼리로 모든 카운트를 한번에 조회 (전체 엔티티 로드 대신)
        long[] stats = todoRepository.countStatsByUser(userRowId, today);
        // [0]=totalTask, [1]=pending, [2]=inProgress, [3]=completed, [4]=todayDue, [5]=overDue, [6]=noteCount, [7]=pinnedNoteCount

        return new TodoServiceDto.TodoStats(stats[0], stats[1], stats[2], stats[3], stats[4], stats[5], stats[6]);
    }

    private void validateTodoOwnership(Todo todo, Long userRowId) {
        if (!todo.getUser().getRowId().equals(userRowId)) {
            log.warn("할일 소유권 검증 실패 - todoId={}, ownerRowId={}, requestUserRowId={}",
                todo.getRowId(), todo.getUser().getRowId(), userRowId);
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
        // 태그 조회 (findByTodoId는 이미 fetchJoin 적용됨)
        List<TodoTagMapping> mappings = todoTagMappingRepository.findByTodoId(todo.getRowId());
        List<TodoServiceDto.TagInfo> tags = mappings.stream()
            .map(m -> new TodoServiceDto.TagInfo(m.getTag().getRowId(), m.getTag().getTagName(), m.getTag().getColor()))
            .toList();

        // 서브태스크 카운트를 배치 쿼리로 조회 (엔티티 전체 로드 대신 count만)
        Map<Long, int[]> counts = todoRepository.findSubtaskCountsByParentIds(List.of(todo.getRowId()));
        int[] subtaskCounts = counts.getOrDefault(todo.getRowId(), new int[]{0, 0});

        return TodoServiceDto.TodoInfo.from(todo, tags, subtaskCounts[0], subtaskCounts[1]);
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

        return todoRepository.findSubtaskCountsByParentIds(todoIds);
    }
}
