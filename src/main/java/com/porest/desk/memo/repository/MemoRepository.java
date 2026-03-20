package com.porest.desk.memo.repository;

import com.porest.desk.memo.domain.Memo;

import java.util.List;
import java.util.Optional;

public interface MemoRepository {
    Optional<Memo> findById(Long rowId);
    List<Memo> findAllByUser(Long userRowId, Long folderId, String search);
    Memo save(Memo memo);
    void delete(Memo memo);
}
