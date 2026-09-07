package com.porest.desk.memo.service.dto;

import com.porest.core.type.YNType;
import com.porest.desk.memo.domain.Memo;

import java.time.LocalDateTime;

public class MemoServiceDto {

    public record CreateCommand(
        Long userRowId,
        String title,
        String content,
        String tag,
        String color
    ) {}

    public record UpdateCommand(
        String title,
        String content,
        String tag,
        String color
    ) {}

    public record MemoInfo(
        Long rowId,
        Long userRowId,
        String title,
        String content,
        String tag,
        String color,
        YNType isPinned,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static MemoInfo from(Memo memo) {
            return new MemoInfo(
                memo.getRowId(),
                memo.getUser().getRowId(),
                memo.getTitle(),
                memo.getContent(),
                memo.getTag(),
                memo.getColor(),
                memo.getIsPinned(),
                memo.getCreateAt(),
                memo.getModifyAt()
            );
        }
    }
}
