package com.porest.desk.todo.service;

import com.porest.desk.todo.service.dto.TodoTagServiceDto;

import java.util.List;

public interface TodoTagService {
    TodoTagServiceDto.TagInfo createTag(TodoTagServiceDto.CreateCommand command);
    List<TodoTagServiceDto.TagInfo> getTags(Long userRowId);
    TodoTagServiceDto.TagInfo updateTag(Long tagId, TodoTagServiceDto.UpdateCommand command);
    void deleteTag(Long tagId);
}
