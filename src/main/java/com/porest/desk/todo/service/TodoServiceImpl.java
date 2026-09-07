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
import java.util.Objects;
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
    private final TodoTagService todoTagService;

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
        // priority 는 NOT NULL 이다. 안 보내면 종전엔 저장이 409 "다른 곳에서 먼저 수정됐어요" 로
        // 튕겼는데, 앱의 하위 할 일 빠른 추가가 정확히 그 요청을 보낸다(제목만 보낸다) — 즉
        // 지금 운영에서 안 되는 화면이 있다(QA #81). 안 보낸 것은 "보통" 이라는 뜻으로 읽는다.
        TodoPriority priority = command.priority() != null ? command.priority() : TodoPriority.MEDIUM;
        if (type == TodoType.NOTE) {
            priority = TodoPriority.LOW;
        }

        // 태그 확보를 할 일 INSERT 앞에 둔다 — 확보는 새 트랜잭션에서 돌므로(findOrCreateByName)
        // 이 트랜잭션에 못 내보낸 변경이 없는 자리에서 끝내는 편이 안전하다.
        List<TodoTag> tags = resolveTagsForWrite(command.tagIds(), command.category(), command.userRowId());

        // category 도 태그 이름과 같은 값으로 남겨야 개명(renameCategory)이 이 행을 찾는다 —
        // 콜레이션이 무시해 주는 것은 끝공백뿐이라 " 업무" 와 "업무" 는 DB 가 다른 값으로 본다.
        Todo todo = Todo.createTodo(
            user, command.title(), command.content(), priority,
            blankToNull(command.category()), command.dueDate(), parent, type
        );

        todoRepository.save(todo);

        for (TodoTag tag : tags) {
            todoTagMappingRepository.save(TodoTagMapping.create(todo, tag));
        }

        log.info("할일 등록 완료: todoId={}, userRowId={}, type={}, tags={}",
            todo.getRowId(), command.userRowId(), type, tags.size());

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

        // ★ todo.updateTodo(...) 가 category 를 덮으므로 옛 값을 그 줄 앞에서 잡는다.
        String previousCategory = todo.getCategory();

        if (command.tagIds() != null) {
            // 태그를 명시한 요청이 이긴다 — 빈 목록은 "태그 없음" 이라는 뜻이다.
            List<TodoTag> tags = resolveOwnedTags(command.tagIds(), userRowId);
            todo.updateTodo(command.title(), command.content(), command.priority(),
                blankToNull(command.category()), command.dueDate());
            replaceMappings(todo, tags);
        } else {
            syncCategoryBridge(todo, userRowId, previousCategory, command.category());
            todo.updateTodo(command.title(), command.content(), command.priority(),
                blankToNull(command.category()), command.dueDate());
        }

        log.info("할일 수정 완료: todoId={}", todoId);

        return buildTodoInfo(todo);
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>전이를 막지 않는다.</b> 세 상태는 사용자가 자기 할 일에 붙이는 표시일 뿐이고,
     * 대기 → 완료 → 진행 중 → 대기 어느 쪽으로 가도 잃는 데이터가 없다. 순서를 강제하면
     * 잘못 누른 것을 되돌리는 길만 막힌다. 별빛은 <b>완료냐 아니냐</b>만 보므로
     * ({@code onTodoStatusToggled}) 진행 중은 대기와 같게 다뤄져 회수까지 자동으로 맞는다.
     */
    @Override
    @Transactional
    public TodoServiceDto.TodoInfo changeStatus(Long todoId, Long userRowId, TodoStatus status) {
        log.debug("할일 상태 변경 시작: todoId={}, status={}", todoId, status);

        Todo todo = findTodoOrThrow(todoId);
        validateTodoOwnership(todo, userRowId);
        if (status == null) {
            // 본문 없는 옛 요청 — 종전 뜻(완료 ↔ 대기) 그대로.
            todo.toggleStatus();
        } else {
            todo.changeStatus(status);
        }
        // 별자리 게이미피케이션 — 완료 전이면 별빛 적립(당일 회수분은 복원), 해제면 당일 회수
        // (같은 트랜잭션). 실제 적립량을 응답에 실어 화면 "+N" 토스트가 거짓이 되지 않게 한다.
        int earnedStarlight = starlightService.onTodoStatusToggled(todo);

        log.info("할일 상태 변경 완료: todoId={}, newStatus={}, earnedStarlight={}",
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
        replaceMappings(todo, resolveOwnedTags(tagIds, userRowId));

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

    // ── 태그 다리 ────────────────────────────────────────────────────────────
    // 웹·앱 어느 쪽도 tagIds 를 안 보낸다 — 보내는 것은 category 문자열 하나다. 그래서
    // 매핑 테이블이 비고, 매핑으로 세는 usageCount 도 0 이 된다. 서버가 여기서 다리를 놓아
    // "category 로 활성 태그를 찾고, 없으면 만들어서" 매핑을 남긴다(QA #79).
    // 화면은 이미 그렇게 보고 있다 — 웹의 태그 선택지가 "서버 태그 ∪ 할 일에 쓰인 category" 다.

    /** 등록에서 남길 태그 — 명시한 tagIds 가 있으면 그쪽이 이기고, 없으면 category 로 잇는다. */
    private List<TodoTag> resolveTagsForWrite(List<Long> tagIds, String category, Long userRowId) {
        if (tagIds != null) {
            return resolveOwnedTags(tagIds, userRowId);
        }
        TodoTag bridged = resolveCategoryTag(userRowId, category);
        return bridged == null ? List.of() : List.of(bridged);
    }

    /**
     * 수정에서 category 가 바뀌면 매핑도 따라 옮긴다.
     *
     * <p>매핑 전체를 갈아엎지 않는 이유 — {@code PATCH /todo/{id}/tags} 로 여러 태그를 붙여 둔
     * 할 일이 있을 수 있고, category 하나만 보내는 요청이 그것들을 조용히 지우면 안 된다.
     * 그래서 <b>옛 이름의 태그만 떼고 새 이름의 태그를 붙인다</b>.
     */
    private void syncCategoryBridge(Todo todo, Long userRowId, String previousCategory, String newCategory) {
        String previous = blankToNull(previousCategory);
        String current = blankToNull(newCategory);
        if (previous == null && current == null) return;

        // 확보(새 트랜잭션)를 이 트랜잭션의 쓰기보다 먼저 끝낸다.
        TodoTag target = resolveCategoryTag(userRowId, current);

        if (previous != null && !previous.equals(current)) {
            todoTagRepository.findActiveByUserAndName(userRowId, previous)
                .ifPresent(stale -> todoTagMappingRepository
                    .deleteByTodoIdAndTagId(todo.getRowId(), stale.getRowId()));
        }
        if (target == null) return;

        boolean alreadyMapped = todoTagMappingRepository.findByTodoId(todo.getRowId()).stream()
            .anyMatch(m -> Objects.equals(m.getTag().getRowId(), target.getRowId()));
        if (!alreadyMapped) {
            todoTagMappingRepository.save(TodoTagMapping.create(todo, target));
        }
    }

    /** category 문자열 → 그 사용자의 활성 태그. 빈 category 는 태그를 만들지 않는다. */
    private TodoTag resolveCategoryTag(Long userRowId, String category) {
        String name = blankToNull(category);
        if (name == null) return null;
        Long tagRowId = todoTagService.findOrCreateByName(userRowId, name);
        return tagRowId == null ? null : todoTagRepository.findById(tagRowId).orElse(null);
    }

    /**
     * 요청이 지목한 태그를 <b>내 것인지 확인하고</b> 돌려준다.
     *
     * <p>종전엔 확인이 없어 남의 {@code tagId} 를 그대로 매핑했고, 그 태그의 이름·색이 내 할 일
     * 응답 {@code tags[]} 로 나갔다(QA #79 — 응답 유출). 캘린더가 같은 자리를 이렇게 막는다
     * ({@code CalendarEventServiceImpl.validateLabelOwnership}): 없으면 404, 남의 것이면 403.
     */
    private List<TodoTag> resolveOwnedTags(List<Long> tagIds, Long userRowId) {
        if (tagIds == null || tagIds.isEmpty()) return List.of();

        List<Long> ids = tagIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return List.of();

        List<TodoTag> tags = todoTagRepository.findAllByIds(ids);
        if (tags.size() != ids.size()) {
            log.warn("태그 조회 실패 - 존재하지 않거나 삭제된 태그: requested={}, found={}", ids.size(), tags.size());
            throw new EntityNotFoundException(DeskErrorCode.TODO_TAG_NOT_FOUND);
        }
        for (TodoTag tag : tags) {
            validateTagOwnership(tag, userRowId);
        }
        return tags;
    }

    private void replaceMappings(Todo todo, List<TodoTag> tags) {
        todoTagMappingRepository.deleteByTodoId(todo.getRowId());
        for (TodoTag tag : tags) {
            todoTagMappingRepository.save(TodoTagMapping.create(todo, tag));
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validateTagOwnership(TodoTag tag, Long userRowId) {
        Long ownerRowId = tag.getUser() != null ? tag.getUser().getRowId() : null;
        if (!userRowId.equals(ownerRowId)) {
            log.warn("태그 소유권 검증 실패 - tagId={}, ownerRowId={}, requestUserRowId={}",
                tag.getRowId(), ownerRowId, userRowId);
            throw new ForbiddenException(DeskErrorCode.TODO_ACCESS_DENIED);
        }
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
        // 태그 조회 (findByTodoId는 이미 fetchJoin 적용됨 · 삭제된 태그는 빠진다)
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
