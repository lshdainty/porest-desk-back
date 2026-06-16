package com.porest.desk.dutchpay.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.desk.dutchpay.domain.DutchPay;
import com.porest.desk.dutchpay.repository.DutchPayRepository;
import com.porest.desk.expense.repository.ExpenseRepository;
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
 * 더치페이 서비스 소유권 가드 회귀 방지 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class DutchPayServiceImplTest {

    @Mock private DutchPayRepository dutchPayRepository;
    @Mock private UserRepository userRepository;
    @Mock private ExpenseRepository expenseRepository;

    @InjectMocks private DutchPayServiceImpl sut;

    private static final long USER_ID = 1L;

    private User user(long rowId) {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", rowId);
        return u;
    }

    private DutchPay othersDutchPay() {
        DutchPay d = mock(DutchPay.class);
        given(d.getUser()).willReturn(user(999L));
        return d;
    }

    @Test
    @DisplayName("getDutchPay — 남의 더치페이는 조회 불가")
    void getRejectsOthers() {
        DutchPay d = othersDutchPay();
        given(dutchPayRepository.findById(5L)).willReturn(Optional.of(d));

        assertThatThrownBy(() -> sut.getDutchPay(5L, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("updateDutchPay — 남의 더치페이는 수정 불가")
    void updateRejectsOthers() {
        DutchPay d = othersDutchPay();
        given(dutchPayRepository.findById(5L)).willReturn(Optional.of(d));

        assertThatThrownBy(() -> sut.updateDutchPay(5L, USER_ID, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("deleteDutchPay — 남의 더치페이는 삭제 불가")
    void deleteRejectsOthers() {
        DutchPay d = othersDutchPay();
        given(dutchPayRepository.findById(5L)).willReturn(Optional.of(d));

        assertThatThrownBy(() -> sut.deleteDutchPay(5L, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }
}
