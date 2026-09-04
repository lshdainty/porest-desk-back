package com.porest.desk.memo.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.common.util.NameNormalizer;
import com.porest.desk.common.validation.FieldLimits;
import com.porest.desk.memo.domain.MemoFolder;
import com.porest.desk.memo.repository.MemoFolderRepository;
import com.porest.desk.memo.service.dto.MemoServiceDto;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MemoFolderServiceImpl implements MemoFolderService {
    private final MemoFolderRepository memoFolderRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public MemoServiceDto.FolderInfo createFolder(MemoServiceDto.FolderCreateCommand command) {
        log.debug("메모 폴더 등록 시작: userRowId={}, folderName={}", command.userRowId(), command.folderName());

        // 저장 전에 이름을 한 번 다듬는다 — 검사와 저장이 같은 값을 보게 만드는 자리다.
        String folderName = NameNormalizer.require(command.folderName(), FieldLimits.WIDE_NAME_MAX);

        User user = userRepository.findById(command.userRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

        MemoFolder parent = null;
        if (command.parentId() != null) {
            parent = memoFolderRepository.findById(command.parentId())
                .orElseThrow(() -> {
                    log.warn("메모 상위 폴더 조회 실패 - 존재하지 않는 폴더: parentId={}", command.parentId());
                    return new EntityNotFoundException(DeskErrorCode.MEMO_FOLDER_NOT_FOUND);
                });
            validateFolderOwnership(parent, command.userRowId());
        }

        // 같은 위치(부모) 내 활성 폴더명 중복 금지 (삭제된 같은 이름은 재사용 허용).
        // parentId 가 null(루트)인 경우를 리포지토리 두 구현 모두 `parent IS NULL` 로 갈라 놨다 —
        // `= null` 로 비교하면 아무것도 안 잡혀 루트 폴더만 중복이 무제한 허용된다.
        if (memoFolderRepository.existsActiveByUserAndParentAndName(
                command.userRowId(), command.parentId(), folderName, null)) {
            throw new InvalidValueException(DeskErrorCode.MEMO_FOLDER_DUPLICATE_NAME);
        }

        MemoFolder folder = MemoFolder.createFolder(user, parent, folderName);

        memoFolderRepository.save(folder);
        flushOrRejectDuplicate();
        log.info("메모 폴더 등록 완료: folderId={}, userRowId={}", folder.getRowId(), command.userRowId());

        return MemoServiceDto.FolderInfo.from(folder);
    }

    @Override
    public List<MemoServiceDto.FolderInfo> getFolders(Long userRowId) {
        log.debug("메모 폴더 목록 조회: userRowId={}", userRowId);

        List<MemoFolder> folders = memoFolderRepository.findAllByUser(userRowId);

        return folders.stream()
            .map(MemoServiceDto.FolderInfo::from)
            .toList();
    }

    @Override
    @Transactional
    public MemoServiceDto.FolderInfo updateFolder(Long folderId, Long userRowId, MemoServiceDto.FolderUpdateCommand command) {
        log.debug("메모 폴더 수정 시작: folderId={}", folderId);

        MemoFolder folder = findFolderOrThrow(folderId);
        validateFolderOwnership(folder, userRowId);

        MemoFolder parent = null;
        if (command.parentId() != null) {
            parent = memoFolderRepository.findById(command.parentId())
                .orElseThrow(() -> {
                    log.warn("메모 상위 폴더 조회 실패 - 존재하지 않는 폴더: parentId={}", command.parentId());
                    return new EntityNotFoundException(DeskErrorCode.MEMO_FOLDER_NOT_FOUND);
                });
            validateFolderOwnership(parent, userRowId); // create 와 대칭 — 남의 폴더 하위로 이동 차단
            validateNoCycle(parent, folderId); // 자기 자신/하위 폴더를 상위로 지정하는 순환 차단
        }

        String folderName = NameNormalizer.require(command.folderName(), FieldLimits.WIDE_NAME_MAX);
        // 이동/개명 후 같은 위치(부모) 내 활성 폴더명 중복 금지 (자기 자신 제외)
        if (memoFolderRepository.existsActiveByUserAndParentAndName(
                userRowId, command.parentId(), folderName, folderId)) {
            throw new InvalidValueException(DeskErrorCode.MEMO_FOLDER_DUPLICATE_NAME);
        }

        folder.updateFolder(parent, folderName, command.sortOrder());
        flushOrRejectDuplicate();

        log.info("메모 폴더 수정 완료: folderId={}", folderId);

        return MemoServiceDto.FolderInfo.from(folder);
    }

    @Override
    @Transactional
    public void deleteFolder(Long folderId, Long userRowId) {
        log.debug("메모 폴더 삭제 시작: folderId={}", folderId);

        MemoFolder folder = findFolderOrThrow(folderId);
        validateFolderOwnership(folder, userRowId);
        folder.deleteFolder();

        log.info("메모 폴더 삭제 완료: folderId={}", folderId);
    }

    /**
     * 조회 검사를 빠져나간 동시 저장 경쟁을 409 로 받는다 — 라벨과 같은 이유·같은 모양이다
     * (EventLabelServiceImpl.flushOrRejectDuplicate 주석에 전말을 적어 뒀다).
     */
    private void flushOrRejectDuplicate() {
        try {
            memoFolderRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new InvalidValueException(DeskErrorCode.MEMO_FOLDER_DUPLICATE_NAME, e);
        }
    }

    /**
     * 순환 방지: 새 상위(parent) 후보의 조상 체인에 수정 대상 폴더가 있으면 순환.
     * 자기 자신을 상위로 지정하는 경우(첫 노드 == folderId)도 함께 차단한다.
     * 기존 데이터가 이미 순환이어도 무한루프에 빠지지 않도록 깊이 상한을 둔다.
     */
    private void validateNoCycle(MemoFolder parent, Long folderId) {
        MemoFolder ancestor = parent;
        int guard = 0;
        while (ancestor != null && guard++ < 100) {
            if (folderId.equals(ancestor.getRowId())) {
                log.warn("메모 폴더 순환 지정 차단 - folderId={}, parentChain 에 자기 자신 포함", folderId);
                throw new InvalidValueException(DeskErrorCode.MEMO_FOLDER_INVALID_PARENT);
            }
            ancestor = ancestor.getParent();
        }
    }

    private void validateFolderOwnership(MemoFolder folder, Long userRowId) {
        if (!folder.getUser().getRowId().equals(userRowId)) {
            log.warn("메모 폴더 소유권 검증 실패 - folderId={}, ownerRowId={}, requestUserRowId={}",
                folder.getRowId(), folder.getUser().getRowId(), userRowId);
            throw new ForbiddenException(DeskErrorCode.MEMO_ACCESS_DENIED);
        }
    }

    private MemoFolder findFolderOrThrow(Long folderId) {
        return memoFolderRepository.findById(folderId)
            .orElseThrow(() -> {
                log.warn("메모 폴더 조회 실패 - 존재하지 않는 폴더: folderId={}", folderId);
                return new EntityNotFoundException(DeskErrorCode.MEMO_FOLDER_NOT_FOUND);
            });
    }
}
