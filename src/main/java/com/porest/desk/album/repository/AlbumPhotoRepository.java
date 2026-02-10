package com.porest.desk.album.repository;

import com.porest.desk.album.domain.AlbumPhoto;

import java.util.List;
import java.util.Optional;

public interface AlbumPhotoRepository {
    Optional<AlbumPhoto> findById(Long rowId);
    List<AlbumPhoto> findByAlbum(Long albumRowId);
    AlbumPhoto save(AlbumPhoto entity);
}
