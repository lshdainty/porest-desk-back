package com.porest.desk.memo.repository;

import com.porest.desk.memo.domain.Memo;

import java.util.List;
import java.util.Optional;

public interface MemoRepository {
    Optional<Memo> findById(Long rowId);
    List<Memo> findAllByUser(Long userRowId, String search);
    /**
     * 태그 개명에 맞춰 {@code memo.tag} 를 따라 옮긴다 — 그 사용자의 활성 메모만.
     *
     * <p>{@code tag} 는 태그 이름의 복사본이라 개명하면 옛 이름이 그대로 남는다. 웹·앱이 보내고
     * 받는 것이 이 문자열이므로, 안 옮기면 개명한 뒤 그 메모를 한 번 저장하는 순간 서버가
     * <b>옛 이름의 태그를 다시 만든다</b>(할 일이 겪은 QA #79 · #88 과 같은 자리).
     *
     * <p><b>FK 로 이어진 행과 이름만 같은 행을 함께</b> 옮긴다 — 백필 전에는 FK 가 비어 있고
     * 이름만 있는 옛 메모가 있다. 둘 중 하나만 보면 그 행이 개명에서 빠진다.
     *
     * @return 옮긴 행 수
     */
    long renameTag(Long userRowId, Long tagRowId, String fromTagName, String toTagName);
    /**
     * 태그 삭제에 맞춰 그 태그를 쓰던 활성 메모의 {@code tag} 를 비우고 FK 를 끊는다.
     *
     * <p>태그 행만 지우면 {@code memo.tag} 에 이름이 남아, 그 메모를 다음에 저장하는 순간
     * 서버가 같은 이름의 태그를 <b>다시 만든다</b>(QA #88 결론). 그래서 문자열과 FK 를
     * 함께 끊는다 — 한쪽만 끊으면 나머지 한쪽이 되살린다.
     *
     * <p>대상은 {@link #renameTag} 와 같은 이유로 <b>FK 로 이어진 행 ∪ 이름만 같은 행</b>이다.
     *
     * @return 비운 행 수
     */
    long clearTag(Long userRowId, Long tagRowId, String tagName);
    Memo save(Memo memo);
    void delete(Memo memo);
}
