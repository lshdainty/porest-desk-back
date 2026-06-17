package com.porest.desk.memo.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.core.type.YNType;
import com.porest.desk.memo.domain.Memo;
import com.porest.desk.memo.domain.MemoFolder;
import com.porest.desk.memo.repository.MemoFolderRepository;
import com.porest.desk.memo.repository.MemoRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 메모 서비스 소유권 가드 회귀 방지 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class MemoServiceImplTest {

    @Mock private MemoRepository memoRepository;
    @Mock private MemoFolderRepository memoFolderRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private MemoServiceImpl sut;

    private static final long USER_ID = 1L;

    private User user(long rowId) {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", rowId);
        return u;
    }

    private Memo othersMemo() {
        Memo m = mock(Memo.class);
        given(m.getUser()).willReturn(user(999L));
        return m;
    }

    @Test
    @DisplayName("getMemo — 남의 메모는 조회 불가")
    void getRejectsOthers() {
        Memo m = othersMemo();
        given(memoRepository.findById(5L)).willReturn(Optional.of(m));

        assertThatThrownBy(() -> sut.getMemo(5L, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("togglePin — 남의 메모는 고정 불가")
    void togglePinRejectsOthers() {
        Memo m = othersMemo();
        given(memoRepository.findById(5L)).willReturn(Optional.of(m));

        assertThatThrownBy(() -> sut.togglePin(5L, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("deleteMemo — 남의 메모는 삭제 불가")
    void deleteRejectsOthers() {
        Memo m = othersMemo();
        given(memoRepository.findById(5L)).willReturn(Optional.of(m));

        assertThatThrownBy(() -> sut.deleteMemo(5L, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("updateMemo — 남의 폴더로 이동 불가(소유권 검증 누락 보강)")
    void updateRejectsOthersFolder() {
        Memo m = mock(Memo.class);
        given(m.getUser()).willReturn(user(USER_ID));
        given(memoRepository.findById(5L)).willReturn(Optional.of(m));
        MemoFolder othersFolder = mock(MemoFolder.class);
        given(othersFolder.getUser()).willReturn(user(999L));
        given(memoFolderRepository.findById(20L)).willReturn(Optional.of(othersFolder));

        var cmd = new MemoServiceDto.UpdateCommand(20L, "수정", "내용", null, null);

        assertThatThrownBy(() -> sut.updateMemo(5L, USER_ID, cmd))
                .isInstanceOf(ForbiddenException.class);
    }

    // ── 정상 CRUD 결과 정확성 ─────────────────────────────
    @Test
    @DisplayName("createMemo — isPinned=N·필드 1:1 매핑, folder 없으면 folderId=null")
    void createMemoDefaults() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        var cmd = new MemoServiceDto.CreateCommand(USER_ID, null, "회의록", "본문", "work", "#FF0000");
        MemoServiceDto.MemoInfo info = sut.createMemo(cmd);

        assertThat(info.userRowId()).isEqualTo(USER_ID);
        assertThat(info.folderId()).isNull();
        assertThat(info.title()).isEqualTo("회의록");
        assertThat(info.content()).isEqualTo("본문");
        assertThat(info.tag()).isEqualTo("work");
        assertThat(info.color()).isEqualTo("#FF0000");
        assertThat(info.isPinned()).isEqualTo(YNType.N);
    }

    @Test
    @DisplayName("togglePin — N→Y, 한 번 더 Y→N")
    void togglePinFlips() {
        Memo memo = Memo.createMemo(user(USER_ID), null, "old", "body", "tag", "#000000");
        ReflectionTestUtils.setField(memo, "rowId", 202L);
        given(memoRepository.findById(202L)).willReturn(Optional.of(memo));

        assertThat(sut.togglePin(202L, USER_ID).isPinned()).isEqualTo(YNType.Y); // N→Y
        assertThat(sut.togglePin(202L, USER_ID).isPinned()).isEqualTo(YNType.N); // Y→N
    }
}
