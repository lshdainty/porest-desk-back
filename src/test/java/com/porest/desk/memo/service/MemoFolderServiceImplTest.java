package com.porest.desk.memo.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.desk.memo.domain.MemoFolder;
import com.porest.desk.memo.repository.MemoFolderRepository;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.porest.desk.memo.service.dto.MemoServiceDto;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 메모 폴더 서비스 소유권 가드 회귀 방지 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class MemoFolderServiceImplTest {

    @Mock private MemoFolderRepository memoFolderRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private MemoFolderServiceImpl sut;

    private static final long USER_ID = 1L;

    private User user(long rowId) {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", rowId);
        return u;
    }

    private MemoFolder othersFolder() {
        MemoFolder f = mock(MemoFolder.class);
        given(f.getUser()).willReturn(user(999L));
        return f;
    }

    @Test
    @DisplayName("updateFolder — 남의 폴더는 수정 불가")
    void updateRejectsOthers() {
        MemoFolder f = othersFolder();
        given(memoFolderRepository.findById(5L)).willReturn(Optional.of(f));

        assertThatThrownBy(() -> sut.updateFolder(5L, USER_ID, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("deleteFolder — 남의 폴더는 삭제 불가")
    void deleteRejectsOthers() {
        MemoFolder f = othersFolder();
        given(memoFolderRepository.findById(5L)).willReturn(Optional.of(f));

        assertThatThrownBy(() -> sut.deleteFolder(5L, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("updateFolder — 남의 폴더를 상위(parent)로 지정 불가(소유권 검증 누락 보강)")
    void updateRejectsOthersParent() {
        MemoFolder owned = mock(MemoFolder.class);
        given(owned.getUser()).willReturn(user(USER_ID));
        given(memoFolderRepository.findById(5L)).willReturn(Optional.of(owned));
        MemoFolder othersParent = othersFolder();
        given(memoFolderRepository.findById(20L)).willReturn(Optional.of(othersParent));

        var cmd = new MemoServiceDto.FolderUpdateCommand(20L, "수정", null);

        assertThatThrownBy(() -> sut.updateFolder(5L, USER_ID, cmd))
                .isInstanceOf(ForbiddenException.class);
    }
}
