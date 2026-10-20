package io.groo.damda.file.repository;

import io.groo.damda.file.domain.FileAttachment;
import io.groo.damda.file.type.ReferenceType;

import java.util.List;
import java.util.Optional;

public interface FileAttachmentRepository {
    Optional<FileAttachment> findById(Long rowId);
    List<FileAttachment> findByReference(ReferenceType referenceType, Long referenceRowId);
    List<FileAttachment> findByUser(Long userRowId);
    FileAttachment save(FileAttachment entity);
}
