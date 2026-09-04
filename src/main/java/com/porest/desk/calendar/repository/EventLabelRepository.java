package com.porest.desk.calendar.repository;

import com.porest.desk.calendar.domain.EventLabel;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface EventLabelRepository {
    Optional<EventLabel> findById(Long rowId);
    List<EventLabel> findAllByUser(Long userRowId);
    /** 라벨별 사용 중 일정 수 — labelRowId → count (GROUP BY 1회, 삭제 일정 제외). */
    Map<Long, Long> countEventsByLabel(Long userRowId);
    boolean existsActiveByUserAndName(Long userRowId, String labelName, Long excludeRowId);
    EventLabel save(EventLabel eventLabel);
    /**
     * 지금까지의 변경을 INSERT/UPDATE 로 즉시 내보낸다 — 활성 이름 UNIQUE 위반을
     * <b>서비스 메서드 안에서</b> 잡기 위한 것이다. 명시하지 않으면 위반이 커밋 시점
     * (트랜잭션 인터셉터 안, 서비스 반환 뒤)에 터져 try/catch 가 닿지 않는다.
     */
    void flush();
    void delete(EventLabel eventLabel);
}
