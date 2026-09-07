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
    /**
     * 그 태그의 매핑을 전부 걷는다 — 태그를 지우는 자리.
     *
     * <p>태그 삭제는 soft-delete 라 매핑은 남는다. 조회가 삭제된 태그를 걸러 주므로 화면에는
     * 안 보이지만, "태그 없음으로 남아요" 라고 말해 놓고 연결은 그대로 두는 셈이다(QA #88).
     */
    void deleteByTagId(Long tagRowId);
}
