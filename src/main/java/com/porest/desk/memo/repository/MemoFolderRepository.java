package com.porest.desk.memo.repository;

import com.porest.desk.memo.domain.MemoFolder;

import java.util.List;
import java.util.Optional;

public interface MemoFolderRepository {
    Optional<MemoFolder> findById(Long rowId);
    List<MemoFolder> findAllByUser(Long userRowId);
    boolean existsActiveByUserAndParentAndName(Long userRowId, Long parentRowId, String folderName, Long excludeRowId);
    MemoFolder save(MemoFolder folder);
    /** 활성 이름 UNIQUE 위반을 서비스 안에서 잡기 위한 즉시 반영. */
    void flush();
    void delete(MemoFolder folder);
}
