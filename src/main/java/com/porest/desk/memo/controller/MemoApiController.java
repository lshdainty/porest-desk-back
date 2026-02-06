package com.porest.desk.memo.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.memo.controller.dto.MemoApiDto;
import com.porest.desk.memo.service.MemoService;
import com.porest.desk.memo.service.dto.MemoServiceDto;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MemoApiController {
    private final MemoService memoService;

    @PostMapping("/memo")
    public ApiResponse<MemoApiDto.Response> createMemo(
            @LoginUser UserPrincipal loginUser,
            @RequestBody MemoApiDto.CreateRequest request) {
        MemoServiceDto.MemoInfo info = memoService.createMemo(new MemoServiceDto.CreateCommand(
            loginUser.getRowId(),
            request.folderId(),
            request.title(),
            request.content()
        ));
        return ApiResponse.success(MemoApiDto.Response.from(info));
    }

    @GetMapping("/memos")
    public ApiResponse<MemoApiDto.ListResponse> getMemos(
            @LoginUser UserPrincipal loginUser,
            @RequestParam(required = false) Long folderId,
            @RequestParam(required = false) String search) {
        List<MemoServiceDto.MemoInfo> infos = memoService.getMemos(
            loginUser.getRowId(), folderId, search
        );
        return ApiResponse.success(MemoApiDto.ListResponse.from(infos));
    }

    @GetMapping("/memo/{id}")
    public ApiResponse<MemoApiDto.Response> getMemo(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        MemoServiceDto.MemoInfo info = memoService.getMemo(id);
        return ApiResponse.success(MemoApiDto.Response.from(info));
    }

    @PutMapping("/memo/{id}")
    public ApiResponse<MemoApiDto.Response> updateMemo(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id,
            @RequestBody MemoApiDto.UpdateRequest request) {
        MemoServiceDto.MemoInfo info = memoService.updateMemo(id, new MemoServiceDto.UpdateCommand(
            request.folderId(),
            request.title(),
            request.content()
        ));
        return ApiResponse.success(MemoApiDto.Response.from(info));
    }

    @PatchMapping("/memo/{id}/pin")
    public ApiResponse<MemoApiDto.Response> togglePin(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        MemoServiceDto.MemoInfo info = memoService.togglePin(id);
        return ApiResponse.success(MemoApiDto.Response.from(info));
    }

    @DeleteMapping("/memo/{id}")
    public ApiResponse<Void> deleteMemo(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        memoService.deleteMemo(id);
        return ApiResponse.success();
    }
}
