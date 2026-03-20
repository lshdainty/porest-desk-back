package com.porest.desk.file.service;

import com.porest.core.exception.InvalidValueException;
import com.porest.desk.common.exception.DeskErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    /** 최대 파일 크기: 10MB */
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L;

    /** 허용 확장자 (소문자) */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        // 이미지
        ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".svg",
        // 문서
        ".pdf",
        // Office
        ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
        // 텍스트
        ".txt", ".csv"
    );

    private final Path rootLocation;

    public FileStorageService(@Value("${desk.file.upload-dir:./uploads}") String uploadDir) {
        this.rootLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("파일 업로드 디렉토리 생성 실패", e);
        }
    }

    public StoredFileInfo store(MultipartFile file) throws IOException {
        // 1. 파일 크기 검증
        if (file.getSize() > MAX_FILE_SIZE) {
            log.warn("파일 크기 초과: size={}, maxSize={}", file.getSize(), MAX_FILE_SIZE);
            throw new InvalidValueException(DeskErrorCode.FILE_TOO_LARGE);
        }

        String originalName = file.getOriginalFilename();

        // 2. 확장자 검증
        String extension = getExtension(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            log.warn("허용되지 않는 파일 확장자: extension={}, originalName={}", extension, originalName);
            throw new InvalidValueException(DeskErrorCode.FILE_INVALID_TYPE);
        }

        String storedName = UUID.randomUUID().toString() + extension;

        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        Path targetDir = rootLocation.resolve(datePath);
        Files.createDirectories(targetDir);

        Path targetPath = targetDir.resolve(storedName);
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        // 3. 저장 후 실제 MIME 타입 검사 (OS 기반, 클라이언트 제공값 신뢰 안 함)
        String detectedContentType = Files.probeContentType(targetPath);
        if (detectedContentType == null) {
            // probeContentType이 null이면 확장자 기반 타입 사용
            detectedContentType = resolveContentTypeByExtension(extension);
        }

        String relativePath = datePath + "/" + storedName;
        log.info("파일 저장 완료: originalName={}, storedName={}, path={}, contentType={}",
            originalName, storedName, relativePath, detectedContentType);

        return new StoredFileInfo(
            originalName,
            storedName,
            relativePath,
            detectedContentType,
            file.getSize()
        );
    }

    public Path load(String filePath) {
        return rootLocation.resolve(filePath).normalize();
    }

    public void delete(String filePath) throws IOException {
        Path path = rootLocation.resolve(filePath).normalize();
        Files.deleteIfExists(path);
        log.info("파일 삭제 완료: path={}", filePath);
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex >= 0 ? filename.substring(dotIndex) : "";
    }

    private String resolveContentTypeByExtension(String extension) {
        return switch (extension.toLowerCase()) {
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".png" -> "image/png";
            case ".gif" -> "image/gif";
            case ".webp" -> "image/webp";
            case ".bmp" -> "image/bmp";
            case ".svg" -> "image/svg+xml";
            case ".pdf" -> "application/pdf";
            case ".doc" -> "application/msword";
            case ".docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case ".xls" -> "application/vnd.ms-excel";
            case ".xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case ".ppt" -> "application/vnd.ms-powerpoint";
            case ".pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case ".txt" -> "text/plain";
            case ".csv" -> "text/csv";
            default -> "application/octet-stream";
        };
    }

    public record StoredFileInfo(
        String originalName,
        String storedName,
        String filePath,
        String contentType,
        Long fileSize
    ) {}
}
