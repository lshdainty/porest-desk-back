package com.porest.desk.file.service;

import com.porest.desk.file.service.dto.FileServiceDto;
import com.porest.desk.file.type.ReferenceType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FileAttachmentService {
    FileServiceDto.FileInfo uploadFile(MultipartFile file, Long userRowId, ReferenceType referenceType, Long referenceRowId);
    FileServiceDto.FileInfo getFile(Long fileId);
    List<FileServiceDto.FileInfo> getFilesByReference(ReferenceType referenceType, Long referenceRowId);
    void deleteFile(Long fileId);
}
