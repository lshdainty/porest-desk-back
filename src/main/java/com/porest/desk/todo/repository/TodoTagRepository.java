package com.porest.desk.todo.repository;

import com.porest.desk.todo.domain.TodoTag;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface TodoTagRepository {
    Optional<TodoTag> findById(Long rowId);
    List<TodoTag> findAllByUser(Long userRowId);
    boolean existsActiveByUserAndName(Long userRowId, String tagName, Long excludeRowId);
    /** 이름으로 활성 태그 하나 — category 문자열을 태그로 잇는 자리(없으면 만든다). */
    Optional<TodoTag> findActiveByUserAndName(Long userRowId, String tagName);
    /**
     * <b>소유권은 보지 않는다</b> — 부르는 서비스가 {@code validateTagOwnership} 으로 건다
     * (캘린더 라벨과 같은 모양: 리포는 존재·활성만, 소유권은 서비스). 남의 tagId 를 그대로
     * 매핑하면 남의 태그 이름·색이 내 응답에 실린다(QA #79).
     */
    List<TodoTag> findAllByIds(List<Long> ids);
    /**
     * 태그별 사용 할일 수 — <b>매핑 기준</b> GROUP BY 1회 (tagRowId → 건수).
     * 소유권 축은 <b>할 일 주인</b>이다(태그 주인으로 걸면 남의 할 일이 섞인다).
     */
    Map<Long, Long> countTodosByTag(Long userRowId);
    TodoTag save(TodoTag tag);
    /** 활성 이름 UNIQUE 위반을 서비스 안에서 잡기 위한 즉시 반영 — EventLabelRepository.flush() 와 같은 이유. */
    void flush();
    void delete(TodoTag tag);
}
