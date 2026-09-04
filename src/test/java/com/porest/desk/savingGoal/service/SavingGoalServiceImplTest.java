package com.porest.desk.savingGoal.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.common.exception.DeskErrorCode;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

    private SavingGoalServiceDto.CreateCommand createCmd(String title) {
        return new SavingGoalServiceDto.CreateCommand(
                USER_ID, title, null, 1_000_000L, "KRW", null, null, null, null, 0);
    }

    @Test
    @DisplayName("createSavingGoal — 활성 목표 중 같은 이름이 있으면 409(사용자 확정 문구가 나가는 자리)")
    void createRejectsDuplicateActiveTitle() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        given(savingGoalRepository.existsActiveByUserAndTitle(USER_ID, "유럽 여행", null)).willReturn(true);

        assertThatThrownBy(() -> sut.createSavingGoal(createCmd("유럽 여행")))
                .isInstanceOf(InvalidValueException.class)
                .extracting(e -> ((InvalidValueException) e).getErrorCode())
                .isEqualTo(DeskErrorCode.SAVING_GOAL_DUPLICATE_NAME);
        verify(savingGoalRepository, never()).save(any());
    }

    @Test
    @DisplayName("createSavingGoal — 이름 앞뒤 공백은 저장 전에 잘린다")
    void createTrimsTitle() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        given(savingGoalRepository.existsActiveByUserAndTitle(USER_ID, "유럽 여행", null)).willReturn(false);

        var info = sut.createSavingGoal(createCmd("  유럽 여행 "));

        assertThat(info.title()).isEqualTo("유럽 여행");
    }

    @Test
    @DisplayName("createSavingGoal — 빈 이름은 400 으로 거절한다(NOT NULL 위반 500 이 아니라)")
    void createRejectsBlankTitle() {
        assertThatThrownBy(() -> sut.createSavingGoal(createCmd("   ")))
                .isInstanceOf(InvalidValueException.class)
                .extracting(e -> ((InvalidValueException) e).getErrorCode())
                .isEqualTo(DeskErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("updateSavingGoal — 자기 자신은 중복 검사에서 뺀다(이름을 그대로 두는 저장이 막히면 안 된다)")
    void updateExcludesSelf() {
        SavingGoal goal = SavingGoal.createSavingGoal(
                user(USER_ID), "유럽 여행", null, 1_000_000L, "KRW", null, null, null, null, 0);
        given(savingGoalRepository.findById(5L)).willReturn(Optional.of(goal));
        given(savingGoalRepository.existsActiveByUserAndTitle(USER_ID, "유럽 여행", 5L)).willReturn(false);

        assertThatCode(() -> sut.updateSavingGoal(5L, USER_ID, new SavingGoalServiceDto.UpdateCommand(
                "유럽 여행", null, 2_000_000L, null, null, null, null)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("updateSavingGoal — 다른 목표와 이름이 겹치면 409")
    void updateRejectsDuplicateActiveTitle() {
        SavingGoal goal = SavingGoal.createSavingGoal(
                user(USER_ID), "비상금", null, 1_000_000L, "KRW", null, null, null, null, 0);
        given(savingGoalRepository.findById(5L)).willReturn(Optional.of(goal));
        given(savingGoalRepository.existsActiveByUserAndTitle(USER_ID, "유럽 여행", 5L)).willReturn(true);

        assertThatThrownBy(() -> sut.updateSavingGoal(5L, USER_ID, new SavingGoalServiceDto.UpdateCommand(
                "유럽 여행", null, 2_000_000L, null, null, null, null)))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("유니크 위반(동시 저장 경쟁)은 500 이 아니라 409 로 나간다")
    void translatesConstraintViolation() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        given(savingGoalRepository.existsActiveByUserAndTitle(USER_ID, "유럽 여행", null)).willReturn(false);
        willThrow(new DataIntegrityViolationException("UK_saving_goal_active_name"))
                .given(savingGoalRepository).flush();

        assertThatThrownBy(() -> sut.createSavingGoal(createCmd("유럽 여행")))
                .isInstanceOf(InvalidValueException.class)
                .extracting(e -> ((InvalidValueException) e).getErrorCode())
                .isEqualTo(DeskErrorCode.SAVING_GOAL_DUPLICATE_NAME);
    }
}
