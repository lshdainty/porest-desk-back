package com.porest.desk.file.repository;

import com.porest.desk.file.domain.FileAttachment;
import com.porest.desk.file.type.ReferenceType;

import java.util.List;
import java.util.Optional;

public interface FileAttachmentRepository {
    Optional<FileAttachment> findById(Long rowId);
    List<FileAttachment> findByReference(ReferenceType referenceType, Long referenceRowId);
    List<FileAttachment> findByUser(Long userRowId);
    FileAttachment save(FileAttachment entity);
}
