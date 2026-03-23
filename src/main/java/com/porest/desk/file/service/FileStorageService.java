package com.porest.desk.file.service;

import com.porest.core.util.FileUploadValidator;
import com.porest.core.util.FileUtils;
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

    /** 허용 확장자 (점 없이, 소문자) */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        // 이미지
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg",
        // 문서
        "pdf",
        // Office
        "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        // 텍스트
        "txt", "csv"
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
        // 1. 파일 크기 + 확장자 통합 검증 (core 공통 유틸 사용)
        FileUploadValidator.validate(file, ALLOWED_EXTENSIONS, MAX_FILE_SIZE);

        String originalName = file.getOriginalFilename();
        String extension = "." + FileUtils.getExtension(originalName).toLowerCase();

        String storedName = UUID.randomUUID().toString() + extension;

        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        Path targetDir = rootLocation.resolve(datePath);
        Files.createDirectories(targetDir);

        Path targetPath = targetDir.resolve(storedName);
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        // 3. 저장 후 실제 MIME 타입 검사 (core 공통 유틸 사용)
        String detectedContentType = FileUploadValidator.detectMimeType(targetPath);

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

    public record StoredFileInfo(
        String originalName,
        String storedName,
        String filePath,
        String contentType,
        Long fileSize
    ) {}
}
