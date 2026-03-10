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
    List<Todo> findAllByUser(Long userRowId, TodoStatus status, TodoPriority priority, String category, LocalDate startDate, LocalDate endDate, Long projectRowId, TodoType type);
    List<Todo> findByUserAndDueDateBetween(Long userRowId, LocalDate startDate, LocalDate endDate);
    List<Todo> findSubtasks(Long parentRowId);
    /** 여러 부모 ID에 대한 서브태스크 카운트를 한 번의 쿼리로 조회 (parentId -> [total, completed]) */
    Map<Long, int[]> findSubtaskCountsByParentIds(List<Long> parentIds);
    List<Todo> findByProject(Long projectRowId);

    /**
     * 사용자의 할일 통계를 단일 집계 쿼리로 조회 (전체 엔티티 로드 대신 COUNT만)
     * 반환: [totalTask, pending, inProgress, completed, todayDue, overDue, noteCount, pinnedNoteCount]
     */
    long[] countStatsByUser(Long userRowId, LocalDate today);

    Todo save(Todo todo);
    void delete(Todo todo);
}
