package com.porest.desk.todo.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.UserPrincipal;
import com.porest.desk.todo.controller.dto.TodoTagApiDto;
import com.porest.desk.todo.service.TodoTagService;
import com.porest.desk.todo.service.dto.TodoTagServiceDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
public class TodoTagApiController {
    private final TodoTagService todoTagService;

    @PostMapping("/todo-tag")
    public ApiResponse<TodoTagApiDto.Response> createTag(
            @LoginUser UserPrincipal loginUser,
            @RequestBody TodoTagApiDto.CreateRequest request) {
        TodoTagServiceDto.TagInfo info = todoTagService.createTag(
            new TodoTagServiceDto.CreateCommand(
                loginUser.getRowId(),
                request.tagName(),
                request.color()
            )
        );
        return ApiResponse.success(TodoTagApiDto.Response.from(info));
    }

    @GetMapping("/todo-tags")
    public ApiResponse<TodoTagApiDto.ListResponse> getTags(
            @LoginUser UserPrincipal loginUser) {
        List<TodoTagServiceDto.TagInfo> infos = todoTagService.getTags(loginUser.getRowId());
        return ApiResponse.success(TodoTagApiDto.ListResponse.from(infos));
    }

    @PutMapping("/todo-tag/{id}")
    public ApiResponse<TodoTagApiDto.Response> updateTag(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id,
            @RequestBody TodoTagApiDto.UpdateRequest request) {
        TodoTagServiceDto.TagInfo info = todoTagService.updateTag(id,
            new TodoTagServiceDto.UpdateCommand(request.tagName(), request.color())
        );
        return ApiResponse.success(TodoTagApiDto.Response.from(info));
    }

    @DeleteMapping("/todo-tag/{id}")
    public ApiResponse<Void> deleteTag(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        todoTagService.deleteTag(id);
        return ApiResponse.success();
    }
}
