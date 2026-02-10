package com.porest.desk.album.repository;

import com.porest.core.type.YNType;
import com.porest.desk.album.domain.Album;
import com.porest.desk.album.domain.QAlbum;
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
public class AlbumQueryDslRepository implements AlbumRepository {
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QAlbum album = QAlbum.album;

    @Override
    public Optional<Album> findById(Long rowId) {
        return Optional.ofNullable(
            queryFactory.selectFrom(album)
                .where(album.rowId.eq(rowId), album.isDeleted.eq(YNType.N))
                .fetchOne()
        );
    }

    @Override
    public List<Album> findByGroup(Long groupRowId) {
        return queryFactory.selectFrom(album)
            .where(
                album.group.rowId.eq(groupRowId),
                album.isDeleted.eq(YNType.N)
            )
            .orderBy(album.rowId.desc())
            .fetch();
    }

    @Override
    public Album save(Album entity) {
        if (entity.getRowId() == null) {
            entityManager.persist(entity);
        } else {
            entityManager.merge(entity);
        }
        return entity;
    }
}
