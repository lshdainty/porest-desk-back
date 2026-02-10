package com.porest.desk.album.repository;

import com.porest.core.type.YNType;
import com.porest.desk.album.domain.AlbumPhoto;
import com.porest.desk.album.domain.QAlbumPhoto;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Primary
@RequiredArgsConstructor
public class AlbumPhotoQueryDslRepository implements AlbumPhotoRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QAlbumPhoto photo = QAlbumPhoto.albumPhoto;

    @Override
    public Optional<AlbumPhoto> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(photo)
                .where(photo.rowId.eq(rowId), photo.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public List<AlbumPhoto> findByAlbum(Long albumRowId) {
        return queryFactory.selectFrom(photo)
            .where(
                photo.album.rowId.eq(albumRowId),
                photo.isDeleted.eq(YNType.N)
            )
            .orderBy(photo.sortOrder.asc(), photo.rowId.asc())
            .fetch();
    }

    @Override
    public AlbumPhoto save(AlbumPhoto entity) {
        if (entity.getRowId() == null) {
            entityManager.persist(entity);
        } else {
            entityManager.merge(entity);
        }
        return entity;
    }
}
