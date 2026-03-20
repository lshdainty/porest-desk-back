package com.porest.desk.memo.repository;

import com.porest.desk.memo.domain.MemoFolder;

import java.util.List;
import java.util.Optional;

public interface MemoFolderRepository {
    Optional<MemoFolder> findById(Long rowId);
    List<MemoFolder> findAllByUser(Long userRowId);
    MemoFolder save(MemoFolder folder);
    void delete(MemoFolder folder);
}
