package com.porest.desk.file.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.file.domain.FileAttachment;
import com.porest.desk.file.repository.FileAttachmentRepository;
import com.porest.desk.file.service.dto.FileServiceDto;
import com.porest.desk.file.type.ReferenceType;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class FileAttachmentServiceImpl implements FileAttachmentService {
    private final FileAttachmentRepository fileAttachmentRepository;
    private final FileStorageService fileStorageService;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public FileServiceDto.FileInfo uploadFile(MultipartFile file, Long userRowId, ReferenceType referenceType, Long referenceRowId) {
        log.debug("파일 업로드 시작: userRowId={}, referenceType={}", userRowId, referenceType);

        User user = userRepository.findById(userRowId)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

        FileStorageService.StoredFileInfo stored;
        try {
            stored = fileStorageService.store(file);
        } catch (IOException e) {
            throw new RuntimeException("파일 저장 실패", e);
        }

        FileAttachment attachment = FileAttachment.create(
            user,
            stored.originalName(),
            stored.storedName(),
            stored.filePath(),
            stored.contentType(),
            stored.fileSize(),
            referenceType,
            referenceRowId
        );

        fileAttachmentRepository.save(attachment);
        log.info("파일 업로드 완료: fileId={}, originalName={}", attachment.getRowId(), stored.originalName());

        return FileServiceDto.FileInfo.from(attachment);
    }

    @Override
    public FileServiceDto.FileInfo getFile(Long fileId, Long userRowId) {
        FileAttachment attachment = fileAttachmentRepository.findById(fileId)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.FILE_NOT_FOUND));
        validateFileOwnership(attachment, userRowId);
        return FileServiceDto.FileInfo.from(attachment);
    }

    @Override
    public List<FileServiceDto.FileInfo> getFilesByReference(ReferenceType referenceType, Long referenceRowId) {
        return fileAttachmentRepository.findByReference(referenceType, referenceRowId).stream()
            .map(FileServiceDto.FileInfo::from)
            .toList();
    }

    @Override
    @Transactional
    public void deleteFile(Long fileId, Long userRowId) {
        log.debug("파일 삭제 시작: fileId={}", fileId);

        FileAttachment attachment = fileAttachmentRepository.findById(fileId)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.FILE_NOT_FOUND));
        validateFileOwnership(attachment, userRowId);

        attachment.deleteFile();

        try {
            fileStorageService.delete(attachment.getFilePath());
        } catch (IOException e) {
            log.warn("물리 파일 삭제 실패: path={}", attachment.getFilePath(), e);
        }

        log.info("파일 삭제 완료: fileId={}", fileId);
    }

    private void validateFileOwnership(FileAttachment attachment, Long userRowId) {
        if (!attachment.getUser().getRowId().equals(userRowId)) {
            log.warn("파일 소유권 검증 실패 - fileId={}, ownerRowId={}, requestUserRowId={}",
                attachment.getRowId(), attachment.getUser().getRowId(), userRowId);
            throw new ForbiddenException(DeskErrorCode.FILE_ACCESS_DENIED);
        }
    }
}
