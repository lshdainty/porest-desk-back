package com.porest.desk.user.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.desk.security.jwt.JwtTokenProvider;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * 사용자 서비스 단위 테스트 — 예산 알림 임계값 조회(기본값/조회 실패).
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private UserRepository userRepository;
    @Mock private RestTemplate ssoRestTemplate;

    @InjectMocks private UserServiceImpl sut;

    private static final long USER_ID = 1L;

    private User userWithThreshold(Integer threshold) {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", USER_ID);
        ReflectionTestUtils.setField(u, "budgetAlertThreshold", threshold);
        return u;
    }

    @Test
    @DisplayName("getBudgetAlertThreshold — 설정값을 반환한다")
    void returnsConfiguredThreshold() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(userWithThreshold(90)));

        assertThat(sut.getBudgetAlertThreshold(USER_ID)).isEqualTo(90);
    }

    @Test
    @DisplayName("getBudgetAlertThreshold — 값이 없으면 기본값 85")
    void returnsDefaultWhenNull() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(userWithThreshold(null)));

        assertThat(sut.getBudgetAlertThreshold(USER_ID)).isEqualTo(85);
    }

    @Test
    @DisplayName("getBudgetAlertThreshold — 사용자가 없으면 NotFound")
    void throwsWhenUserNotFound() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.getBudgetAlertThreshold(USER_ID))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
