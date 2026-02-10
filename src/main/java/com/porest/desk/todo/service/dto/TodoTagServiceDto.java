package com.porest.desk.todo.service.dto;

import com.porest.desk.todo.domain.TodoTag;

import java.time.LocalDateTime;

public class TodoTagServiceDto {

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
        LocalDateTime modifyAt
    ) {
        public static TagInfo from(TodoTag tag) {
            return new TagInfo(
                tag.getRowId(),
                tag.getUser().getRowId(),
                tag.getTagName(),
                tag.getColor(),
                tag.getCreateAt(),
                tag.getModifyAt()
            );
        }
    }
}
