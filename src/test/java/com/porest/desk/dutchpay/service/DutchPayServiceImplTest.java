package com.porest.desk.dutchpay.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.core.type.YNType;
import com.porest.desk.dutchpay.domain.DutchPay;
import com.porest.desk.dutchpay.domain.DutchPayParticipant;
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

import static org.assertj.core.api.Assertions.assertThat;
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
        given(d.getActiveParticipants()).willReturn(List.of()); // 해당 참가자 없음
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
    @DisplayName("createDutchPay — 같은 등록 사용자를 중복 참가자로 추가하면 거부")
    void createRejectsDuplicateRegisteredParticipant() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        var cmd = new DutchPayServiceDto.CreateCommand(
                USER_ID, null, "점심", null, 10_000L, "KRW", SplitMethod.CUSTOM,
                LocalDate.of(2026, 6, 1),
                List.of(
                        new DutchPayServiceDto.ParticipantCommand(50L, "철수", 5_000L),
                        new DutchPayServiceDto.ParticipantCommand(50L, "철수(중복)", 5_000L)));

        assertThatThrownBy(() -> sut.createDutchPay(cmd))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("createDutchPay — 이름만 있는 참가자(이름 중복)도 거부")
    void createRejectsDuplicateNameOnlyParticipant() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        var cmd = new DutchPayServiceDto.CreateCommand(
                USER_ID, null, "점심", null, 10_000L, "KRW", SplitMethod.CUSTOM,
                LocalDate.of(2026, 6, 1),
                List.of(
                        new DutchPayServiceDto.ParticipantCommand(null, "영희", 5_000L),
                        new DutchPayServiceDto.ParticipantCommand(null, "영희", 5_000L)));

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

    // ── 정산 정상 동작 정확성 ─────────────────────────────
    @Test
    @DisplayName("markParticipantPaid — 마지막 미납자 납부 시 isSettled false→true")
    void markParticipantPaidLastUnpaidSettles() {
        DutchPay dp = DutchPay.createDutchPay(user(USER_ID), null, "회식", null, 20_000L, "KRW",
                SplitMethod.CUSTOM, LocalDate.of(2026, 6, 1));
        ReflectionTestUtils.setField(dp, "rowId", 5L);
        DutchPayParticipant p1 = DutchPayParticipant.create(dp, null, "A", 10_000L);
        ReflectionTestUtils.setField(p1, "rowId", 11L);
        p1.markPaid();
        DutchPayParticipant p2 = DutchPayParticipant.create(dp, null, "B", 10_000L);
        ReflectionTestUtils.setField(p2, "rowId", 12L);
        dp.addParticipant(p1);
        dp.addParticipant(p2);
        given(dutchPayRepository.findById(5L)).willReturn(Optional.of(dp));

        var info = sut.markParticipantPaid(5L, USER_ID, 12L);

        assertThat(info.isSettled()).isTrue();             // p1·p2 모두 납부 → 정산 완료
        assertThat(p2.getIsPaid()).isEqualTo(YNType.Y);
    }

    @Test
    @DisplayName("markParticipantPaid — 첫 납부자 처리해도 미납자 남으면 isSettled false 유지")
    void markParticipantPaidPartialNotSettled() {
        DutchPay dp = DutchPay.createDutchPay(user(USER_ID), null, "회식", null, 20_000L, "KRW",
                SplitMethod.CUSTOM, LocalDate.of(2026, 6, 1));
        ReflectionTestUtils.setField(dp, "rowId", 5L);
        DutchPayParticipant p1 = DutchPayParticipant.create(dp, null, "A", 10_000L);
        ReflectionTestUtils.setField(p1, "rowId", 11L);
        DutchPayParticipant p2 = DutchPayParticipant.create(dp, null, "B", 10_000L);
        ReflectionTestUtils.setField(p2, "rowId", 12L);
        dp.addParticipant(p1);
        dp.addParticipant(p2);
        given(dutchPayRepository.findById(5L)).willReturn(Optional.of(dp));

        var info = sut.markParticipantPaid(5L, USER_ID, 11L);

        assertThat(info.isSettled()).isFalse();
        assertThat(p1.getIsPaid()).isEqualTo(YNType.Y);
        assertThat(p2.getIsPaid()).isEqualTo(YNType.N);
    }

    @Test
    @DisplayName("createDutchPay — EQUAL 이어도 서버 균등분배 없이 클라 입력 금액 그대로 저장")
    void createStoresClientAmountsWithoutRedistribution() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        var cmd = new DutchPayServiceDto.CreateCommand(
                USER_ID, null, "점심", null, 10_000L, "KRW", SplitMethod.EQUAL, LocalDate.of(2026, 6, 1),
                List.of(
                        new DutchPayServiceDto.ParticipantCommand(null, "A", 3_000L),
                        new DutchPayServiceDto.ParticipantCommand(null, "B", 3_000L),
                        new DutchPayServiceDto.ParticipantCommand(null, "C", 4_000L)));

        var info = sut.createDutchPay(cmd);

        // EQUAL 이지만 10,000/3 균등분배(3,333..) 아님 — 입력 3,000/3,000/4,000 그대로
        assertThat(info.participants()).extracting(DutchPayServiceDto.ParticipantInfo::amount)
                .containsExactlyInAnyOrder(3_000L, 3_000L, 4_000L);
        assertThat(info.totalAmount()).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("createDutchPay — 참가자 합(8,000)≠totalAmount(10,000)이어도 통과(서버 합계검증 없음)")
    void createAllowsParticipantSumMismatch() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        var cmd = new DutchPayServiceDto.CreateCommand(
                USER_ID, null, "점심", null, 10_000L, "KRW", SplitMethod.CUSTOM, LocalDate.of(2026, 6, 1),
                List.of(
                        new DutchPayServiceDto.ParticipantCommand(null, "A", 4_000L),
                        new DutchPayServiceDto.ParticipantCommand(null, "B", 4_000L)));

        var info = sut.createDutchPay(cmd); // 예외 없이 성공

        long sum = info.participants().stream()
                .mapToLong(DutchPayServiceDto.ParticipantInfo::amount).sum();
        assertThat(sum).isEqualTo(8_000L);            // 합 8,000
        assertThat(info.totalAmount()).isEqualTo(10_000L); // total 10,000 (불일치 허용)
    }
}
