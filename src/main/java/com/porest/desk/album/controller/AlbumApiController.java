package com.porest.desk.album.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.album.controller.dto.AlbumApiDto;
import com.porest.desk.album.service.AlbumService;
import com.porest.desk.album.service.dto.AlbumServiceDto;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AlbumApiController {
    private final AlbumService albumService;

    @PostMapping("/album")
    public ApiResponse<AlbumApiDto.Response> createAlbum(
            @LoginUser UserPrincipal loginUser,
            @RequestBody AlbumApiDto.CreateRequest request) {
        AlbumServiceDto.AlbumInfo info = albumService.createAlbum(new AlbumServiceDto.CreateCommand(
            request.groupRowId(),
            request.albumName(),
            request.description()
        ));
        return ApiResponse.success(AlbumApiDto.Response.from(info));
    }

    @GetMapping("/albums")
    public ApiResponse<AlbumApiDto.ListResponse> getAlbums(
            @LoginUser UserPrincipal loginUser,
            @RequestParam Long groupRowId) {
        List<AlbumServiceDto.AlbumInfo> infos = albumService.getAlbums(groupRowId);
        return ApiResponse.success(AlbumApiDto.ListResponse.from(infos));
    }

    @GetMapping("/album/{id}")
    public ApiResponse<AlbumApiDto.DetailResponse> getAlbum(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        AlbumServiceDto.AlbumDetailInfo info = albumService.getAlbum(id);
        return ApiResponse.success(AlbumApiDto.DetailResponse.from(info));
    }

    @PutMapping("/album/{id}")
    public ApiResponse<AlbumApiDto.Response> updateAlbum(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id,
            @RequestBody AlbumApiDto.UpdateRequest request) {
        AlbumServiceDto.AlbumInfo info = albumService.updateAlbum(id, new AlbumServiceDto.UpdateCommand(
            request.albumName(),
            request.description()
        ));
        return ApiResponse.success(AlbumApiDto.Response.from(info));
    }

    @DeleteMapping("/album/{id}")
    public ApiResponse<Void> deleteAlbum(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        albumService.deleteAlbum(id);
        return ApiResponse.success();
    }

    @PostMapping("/album/{albumId}/photo")
    public ApiResponse<AlbumApiDto.PhotoResponse> addPhoto(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long albumId,
            @RequestBody AlbumApiDto.AddPhotoRequest request) {
        AlbumServiceDto.PhotoInfo info = albumService.addPhoto(new AlbumServiceDto.AddPhotoCommand(
            albumId,
            request.fileRowId(),
            loginUser.getRowId(),
            request.caption()
        ));
        return ApiResponse.success(AlbumApiDto.PhotoResponse.from(info));
    }

    @DeleteMapping("/album/photo/{photoId}")
    public ApiResponse<Void> removePhoto(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long photoId) {
        albumService.removePhoto(photoId);
        return ApiResponse.success();
    }

    @PatchMapping("/album/{albumId}/cover")
    public ApiResponse<AlbumApiDto.Response> setCover(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long albumId,
            @RequestBody AlbumApiDto.SetCoverRequest request) {
        AlbumServiceDto.AlbumInfo info = albumService.setCover(albumId, request.fileRowId());
        return ApiResponse.success(AlbumApiDto.Response.from(info));
    }
}
