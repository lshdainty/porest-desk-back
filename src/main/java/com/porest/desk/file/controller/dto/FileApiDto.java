package com.porest.desk.file.controller.dto;

import com.porest.desk.file.service.dto.FileServiceDto;

import java.util.List;

public class FileApiDto {

    public record Response(
        Long rowId,
        String originalName,
        String contentType,
        Long fileSize,
        String referenceType,
        Long referenceRowId,
        String createAt
    ) {
        public static Response from(FileServiceDto.FileInfo info) {
            return new Response(
                info.rowId(),
                info.originalName(),
                info.contentType(),
                info.fileSize(),
                info.referenceType(),
                info.referenceRowId(),
                info.createAt()
            );
        }
    }

    public record ListResponse(List<Response> files) {
        public static ListResponse from(List<FileServiceDto.FileInfo> infos) {
            return new ListResponse(
                infos.stream().map(Response::from).toList()
            );
        }
    }
}
