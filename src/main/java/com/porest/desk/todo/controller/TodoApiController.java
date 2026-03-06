package com.porest.desk.todo.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.UserPrincipal;
import com.porest.desk.todo.controller.dto.TodoApiDto;
import com.porest.desk.todo.service.TodoService;
import com.porest.desk.todo.service.dto.TodoServiceDto;
import com.porest.desk.todo.type.TodoPriority;
import com.porest.desk.todo.type.TodoStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TodoApiController {
    private final TodoService todoService;

    @PostMapping("/todo")
    public ApiResponse<TodoApiDto.Response> createTodo(
            @LoginUser UserPrincipal loginUser,
            @RequestBody TodoApiDto.CreateRequest request) {
        TodoServiceDto.TodoInfo info = todoService.createTodo(new TodoServiceDto.CreateCommand(
            loginUser.getRowId(),
            request.title(),
            request.content(),
            request.priority(),
            request.category(),
            request.dueDate(),
            request.projectRowId(),
            request.parentRowId(),
            request.tagIds()
        ));
        return ApiResponse.success(TodoApiDto.Response.from(info));
    }

    @GetMapping("/todos")
    public ApiResponse<TodoApiDto.ListResponse> getTodos(
            @LoginUser UserPrincipal loginUser,
            @RequestParam(required = false) TodoStatus status,
            @RequestParam(required = false) TodoPriority priority,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long projectRowId) {
        List<TodoServiceDto.TodoInfo> infos = todoService.getTodos(
            loginUser.getRowId(), status, priority, category, startDate, endDate, projectRowId
        );
        return ApiResponse.success(TodoApiDto.ListResponse.from(infos));
    }

    @GetMapping("/todo/{id}")
    public ApiResponse<TodoApiDto.Response> getTodo(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        TodoServiceDto.TodoInfo info = todoService.getTodo(id, loginUser.getRowId());
        return ApiResponse.success(TodoApiDto.Response.from(info));
    }

    @PutMapping("/todo/{id}")
    public ApiResponse<TodoApiDto.Response> updateTodo(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id,
            @RequestBody TodoApiDto.UpdateRequest request) {
        TodoServiceDto.TodoInfo info = todoService.updateTodo(id, loginUser.getRowId(), new TodoServiceDto.UpdateCommand(
            request.title(),
            request.content(),
            request.priority(),
            request.category(),
            request.dueDate(),
            request.projectRowId(),
            request.tagIds()
        ));
        return ApiResponse.success(TodoApiDto.Response.from(info));
    }

    @PatchMapping("/todo/{id}/status")
    public ApiResponse<TodoApiDto.Response> toggleStatus(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        TodoServiceDto.TodoInfo info = todoService.toggleStatus(id, loginUser.getRowId());
        return ApiResponse.success(TodoApiDto.Response.from(info));
    }

    @PatchMapping("/todos/reorder")
    public ApiResponse<Void> reorderTodos(
            @LoginUser UserPrincipal loginUser,
            @RequestBody TodoApiDto.ReorderRequest request) {
        List<TodoServiceDto.ReorderCommand.ReorderItem> items = request.items().stream()
            .map(item -> new TodoServiceDto.ReorderCommand.ReorderItem(item.todoId(), item.sortOrder()))
            .toList();
        todoService.reorderTodos(loginUser.getRowId(), new TodoServiceDto.ReorderCommand(items));
        return ApiResponse.success();
    }

    @DeleteMapping("/todo/{id}")
    public ApiResponse<Void> deleteTodo(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        todoService.deleteTodo(id, loginUser.getRowId());
        return ApiResponse.success();
    }

    @GetMapping("/todo/{id}/subtasks")
    public ApiResponse<TodoApiDto.ListResponse> getSubtasks(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        List<TodoServiceDto.TodoInfo> infos = todoService.getSubtasks(id, loginUser.getRowId());
        return ApiResponse.success(TodoApiDto.ListResponse.from(infos));
    }

    @PatchMapping("/todo/{id}/tags")
    public ApiResponse<Void> updateTags(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id,
            @RequestBody TodoApiDto.TagUpdateRequest request) {
        todoService.updateTags(id, loginUser.getRowId(), request.tagIds());
        return ApiResponse.success();
    }

    @GetMapping("/todos/stats")
    public ApiResponse<TodoApiDto.StatsResponse> getStats(
            @LoginUser UserPrincipal loginUser) {
        TodoServiceDto.TodoStats stats = todoService.getStats(loginUser.getRowId());
        return ApiResponse.success(TodoApiDto.StatsResponse.from(stats));
    }
}
