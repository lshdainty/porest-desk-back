package com.porest.desk.file.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.file.controller.dto.FileApiDto;
import com.porest.desk.file.service.FileAttachmentService;
import com.porest.desk.file.service.FileStorageService;
import com.porest.desk.file.service.dto.FileServiceDto;
import com.porest.desk.file.type.ReferenceType;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FileApiController {
    private final FileAttachmentService fileAttachmentService;
    private final FileStorageService fileStorageService;

    @PostMapping("/files/upload")
    public ApiResponse<FileApiDto.Response> uploadFile(
            @LoginUser UserPrincipal loginUser,
            @RequestParam("file") MultipartFile file,
            @RequestParam("referenceType") ReferenceType referenceType,
            @RequestParam(value = "referenceRowId", required = false) Long referenceRowId) {
        FileServiceDto.FileInfo info = fileAttachmentService.uploadFile(
            file, loginUser.getRowId(), referenceType, referenceRowId
        );
        return ApiResponse.success(FileApiDto.Response.from(info));
    }

    @GetMapping("/files/{id}")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long id) throws MalformedURLException {
        FileServiceDto.FileInfo info = fileAttachmentService.getFile(id);
        Path filePath = fileStorageService.load(info.filePath());
        Resource resource = new UrlResource(filePath.toUri());

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(info.contentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + info.originalName() + "\"")
            .body(resource);
    }

    @GetMapping("/files")
    public ApiResponse<FileApiDto.ListResponse> getFilesByReference(
            @RequestParam ReferenceType referenceType,
            @RequestParam Long referenceRowId) {
        List<FileServiceDto.FileInfo> infos = fileAttachmentService.getFilesByReference(referenceType, referenceRowId);
        return ApiResponse.success(FileApiDto.ListResponse.from(infos));
    }

    @DeleteMapping("/files/{id}")
    public ApiResponse<Void> deleteFile(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        fileAttachmentService.deleteFile(id);
        return ApiResponse.success();
    }
}
