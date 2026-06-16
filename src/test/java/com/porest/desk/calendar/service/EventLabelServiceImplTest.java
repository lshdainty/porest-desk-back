package com.porest.desk.calendar.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.calendar.domain.EventLabel;
import com.porest.desk.calendar.repository.EventLabelRepository;
import com.porest.desk.calendar.service.dto.EventLabelServiceDto;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 일정 라벨 서비스 회귀 방지 단위 테스트 — 생성 + 수정/삭제 소유권 가드.
 */
@ExtendWith(MockitoExtension.class)
class EventLabelServiceImplTest {

    @Mock private EventLabelRepository eventLabelRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private EventLabelServiceImpl sut;

    private static final long USER_ID = 1L;

    private User user(long rowId) {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", rowId);
        return u;
    }

    @Test
    @DisplayName("createLabel — 라벨을 생성하고 저장한다")
    void create() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        given(eventLabelRepository.findAllByUser(USER_ID)).willReturn(List.of());

        var info = sut.createLabel(new EventLabelServiceDto.CreateCommand(USER_ID, "업무", "#f00"));

        assertThat(info.labelName()).isEqualTo("업무");
        assertThat(info.sortOrder()).isEqualTo(0);
        verify(eventLabelRepository).save(any(EventLabel.class));
    }

    @Test
    @DisplayName("createLabel — 활성 라벨 중 같은 이름이 있으면 거부(중복 방지)")
    void createRejectsDuplicateActiveName() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        given(eventLabelRepository.existsActiveByUserAndName(USER_ID, "업무", null)).willReturn(true);

        assertThatThrownBy(() -> sut.createLabel(new EventLabelServiceDto.CreateCommand(USER_ID, "업무", "#f00")))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("updateLabel — 남의 라벨은 수정 불가")
    void updateRejectsOthers() {
        EventLabel label = mock(EventLabel.class);
        given(label.getUser()).willReturn(user(999L));
        given(eventLabelRepository.findById(5L)).willReturn(Optional.of(label));

        assertThatThrownBy(() -> sut.updateLabel(5L, USER_ID,
                new EventLabelServiceDto.UpdateCommand("수정", "#0f0")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("deleteLabel — 남의 라벨은 삭제 불가")
    void deleteRejectsOthers() {
        EventLabel label = mock(EventLabel.class);
        given(label.getUser()).willReturn(user(999L));
        given(eventLabelRepository.findById(5L)).willReturn(Optional.of(label));

        assertThatThrownBy(() -> sut.deleteLabel(5L, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }
}
