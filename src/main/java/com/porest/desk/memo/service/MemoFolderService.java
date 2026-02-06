package com.porest.desk.memo.service;

import com.porest.desk.memo.service.dto.MemoServiceDto;

import java.util.List;

public interface MemoFolderService {
    MemoServiceDto.FolderInfo createFolder(MemoServiceDto.FolderCreateCommand command);
    List<MemoServiceDto.FolderInfo> getFolders(Long userRowId);
    MemoServiceDto.FolderInfo updateFolder(Long folderId, MemoServiceDto.FolderUpdateCommand command);
    void deleteFolder(Long folderId);
}
