package com.porest.desk.todo.service.dto;

import com.porest.desk.todo.domain.TodoProject;

import java.time.LocalDateTime;
import java.util.List;

public class TodoProjectServiceDto {

    public record CreateCommand(
        Long userRowId,
        String projectName,
        String description,
        String color,
        String icon
    ) {}

    public record UpdateCommand(
        String projectName,
        String description,
        String color,
        String icon
    ) {}

    public record ReorderCommand(
        List<ReorderItem> items
    ) {
        public record ReorderItem(
            Long projectId,
            int sortOrder
        ) {}
    }

    public record ProjectInfo(
        Long rowId,
        Long userRowId,
        String projectName,
        String description,
        String color,
        String icon,
        Integer sortOrder,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static ProjectInfo from(TodoProject project) {
            return new ProjectInfo(
                project.getRowId(),
                project.getUser().getRowId(),
                project.getProjectName(),
                project.getDescription(),
                project.getColor(),
                project.getIcon(),
                project.getSortOrder(),
                project.getCreateAt(),
                project.getModifyAt()
            );
        }
    }
}
