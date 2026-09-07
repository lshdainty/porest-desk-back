package com.porest.desk.todo.repository;

import com.porest.desk.todo.domain.Todo;
import com.porest.desk.todo.type.TodoPriority;
import com.porest.desk.todo.type.TodoStatus;
import com.porest.desk.todo.type.TodoType;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface TodoRepository {
    Optional<Todo> findById(Long rowId);
    List<Todo> findAllByUser(Long userRowId, TodoStatus status, TodoPriority priority, String category, LocalDate startDate, LocalDate endDate, TodoType type);
    /**
     * 태그 개명에 맞춰 {@code todo.category} 를 따라 옮긴다 — 옛 이름을 쓰던 활성 할 일만.
     *
     * <p>{@code category} 는 태그 이름의 복사본이라 개명하면 옛 이름이 그대로 남는다.
     * 목록 필터·내보내기가 이 문자열을 쓰므로, 안 옮기면 개명한 태그로는 아무것도 안 걸린다(QA #79).
     *
     * @return 옮긴 행 수
     */
    long renameCategory(Long userRowId, String fromCategory, String toCategory);
    /**
     * 태그 삭제에 맞춰 그 이름을 쓰던 활성 할 일의 {@code category} 를 비운다.
     *
     * <p>{@code category} 는 태그 이름의 복사본이다. 태그만 지우면 이 문자열이 남아, 그 할 일을
     * 다음에 저장하는 순간 서버가 같은 이름의 태그를 <b>다시 만든다</b>(QA #88).
     * 삭제 확인창은 "이 태그를 쓰는 할 일 N건은 태그 없음으로 남아요" 라고 말하고 있으므로,
     * 화면의 약속대로 이름을 지워 둔다.
     *
     * @return 비운 행 수
     */
    long clearCategory(Long userRowId, String category);
    List<Todo> findByUserAndDueDateBetween(Long userRowId, LocalDate startDate, LocalDate endDate);
    List<Todo> findSubtasks(Long parentRowId);
    /** 여러 부모 ID에 대한 서브태스크 카운트를 한 번의 쿼리로 조회 (parentId -> [total, completed]) */
    Map<Long, int[]> findSubtaskCountsByParentIds(List<Long> parentIds);

    /**
     * 사용자의 할일 통계를 단일 집계 쿼리로 조회 (전체 엔티티 로드 대신 COUNT만)
     * 반환: [totalTask, pending, inProgress, completed, todayDue, overDue, noteCount, pinnedNoteCount]
     */
    long[] countStatsByUser(Long userRowId, LocalDate today);

    List<Todo> findDueTodosForReminder(LocalDate startDate, LocalDate endDate);
    Todo save(Todo todo);
    void delete(Todo todo);
}
