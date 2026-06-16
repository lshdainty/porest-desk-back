package com.porest.desk.memo.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.desk.memo.domain.Memo;
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

import java.util.Optional;

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
}
