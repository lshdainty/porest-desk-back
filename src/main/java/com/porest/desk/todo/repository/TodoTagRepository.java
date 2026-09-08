package com.porest.desk.todo.repository;

import com.porest.desk.todo.domain.TodoTag;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface TodoTagRepository {
    Optional<TodoTag> findById(Long rowId);
    /**
     * FK 를 잇기 위한 <b>참조</b> — 행을 읽지 않는다(SELECT 가 나가지 않는다).
     *
     * <p>{@code findOrCreateByName} 이 <b>새 트랜잭션</b>으로 커밋한 태그를 {@link #findById} 로
     * 다시 읽으면 못 찾는다 — 부르는 트랜잭션은 그 커밋보다 앞서 스냅샷을 잡았고, MariaDB
     * 기본 격리수준(REPEATABLE READ)에서 그 스냅샷은 뒤에 커밋된 행을 안 보여 준다.
     * 그래서 처음 쓰는 이름은 마스터만 생기고 FK 가 비었다(QA #102). 프록시는 조회를 안 하고
     * 아이디만 들고 있으므로 스냅샷과 무관하고, 행은 이미 커밋돼 있어 FK 삽입은 통과한다.
     *
     * <p><b>이 프록시에서 필드를 읽지 마라.</b> 읽는 순간 지연 로딩 SELECT 가 나가 같은 스냅샷에
     * 부딪히고 {@code EntityNotFoundException} 이 된다 — 아이디 게터도 예외가 아니다(필드 접근
     * 매핑이라 프록시가 가로채지 못한다). 이름·색이 필요하면 {@code findOrCreateByName} 이
     * 함께 돌려주는 {@code TagRef} 를 써라.
     */
    TodoTag getReference(Long rowId);
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
