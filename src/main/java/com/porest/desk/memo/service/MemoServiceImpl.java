package com.porest.desk.memo.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.memo.domain.Memo;
import com.porest.desk.memo.domain.MemoFolder;
import com.porest.desk.memo.repository.MemoFolderRepository;
import com.porest.desk.memo.repository.MemoRepository;
import com.porest.desk.memo.service.dto.MemoServiceDto;
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
public class MemoServiceImpl implements MemoService {
    private final MemoRepository memoRepository;
    private final MemoFolderRepository memoFolderRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public MemoServiceDto.MemoInfo createMemo(MemoServiceDto.CreateCommand command) {
        log.debug("메모 등록 시작: userRowId={}, title={}", command.userRowId(), command.title());

        User user = userRepository.findById(command.userRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

        MemoFolder folder = null;
        if (command.folderId() != null) {
            folder = memoFolderRepository.findById(command.folderId())
                .orElseThrow(() -> {
                    log.warn("메모 폴더 조회 실패 - 존재하지 않는 폴더: folderId={}", command.folderId());
                    return new EntityNotFoundException(DeskErrorCode.MEMO_FOLDER_NOT_FOUND);
                });
            validateFolderOwnership(folder, command.userRowId());
        }

        Memo memo = Memo.createMemo(user, folder, command.title(), command.content());

        memoRepository.save(memo);
        log.info("메모 등록 완료: memoId={}, userRowId={}", memo.getRowId(), command.userRowId());

        return MemoServiceDto.MemoInfo.from(memo);
    }

    @Override
    public List<MemoServiceDto.MemoInfo> getMemos(Long userRowId, Long folderId, String search) {
        log.debug("메모 목록 조회: userRowId={}, folderId={}, search={}", userRowId, folderId, search);

        List<Memo> memos = memoRepository.findAllByUser(userRowId, folderId, search);

        return memos.stream()
            .map(MemoServiceDto.MemoInfo::from)
            .toList();
    }

    @Override
    public MemoServiceDto.MemoInfo getMemo(Long memoId, Long userRowId) {
        log.debug("메모 상세 조회: memoId={}", memoId);

        Memo memo = findMemoOrThrow(memoId);
        validateMemoOwnership(memo, userRowId);

        return MemoServiceDto.MemoInfo.from(memo);
    }

    @Override
    @Transactional
    public MemoServiceDto.MemoInfo updateMemo(Long memoId, Long userRowId, MemoServiceDto.UpdateCommand command) {
        log.debug("메모 수정 시작: memoId={}", memoId);

        Memo memo = findMemoOrThrow(memoId);
        validateMemoOwnership(memo, userRowId);

        MemoFolder folder = null;
        if (command.folderId() != null) {
            folder = memoFolderRepository.findById(command.folderId())
                .orElseThrow(() -> {
                    log.warn("메모 폴더 조회 실패 - 존재하지 않는 폴더: folderId={}", command.folderId());
                    return new EntityNotFoundException(DeskErrorCode.MEMO_FOLDER_NOT_FOUND);
                });
        }

        memo.updateMemo(folder, command.title(), command.content());

        log.info("메모 수정 완료: memoId={}", memoId);

        return MemoServiceDto.MemoInfo.from(memo);
    }

    @Override
    @Transactional
    public MemoServiceDto.MemoInfo togglePin(Long memoId, Long userRowId) {
        log.debug("메모 핀 토글 시작: memoId={}", memoId);

        Memo memo = findMemoOrThrow(memoId);
        validateMemoOwnership(memo, userRowId);
        memo.togglePin();

        log.info("메모 핀 토글 완료: memoId={}, isPinned={}", memoId, memo.getIsPinned());

        return MemoServiceDto.MemoInfo.from(memo);
    }

    @Override
    @Transactional
    public void deleteMemo(Long memoId, Long userRowId) {
        log.debug("메모 삭제 시작: memoId={}", memoId);

        Memo memo = findMemoOrThrow(memoId);
        validateMemoOwnership(memo, userRowId);
        memo.deleteMemo();

        log.info("메모 삭제 완료: memoId={}", memoId);
    }

    private void validateMemoOwnership(Memo memo, Long userRowId) {
        if (!memo.getUser().getRowId().equals(userRowId)) {
            log.warn("메모 소유권 검증 실패 - memoId={}, ownerRowId={}, requestUserRowId={}",
                memo.getRowId(), memo.getUser().getRowId(), userRowId);
            throw new ForbiddenException(DeskErrorCode.MEMO_ACCESS_DENIED);
        }
    }

    private void validateFolderOwnership(MemoFolder folder, Long userRowId) {
        if (!folder.getUser().getRowId().equals(userRowId)) {
            log.warn("메모 폴더 소유권 검증 실패 - folderId={}, ownerRowId={}, requestUserRowId={}",
                folder.getRowId(), folder.getUser().getRowId(), userRowId);
            throw new ForbiddenException(DeskErrorCode.MEMO_ACCESS_DENIED);
        }
    }

    private Memo findMemoOrThrow(Long memoId) {
        return memoRepository.findById(memoId)
            .orElseThrow(() -> {
                log.warn("메모 조회 실패 - 존재하지 않는 메모: memoId={}", memoId);
                return new EntityNotFoundException(DeskErrorCode.MEMO_NOT_FOUND);
            });
    }
}
