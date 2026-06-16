package com.porest.desk.dutchpay.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.dutchpay.domain.DutchPay;
import com.porest.desk.dutchpay.repository.DutchPayRepository;
import com.porest.desk.dutchpay.service.dto.DutchPayServiceDto;
import com.porest.desk.dutchpay.type.SplitMethod;
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

import java.time.LocalDate;
import java.util.List;
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

    @Test
    @DisplayName("markParticipantPaid — 남의 더치페이는 정산 처리 불가")
    void markPaidRejectsOthers() {
        DutchPay d = othersDutchPay();
        given(dutchPayRepository.findById(5L)).willReturn(Optional.of(d));

        assertThatThrownBy(() -> sut.markParticipantPaid(5L, USER_ID, 7L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("markParticipantPaid — 존재하지 않는 참가자는 NotFound")
    void markPaidRejectsUnknownParticipant() {
        DutchPay d = mock(DutchPay.class);
        given(d.getUser()).willReturn(user(USER_ID));
        given(d.getParticipants()).willReturn(List.of()); // 해당 참가자 없음
        given(dutchPayRepository.findById(5L)).willReturn(Optional.of(d));

        assertThatThrownBy(() -> sut.markParticipantPaid(5L, USER_ID, 7L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("createDutchPay — 참가자 금액이 0/음수면 거부(정산 데이터 오염 차단)")
    void createRejectsNonPositiveParticipantAmount() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        var cmd = new DutchPayServiceDto.CreateCommand(
                USER_ID, null, "점심", null, 10_000L, "KRW", SplitMethod.CUSTOM,
                LocalDate.of(2026, 6, 1),
                List.of(new DutchPayServiceDto.ParticipantCommand(null, "참가자A", -1_000L)));

        assertThatThrownBy(() -> sut.createDutchPay(cmd))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("settleAll — 남의 더치페이는 전체 정산 불가")
    void settleAllRejectsOthers() {
        DutchPay d = othersDutchPay();
        given(dutchPayRepository.findById(5L)).willReturn(Optional.of(d));

        assertThatThrownBy(() -> sut.settleAll(5L, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }
}
