package com.porest.desk.memo.controller.dto;

import com.porest.core.type.YNType;
import com.porest.desk.memo.service.dto.MemoServiceDto;

import java.time.LocalDateTime;
import java.util.List;

public class MemoApiDto {

    public record CreateRequest(
        Long folderId,
        String title,
        String content
    ) {}

    public record UpdateRequest(
        Long folderId,
        String title,
        String content
    ) {}

    public record Response(
        Long rowId,
        Long userRowId,
        Long folderId,
        String title,
        String content,
        YNType isPinned,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static Response from(MemoServiceDto.MemoInfo info) {
            return new Response(
                info.rowId(),
                info.userRowId(),
                info.folderId(),
                info.title(),
                info.content(),
                info.isPinned(),
                info.createAt(),
                info.modifyAt()
            );
        }
    }

    public record ListResponse(
        List<Response> memos
    ) {
        public static ListResponse from(List<MemoServiceDto.MemoInfo> infos) {
            List<Response> responses = infos.stream()
                .map(Response::from)
                .toList();
            return new ListResponse(responses);
        }
    }

    public record FolderCreateRequest(
        Long parentId,
        String folderName
    ) {}

    public record FolderUpdateRequest(
        Long parentId,
        String folderName,
        Integer sortOrder
    ) {}

    public record FolderResponse(
        Long rowId,
        Long userRowId,
        Long parentId,
        String folderName,
        Integer sortOrder,
        LocalDateTime createAt,
        LocalDateTime modifyAt
    ) {
        public static FolderResponse from(MemoServiceDto.FolderInfo info) {
            return new FolderResponse(
                info.rowId(),
                info.userRowId(),
                info.parentId(),
                info.folderName(),
                info.sortOrder(),
                info.createAt(),
                info.modifyAt()
            );
        }
    }

    public record FolderListResponse(
        List<FolderResponse> folders
    ) {
        public static FolderListResponse from(List<MemoServiceDto.FolderInfo> infos) {
            List<FolderResponse> responses = infos.stream()
                .map(FolderResponse::from)
                .toList();
            return new FolderListResponse(responses);
        }
    }
}
