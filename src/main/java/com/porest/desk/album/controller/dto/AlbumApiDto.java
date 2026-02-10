package com.porest.desk.album.controller.dto;

import com.porest.desk.album.service.dto.AlbumServiceDto;

import java.util.List;

public class AlbumApiDto {

    public record CreateRequest(
        Long groupRowId,
        String albumName,
        String description
    ) {}

    public record UpdateRequest(
        String albumName,
        String description
    ) {}

    public record AddPhotoRequest(
        Long fileRowId,
        String caption
    ) {}

    public record SetCoverRequest(
        Long fileRowId
    ) {}

    public record Response(
        Long rowId,
        Long groupRowId,
        String albumName,
        String description,
        Long coverFileRowId,
        int photoCount,
        String createAt
    ) {
        public static Response from(AlbumServiceDto.AlbumInfo info) {
            return new Response(
                info.rowId(),
                info.groupRowId(),
                info.albumName(),
                info.description(),
                info.coverFileRowId(),
                info.photoCount(),
                info.createAt()
            );
        }
    }

    public record DetailResponse(
        Long rowId,
        Long groupRowId,
        String albumName,
        String description,
        Long coverFileRowId,
        List<PhotoResponse> photos,
        String createAt
    ) {
        public static DetailResponse from(AlbumServiceDto.AlbumDetailInfo info) {
            return new DetailResponse(
                info.rowId(),
                info.groupRowId(),
                info.albumName(),
                info.description(),
                info.coverFileRowId(),
                info.photos().stream().map(PhotoResponse::from).toList(),
                info.createAt()
            );
        }
    }

    public record PhotoResponse(
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
        public static PhotoResponse from(AlbumServiceDto.PhotoInfo info) {
            return new PhotoResponse(
                info.rowId(),
                info.albumRowId(),
                info.fileRowId(),
                info.originalName(),
                info.contentType(),
                info.fileSize(),
                info.userRowId(),
                info.caption(),
                info.sortOrder(),
                info.createAt()
            );
        }
    }

    public record ListResponse(List<Response> albums) {
        public static ListResponse from(List<AlbumServiceDto.AlbumInfo> infos) {
            return new ListResponse(
                infos.stream().map(Response::from).toList()
            );
        }
    }
}
