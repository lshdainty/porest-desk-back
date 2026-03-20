package com.porest.desk.group.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.group.controller.dto.UserGroupApiDto;
import com.porest.desk.group.service.UserGroupService;
import com.porest.desk.group.service.dto.UserGroupServiceDto;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.UserPrincipal;
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
public class UserGroupApiController {
    private final UserGroupService userGroupService;

    @PostMapping("/group")
    public ApiResponse<UserGroupApiDto.Response> createGroup(
            @LoginUser UserPrincipal loginUser,
            @RequestBody UserGroupApiDto.CreateRequest request) {
        UserGroupServiceDto.CreateCommand command = new UserGroupServiceDto.CreateCommand(
            loginUser.getRowId(),
            request.groupName(),
            request.description(),
            request.groupTypeId()
        );
        UserGroupServiceDto.GroupInfo info = userGroupService.createGroup(command);
        return ApiResponse.success(UserGroupApiDto.Response.from(info));
    }

    @GetMapping("/groups")
    public ApiResponse<UserGroupApiDto.ListResponse> getGroups(
            @LoginUser UserPrincipal loginUser) {
        List<UserGroupServiceDto.GroupInfo> infos = userGroupService.getGroups(loginUser.getRowId());
        return ApiResponse.success(UserGroupApiDto.ListResponse.from(infos));
    }

    @GetMapping("/group/{id}")
    public ApiResponse<UserGroupApiDto.DetailResponse> getGroup(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        UserGroupServiceDto.GroupDetailInfo info = userGroupService.getGroup(id);
        return ApiResponse.success(UserGroupApiDto.DetailResponse.from(info));
    }

    @PutMapping("/group/{id}")
    public ApiResponse<UserGroupApiDto.Response> updateGroup(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id,
            @RequestBody UserGroupApiDto.UpdateRequest request) {
        UserGroupServiceDto.UpdateCommand command = new UserGroupServiceDto.UpdateCommand(
            id,
            request.groupName(),
            request.description(),
            request.groupTypeId()
        );
        UserGroupServiceDto.GroupInfo info = userGroupService.updateGroup(command);
        return ApiResponse.success(UserGroupApiDto.Response.from(info));
    }

    @DeleteMapping("/group/{id}")
    public ApiResponse<Void> deleteGroup(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        userGroupService.deleteGroup(id);
        return ApiResponse.success();
    }

    @PatchMapping("/group/{id}/regenerate-invite-code")
    public ApiResponse<UserGroupApiDto.InviteCodeResponse> regenerateInviteCode(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        String newCode = userGroupService.regenerateInviteCode(id);
        return ApiResponse.success(new UserGroupApiDto.InviteCodeResponse(newCode));
    }

    @PostMapping("/group/join")
    public ApiResponse<UserGroupApiDto.DetailResponse> joinGroup(
            @LoginUser UserPrincipal loginUser,
            @RequestBody UserGroupApiDto.JoinRequest request) {
        UserGroupServiceDto.GroupDetailInfo info =
            userGroupService.joinByInviteCode(loginUser.getRowId(), request.inviteCode());
        return ApiResponse.success(UserGroupApiDto.DetailResponse.from(info));
    }

    @DeleteMapping("/group/{groupId}/member/{memberId}")
    public ApiResponse<Void> removeMember(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long groupId,
            @PathVariable Long memberId) {
        userGroupService.removeMember(groupId, memberId);
        return ApiResponse.success();
    }

    @PatchMapping("/group/{groupId}/member/{memberId}/role")
    public ApiResponse<Void> changeMemberRole(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long groupId,
            @PathVariable Long memberId,
            @RequestBody UserGroupApiDto.ChangeRoleRequest request) {
        userGroupService.changeMemberRole(groupId, memberId, request.role());
        return ApiResponse.success();
    }
}
