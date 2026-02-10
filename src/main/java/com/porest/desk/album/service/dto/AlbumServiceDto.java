package com.porest.desk.album.service.dto;

import com.porest.desk.album.domain.Album;
import com.porest.desk.album.domain.AlbumPhoto;

import java.util.List;

public class AlbumServiceDto {

    public record CreateCommand(
        Long groupRowId,
        String albumName,
        String description
    ) {}

    public record UpdateCommand(
        String albumName,
        String description
    ) {}

    public record AddPhotoCommand(
        Long albumRowId,
        Long fileRowId,
        Long userRowId,
        String caption
    ) {}

    public record AlbumInfo(
        Long rowId,
        Long groupRowId,
        String albumName,
        String description,
        Long coverFileRowId,
        int photoCount,
        String createAt
    ) {
        public static AlbumInfo from(Album album, int photoCount) {
            return new AlbumInfo(
                album.getRowId(),
                album.getGroup().getRowId(),
                album.getAlbumName(),
                album.getDescription(),
                album.getCoverFile() != null ? album.getCoverFile().getRowId() : null,
                photoCount,
                album.getCreateAt() != null ? album.getCreateAt().toString() : null
            );
        }
    }

    public record AlbumDetailInfo(
        Long rowId,
        Long groupRowId,
        String albumName,
        String description,
        Long coverFileRowId,
        List<PhotoInfo> photos,
        String createAt
    ) {
        public static AlbumDetailInfo from(Album album, List<PhotoInfo> photos) {
            return new AlbumDetailInfo(
                album.getRowId(),
                album.getGroup().getRowId(),
                album.getAlbumName(),
                album.getDescription(),
                album.getCoverFile() != null ? album.getCoverFile().getRowId() : null,
                photos,
                album.getCreateAt() != null ? album.getCreateAt().toString() : null
            );
        }
    }

    public record PhotoInfo(
        Long rowId,
        Long albumRowId,
        Long fileRowId,
        String originalName,
        String contentType,
        Long fileSize,
        Long userRowId,
        String caption,
        Integer sortOrder,
        String createAt
    ) {
        public static PhotoInfo from(AlbumPhoto photo) {
            return new PhotoInfo(
                photo.getRowId(),
                photo.getAlbum().getRowId(),
                photo.getFile().getRowId(),
                photo.getFile().getOriginalName(),
                photo.getFile().getContentType(),
                photo.getFile().getFileSize(),
                photo.getUser().getRowId(),
                photo.getCaption(),
                photo.getSortOrder(),
                photo.getCreateAt() != null ? photo.getCreateAt().toString() : null
            );
        }
    }
}
