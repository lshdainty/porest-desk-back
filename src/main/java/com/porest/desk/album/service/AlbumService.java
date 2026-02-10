package com.porest.desk.album.service;

import com.porest.desk.album.service.dto.AlbumServiceDto;

import java.util.List;

public interface AlbumService {
    AlbumServiceDto.AlbumInfo createAlbum(AlbumServiceDto.CreateCommand command);
    List<AlbumServiceDto.AlbumInfo> getAlbums(Long groupRowId);
    AlbumServiceDto.AlbumDetailInfo getAlbum(Long albumId);
    AlbumServiceDto.AlbumInfo updateAlbum(Long albumId, AlbumServiceDto.UpdateCommand command);
    void deleteAlbum(Long albumId);
    AlbumServiceDto.PhotoInfo addPhoto(AlbumServiceDto.AddPhotoCommand command);
    void removePhoto(Long photoId);
    AlbumServiceDto.AlbumInfo setCover(Long albumId, Long fileRowId);
}
