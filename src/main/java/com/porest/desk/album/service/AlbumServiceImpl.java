package com.porest.desk.album.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.desk.album.domain.Album;
import com.porest.desk.album.domain.AlbumPhoto;
import com.porest.desk.album.repository.AlbumPhotoRepository;
import com.porest.desk.album.repository.AlbumRepository;
import com.porest.desk.album.service.dto.AlbumServiceDto;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.file.domain.FileAttachment;
import com.porest.desk.file.repository.FileAttachmentRepository;
import com.porest.desk.group.domain.UserGroup;
import com.porest.desk.group.repository.UserGroupRepository;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AlbumServiceImpl implements AlbumService {
    private final AlbumRepository albumRepository;
    private final AlbumPhotoRepository albumPhotoRepository;
    private final UserGroupRepository userGroupRepository;
    private final FileAttachmentRepository fileAttachmentRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public AlbumServiceDto.AlbumInfo createAlbum(AlbumServiceDto.CreateCommand command) {
        log.debug("앨범 생성 시작: groupRowId={}", command.groupRowId());

        UserGroup group = userGroupRepository.findById(command.groupRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.GROUP_NOT_FOUND));

        Album album = Album.createAlbum(group, command.albumName(), command.description());
        albumRepository.save(album);

        log.info("앨범 생성 완료: albumId={}", album.getRowId());
        return AlbumServiceDto.AlbumInfo.from(album, 0);
    }

    @Override
    public List<AlbumServiceDto.AlbumInfo> getAlbums(Long groupRowId) {
        log.debug("앨범 목록 조회: groupRowId={}", groupRowId);

        return albumRepository.findByGroup(groupRowId).stream()
            .map(album -> {
                int photoCount = albumPhotoRepository.findByAlbum(album.getRowId()).size();
                return AlbumServiceDto.AlbumInfo.from(album, photoCount);
            })
            .toList();
    }

    @Override
    public AlbumServiceDto.AlbumDetailInfo getAlbum(Long albumId) {
        log.debug("앨범 상세 조회: albumId={}", albumId);

        Album album = findAlbumOrThrow(albumId);
        List<AlbumServiceDto.PhotoInfo> photos = albumPhotoRepository.findByAlbum(albumId).stream()
            .map(AlbumServiceDto.PhotoInfo::from)
            .toList();

        return AlbumServiceDto.AlbumDetailInfo.from(album, photos);
    }

    @Override
    @Transactional
    public AlbumServiceDto.AlbumInfo updateAlbum(Long albumId, AlbumServiceDto.UpdateCommand command) {
        log.debug("앨범 수정 시작: albumId={}", albumId);

        Album album = findAlbumOrThrow(albumId);
        album.updateAlbum(command.albumName(), command.description());

        int photoCount = albumPhotoRepository.findByAlbum(albumId).size();
        log.info("앨범 수정 완료: albumId={}", albumId);

        return AlbumServiceDto.AlbumInfo.from(album, photoCount);
    }

    @Override
    @Transactional
    public void deleteAlbum(Long albumId) {
        log.debug("앨범 삭제 시작: albumId={}", albumId);

        Album album = findAlbumOrThrow(albumId);
        album.deleteAlbum();

        log.info("앨범 삭제 완료: albumId={}", albumId);
    }

    @Override
    @Transactional
    public AlbumServiceDto.PhotoInfo addPhoto(AlbumServiceDto.AddPhotoCommand command) {
        log.debug("앨범 사진 추가 시작: albumRowId={}, fileRowId={}", command.albumRowId(), command.fileRowId());

        Album album = findAlbumOrThrow(command.albumRowId());

        FileAttachment file = fileAttachmentRepository.findById(command.fileRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.FILE_NOT_FOUND));

        User user = userRepository.findById(command.userRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

        int currentCount = albumPhotoRepository.findByAlbum(command.albumRowId()).size();
        AlbumPhoto photo = AlbumPhoto.createPhoto(album, file, user, command.caption(), currentCount);
        albumPhotoRepository.save(photo);

        log.info("앨범 사진 추가 완료: photoId={}", photo.getRowId());
        return AlbumServiceDto.PhotoInfo.from(photo);
    }

    @Override
    @Transactional
    public void removePhoto(Long photoId) {
        log.debug("앨범 사진 삭제 시작: photoId={}", photoId);

        AlbumPhoto photo = albumPhotoRepository.findById(photoId)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.ALBUM_PHOTO_NOT_FOUND));

        photo.deletePhoto();
        log.info("앨범 사진 삭제 완료: photoId={}", photoId);
    }

    @Override
    @Transactional
    public AlbumServiceDto.AlbumInfo setCover(Long albumId, Long fileRowId) {
        log.debug("앨범 커버 설정: albumId={}, fileRowId={}", albumId, fileRowId);

        Album album = findAlbumOrThrow(albumId);

        FileAttachment file = fileAttachmentRepository.findById(fileRowId)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.FILE_NOT_FOUND));

        album.setCoverFile(file);
        int photoCount = albumPhotoRepository.findByAlbum(albumId).size();

        log.info("앨범 커버 설정 완료: albumId={}", albumId);
        return AlbumServiceDto.AlbumInfo.from(album, photoCount);
    }

    private Album findAlbumOrThrow(Long albumId) {
        return albumRepository.findById(albumId)
            .orElseThrow(() -> {
                log.warn("앨범 조회 실패 - 존재하지 않는 앨범: albumId={}", albumId);
                return new EntityNotFoundException(DeskErrorCode.ALBUM_NOT_FOUND);
            });
    }
}
