package com.porest.desk.savingGoal.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.savingGoal.domain.SavingGoal;
import com.porest.desk.savingGoal.repository.SavingGoalRepository;
import com.porest.desk.savingGoal.service.dto.SavingGoalServiceDto;
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
 * 저축 목표 서비스 소유권 가드 회귀 방지 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class SavingGoalServiceImplTest {

    @Mock private SavingGoalRepository savingGoalRepository;
    @Mock private UserRepository userRepository;
    @Mock private AssetRepository assetRepository;

    @InjectMocks private SavingGoalServiceImpl sut;

    private static final long USER_ID = 1L;

    private User user(long rowId) {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", rowId);
        return u;
    }

    private SavingGoal othersGoal() {
        SavingGoal g = mock(SavingGoal.class);
        given(g.getUser()).willReturn(user(999L));
        return g;
    }

    @Test
    @DisplayName("getSavingGoal — 남의 목표는 조회 불가")
    void getRejectsOthers() {
        SavingGoal g = othersGoal();
        given(savingGoalRepository.findById(5L)).willReturn(Optional.of(g));

        assertThatThrownBy(() -> sut.getSavingGoal(5L, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("updateSavingGoal — 남의 목표는 수정 불가")
    void updateRejectsOthers() {
        SavingGoal g = othersGoal();
        given(savingGoalRepository.findById(5L)).willReturn(Optional.of(g));

        assertThatThrownBy(() -> sut.updateSavingGoal(5L, USER_ID, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("contribute — 남의 목표에는 납입 불가")
    void contributeRejectsOthers() {
        SavingGoal g = othersGoal();
        given(savingGoalRepository.findById(5L)).willReturn(Optional.of(g));

        assertThatThrownBy(() -> sut.contribute(5L, USER_ID,
                new SavingGoalServiceDto.ContributeCommand(10_000L, null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("deleteSavingGoal — 남의 목표는 삭제 불가")
    void deleteRejectsOthers() {
        SavingGoal g = othersGoal();
        given(savingGoalRepository.findById(5L)).willReturn(Optional.of(g));

        assertThatThrownBy(() -> sut.deleteSavingGoal(5L, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }
}
