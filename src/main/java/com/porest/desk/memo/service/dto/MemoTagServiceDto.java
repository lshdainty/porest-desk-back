package com.porest.desk.memo.service.dto;

import com.porest.desk.memo.domain.MemoTag;

import java.time.LocalDateTime;

public class MemoTagServiceDto {

    public record CreateCommand(
        Long userRowId,
        String tagName,
        String color
    ) {}

    public record UpdateCommand(
        String tagName,
        String color
    ) {}

    public record TagInfo(
        Long rowId,
        Long userRowId,
        String tagName,
        String color,
        LocalDateTime createAt,
        LocalDateTime modifyAt,
        long usageCount
    ) {
        public static TagInfo from(MemoTag tag) {
            return from(tag, 0L);
        }

        public static TagInfo from(MemoTag tag, long usageCount) {
            return new TagInfo(
                tag.getRowId(),
                tag.getUser().getRowId(),
                tag.getTagName(),
                tag.getColor(),
                tag.getCreateAt(),
                tag.getModifyAt(),
                usageCount
            );
        }
    }
}
