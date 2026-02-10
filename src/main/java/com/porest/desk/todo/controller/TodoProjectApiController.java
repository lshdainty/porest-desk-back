package com.porest.desk.todo.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.UserPrincipal;
import com.porest.desk.todo.controller.dto.TodoProjectApiDto;
import com.porest.desk.todo.service.TodoProjectService;
import com.porest.desk.todo.service.dto.TodoProjectServiceDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TodoProjectApiController {
    private final TodoProjectService todoProjectService;

    @PostMapping("/todo-project")
    public ApiResponse<TodoProjectApiDto.Response> createProject(
            @LoginUser UserPrincipal loginUser,
            @RequestBody TodoProjectApiDto.CreateRequest request) {
        TodoProjectServiceDto.ProjectInfo info = todoProjectService.createProject(
            new TodoProjectServiceDto.CreateCommand(
                loginUser.getRowId(),
                request.projectName(),
                request.description(),
                request.color(),
                request.icon()
            )
        );
        return ApiResponse.success(TodoProjectApiDto.Response.from(info));
    }

    @GetMapping("/todo-projects")
    public ApiResponse<TodoProjectApiDto.ListResponse> getProjects(
            @LoginUser UserPrincipal loginUser) {
        List<TodoProjectServiceDto.ProjectInfo> infos = todoProjectService.getProjects(loginUser.getRowId());
        return ApiResponse.success(TodoProjectApiDto.ListResponse.from(infos));
    }

    @PutMapping("/todo-project/{id}")
    public ApiResponse<TodoProjectApiDto.Response> updateProject(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id,
            @RequestBody TodoProjectApiDto.UpdateRequest request) {
        TodoProjectServiceDto.ProjectInfo info = todoProjectService.updateProject(id,
            new TodoProjectServiceDto.UpdateCommand(
                request.projectName(),
                request.description(),
                request.color(),
                request.icon()
            )
        );
        return ApiResponse.success(TodoProjectApiDto.Response.from(info));
    }

    @PatchMapping("/todo-projects/reorder")
    public ApiResponse<Void> reorderProjects(
            @LoginUser UserPrincipal loginUser,
            @RequestBody TodoProjectApiDto.ReorderRequest request) {
        List<TodoProjectServiceDto.ReorderCommand.ReorderItem> items = request.items().stream()
            .map(item -> new TodoProjectServiceDto.ReorderCommand.ReorderItem(item.projectId(), item.sortOrder()))
            .toList();
        todoProjectService.reorderProjects(loginUser.getRowId(), new TodoProjectServiceDto.ReorderCommand(items));
        return ApiResponse.success();
    }

    @DeleteMapping("/todo-project/{id}")
    public ApiResponse<Void> deleteProject(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        todoProjectService.deleteProject(id);
        return ApiResponse.success();
    }
}
