package com.porest.desk.memo.service;

import com.porest.desk.memo.service.dto.MemoServiceDto;

import java.util.List;

public interface MemoService {
    MemoServiceDto.MemoInfo createMemo(MemoServiceDto.CreateCommand command);
    List<MemoServiceDto.MemoInfo> getMemos(Long userRowId, Long folderId, String search);
    MemoServiceDto.MemoInfo getMemo(Long memoId, Long userRowId);
    MemoServiceDto.MemoInfo updateMemo(Long memoId, Long userRowId, MemoServiceDto.UpdateCommand command);
    MemoServiceDto.MemoInfo togglePin(Long memoId, Long userRowId);
    void deleteMemo(Long memoId, Long userRowId);
}
