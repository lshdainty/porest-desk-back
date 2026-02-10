package com.porest.desk.file.service;

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
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

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
        String originalName = file.getOriginalFilename();
        String extension = getExtension(originalName);
        String storedName = UUID.randomUUID().toString() + extension;

        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        Path targetDir = rootLocation.resolve(datePath);
        Files.createDirectories(targetDir);

        Path targetPath = targetDir.resolve(storedName);
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        String relativePath = datePath + "/" + storedName;
        log.info("파일 저장 완료: originalName={}, storedName={}, path={}", originalName, storedName, relativePath);

        return new StoredFileInfo(
            originalName,
            storedName,
            relativePath,
            file.getContentType(),
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

    public record StoredFileInfo(
        String originalName,
        String storedName,
        String filePath,
        String contentType,
        Long fileSize
    ) {}
}
