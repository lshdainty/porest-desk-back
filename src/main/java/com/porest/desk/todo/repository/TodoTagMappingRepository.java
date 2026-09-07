package com.porest.desk.todo.repository;

import com.porest.desk.todo.domain.TodoTagMapping;

import java.util.List;

public interface TodoTagMappingRepository {
    /** 응답 {@code tags[]} 의 재료 — <b>삭제된 태그의 매핑은 돌려주지 않는다</b>. */
    List<TodoTagMapping> findByTodoId(Long todoRowId);
    /** {@link #findByTodoId} 의 배치판(N+1 금지). 삭제된 태그 제외 규칙은 같다. */
    List<TodoTagMapping> findByTodoIds(List<Long> todoRowIds);
    TodoTagMapping save(TodoTagMapping mapping);
    void deleteByTodoId(Long todoRowId);
    /** 매핑 하나만 걷는다 — category 가 바뀌었을 때 옛 이름의 태그를 떼는 자리. */
    void deleteByTodoIdAndTagId(Long todoRowId, Long tagRowId);
}
