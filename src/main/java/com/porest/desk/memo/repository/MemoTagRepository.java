package com.porest.desk.memo.repository;

import com.porest.desk.memo.domain.MemoTag;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface MemoTagRepository {
    Optional<MemoTag> findById(Long rowId);
    List<MemoTag> findAllByUser(Long userRowId);
    boolean existsActiveByUserAndName(Long userRowId, String tagName, Long excludeRowId);
    /** 이름으로 활성 태그 하나 — {@code memo.tag} 문자열을 마스터로 잇는 자리(없으면 만든다). */
    Optional<MemoTag> findActiveByUserAndName(Long userRowId, String tagName);
    /**
     * 태그별 사용 메모 수 — <b>FK 기준</b> GROUP BY 1회 (tagRowId → 건수).
     *
     * <p>소유권 축은 <b>메모 주인</b>이다. 태그 주인으로 걸면 남의 메모가 섞인다 —
     * 할 일이 같은 자리에서 같은 이유로 할 일 주인을 축으로 삼는다(QA #79).
     */
    Map<Long, Long> countMemosByTag(Long userRowId);
    MemoTag save(MemoTag tag);
    /** 활성 이름 UNIQUE 위반을 서비스 안에서 잡기 위한 즉시 반영 — TodoTagRepository.flush() 와 같은 이유. */
    void flush();
    void delete(MemoTag tag);
}
