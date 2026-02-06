package com.porest.desk.memo.service.dto;

import com.porest.core.type.YNType;
import com.porest.desk.memo.domain.Memo;
import com.porest.desk.memo.domain.MemoFolder;

import java.time.LocalDateTime;

public class MemoServiceDto {

    public record CreateCommand(
        Long userRowId,
        Long folderId,
        String title,
        String content
    ) {}

    public record UpdateCommand(
        Long folderId,
        String title,
        String content
    ) {}

    public record MemoInfo(
        Long rowId,
        Long userRowId,
        Long folderId,
        String title,
        String content,
        YNType isPinned,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static MemoInfo from(Memo memo) {
            return new MemoInfo(
                memo.getRowId(),
                memo.getUser().getRowId(),
                memo.getFolder() != null ? memo.getFolder().getRowId() : null,
                memo.getTitle(),
                memo.getContent(),
                memo.getIsPinned(),
                memo.getCreateAt(),
                memo.getModifyAt()
            );
        }
    }

    public record FolderCreateCommand(
        Long userRowId,
        Long parentId,
        String folderName
    ) {}

    public record FolderUpdateCommand(
        Long parentId,
        String folderName,
        Integer sortOrder
    ) {}

    public record FolderInfo(
        Long rowId,
        Long userRowId,
        Long parentId,
        String folderName,
        Integer sortOrder,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static FolderInfo from(MemoFolder folder) {
            return new FolderInfo(
                folder.getRowId(),
                folder.getUser().getRowId(),
                folder.getParent() != null ? folder.getParent().getRowId() : null,
                folder.getFolderName(),
                folder.getSortOrder(),
                folder.getCreateAt(),
                folder.getModifyAt()
            );
        }
    }
}
