package com.porest.desk.file.service.dto;

import com.porest.desk.file.domain.FileAttachment;
import com.porest.desk.file.type.ReferenceType;

public class FileServiceDto {

    public record UploadCommand(
        Long userRowId,
        ReferenceType referenceType,
        Long referenceRowId
    ) {}

    public record FileInfo(
        Long rowId,
        Long userRowId,
        String originalName,
        String storedName,
        String filePath,
        String contentType,
        Long fileSize,
        String referenceType,
        Long referenceRowId,
        String createAt
    ) {
        public static FileInfo from(FileAttachment file) {
            return new FileInfo(
                file.getRowId(),
                file.getUser().getRowId(),
                file.getOriginalName(),
                file.getStoredName(),
                file.getFilePath(),
                file.getContentType(),
                file.getFileSize(),
                file.getReferenceType().name(),
                file.getReferenceRowId(),
                file.getCreateAt() != null ? file.getCreateAt().toString() : null
            );
        }
    }
}
