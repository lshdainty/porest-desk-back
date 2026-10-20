package io.groo.damda.file.service;

import io.groo.damda.file.service.dto.FileServiceDto;
import io.groo.damda.file.type.ReferenceType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FileAttachmentService {
    FileServiceDto.FileInfo uploadFile(MultipartFile file, Long userRowId, ReferenceType referenceType, Long referenceRowId);
    FileServiceDto.FileInfo getFile(Long fileId, Long userRowId);
    List<FileServiceDto.FileInfo> getFilesByReference(ReferenceType referenceType, Long referenceRowId, Long userRowId);
    void deleteFile(Long fileId, Long userRowId);
}
