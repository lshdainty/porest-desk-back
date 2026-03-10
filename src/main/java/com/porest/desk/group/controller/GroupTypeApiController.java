package com.porest.desk.group.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.group.controller.dto.GroupTypeApiDto;
import com.porest.desk.group.service.GroupTypeService;
import com.porest.desk.group.service.dto.GroupTypeServiceDto;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.UserPrincipal;
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
public class GroupTypeApiController {
    private final GroupTypeService groupTypeService;

    @PostMapping("/group-type")
    public ApiResponse<GroupTypeApiDto.Response> createGroupType(
            @LoginUser UserPrincipal loginUser,
            @RequestBody GroupTypeApiDto.CreateRequest request) {
        GroupTypeServiceDto.GroupTypeInfo info = groupTypeService.createGroupType(new GroupTypeServiceDto.CreateCommand(
            loginUser.getRowId(),
            request.typeName(),
            request.color(),
            request.sortOrder() != null ? request.sortOrder() : 0
        ));
        return ApiResponse.success(GroupTypeApiDto.Response.from(info));
    }

    @GetMapping("/group-types")
    public ApiResponse<GroupTypeApiDto.ListResponse> getGroupTypes(
            @LoginUser UserPrincipal loginUser) {
        List<GroupTypeServiceDto.GroupTypeInfo> infos = groupTypeService.getGroupTypes(loginUser.getRowId());
        return ApiResponse.success(GroupTypeApiDto.ListResponse.from(infos));
    }

    @PutMapping("/group-type/{id}")
    public ApiResponse<GroupTypeApiDto.Response> updateGroupType(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id,
            @RequestBody GroupTypeApiDto.UpdateRequest request) {
        GroupTypeServiceDto.GroupTypeInfo info = groupTypeService.updateGroupType(new GroupTypeServiceDto.UpdateCommand(
            id,
            loginUser.getRowId(),
            request.typeName(),
            request.color(),
            request.sortOrder() != null ? request.sortOrder() : 0
        ));
        return ApiResponse.success(GroupTypeApiDto.Response.from(info));
    }

    @DeleteMapping("/group-type/{id}")
    public ApiResponse<Void> deleteGroupType(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        groupTypeService.deleteGroupType(id, loginUser.getRowId());
        return ApiResponse.success();
    }
}
