package com.porest.desk.album.repository;

import com.porest.desk.album.domain.Album;

import java.util.List;
import java.util.Optional;

public interface AlbumRepository {
    Optional<Album> findById(Long rowId);
    List<Album> findByGroup(Long groupRowId);
    Album save(Album entity);
}
