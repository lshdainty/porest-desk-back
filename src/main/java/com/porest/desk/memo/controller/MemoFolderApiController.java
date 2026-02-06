package com.porest.desk.memo.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.memo.controller.dto.MemoApiDto;
import com.porest.desk.memo.service.MemoFolderService;
import com.porest.desk.memo.service.dto.MemoServiceDto;
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
public class MemoFolderApiController {
    private final MemoFolderService memoFolderService;

    @PostMapping("/memo/folder")
    public ApiResponse<MemoApiDto.FolderResponse> createFolder(
            @LoginUser UserPrincipal loginUser,
            @RequestBody MemoApiDto.FolderCreateRequest request) {
        MemoServiceDto.FolderInfo info = memoFolderService.createFolder(new MemoServiceDto.FolderCreateCommand(
            loginUser.getRowId(),
            request.parentId(),
            request.folderName()
        ));
        return ApiResponse.success(MemoApiDto.FolderResponse.from(info));
    }

    @GetMapping("/memo/folders")
    public ApiResponse<MemoApiDto.FolderListResponse> getFolders(
            @LoginUser UserPrincipal loginUser) {
        List<MemoServiceDto.FolderInfo> infos = memoFolderService.getFolders(loginUser.getRowId());
        return ApiResponse.success(MemoApiDto.FolderListResponse.from(infos));
    }

    @PutMapping("/memo/folder/{id}")
    public ApiResponse<MemoApiDto.FolderResponse> updateFolder(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id,
            @RequestBody MemoApiDto.FolderUpdateRequest request) {
        MemoServiceDto.FolderInfo info = memoFolderService.updateFolder(id, new MemoServiceDto.FolderUpdateCommand(
            request.parentId(),
            request.folderName(),
            request.sortOrder()
        ));
        return ApiResponse.success(MemoApiDto.FolderResponse.from(info));
    }

    @DeleteMapping("/memo/folder/{id}")
    public ApiResponse<Void> deleteFolder(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        memoFolderService.deleteFolder(id);
        return ApiResponse.success();
    }
}
