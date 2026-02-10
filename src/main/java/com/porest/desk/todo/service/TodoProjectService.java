package com.porest.desk.todo.service;

import com.porest.desk.todo.service.dto.TodoProjectServiceDto;

import java.util.List;

public interface TodoProjectService {
    TodoProjectServiceDto.ProjectInfo createProject(TodoProjectServiceDto.CreateCommand command);
    List<TodoProjectServiceDto.ProjectInfo> getProjects(Long userRowId);
    TodoProjectServiceDto.ProjectInfo updateProject(Long projectId, TodoProjectServiceDto.UpdateCommand command);
    void reorderProjects(Long userRowId, TodoProjectServiceDto.ReorderCommand command);
    void deleteProject(Long projectId);
}
