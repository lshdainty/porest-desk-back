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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import com.porest.desk.common.exception.DeskErrorCode;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
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
                List.of(new DutchPayServiceDto.ParticipantCommand(
            null,null, "참가자A", -1_000L, true)));

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
                        new DutchPayServiceDto.ParticipantCommand(
            null,50L, "철수", 5_000L, true),
                        new DutchPayServiceDto.ParticipantCommand(
            null,50L, "철수(중복)", 5_000L, false)));

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
                        new DutchPayServiceDto.ParticipantCommand(
            null,null, "영희", 5_000L, true),
                        new DutchPayServiceDto.ParticipantCommand(
            null,null, "영희", 5_000L, false)));

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
        DutchPayParticipant payer = DutchPayParticipant.create(dp, null, "결제자", 10_000L, true);
        ReflectionTestUtils.setField(payer, "rowId", 10L);
        dp.addParticipant(payer);
        DutchPayParticipant p1 = DutchPayParticipant.create(dp, null, "A", 10_000L, false);
        ReflectionTestUtils.setField(p1, "rowId", 11L);
        p1.markPaid();
        DutchPayParticipant p2 = DutchPayParticipant.create(dp, null, "B", 10_000L, false);
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
        DutchPayParticipant payer = DutchPayParticipant.create(dp, null, "결제자", 10_000L, true);
        ReflectionTestUtils.setField(payer, "rowId", 10L);
        dp.addParticipant(payer);
        DutchPayParticipant p1 = DutchPayParticipant.create(dp, null, "A", 10_000L, false);
        ReflectionTestUtils.setField(p1, "rowId", 11L);
        DutchPayParticipant p2 = DutchPayParticipant.create(dp, null, "B", 10_000L, false);
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
                        new DutchPayServiceDto.ParticipantCommand(
            null,null, "A", 3_000L, true),
                        new DutchPayServiceDto.ParticipantCommand(
            null,null, "B", 3_000L, false),
                        new DutchPayServiceDto.ParticipantCommand(
            null,null, "C", 4_000L, false)));

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
                        new DutchPayServiceDto.ParticipantCommand(
            null,null, "A", 4_000L, true),
                        new DutchPayServiceDto.ParticipantCommand(
            null,null, "B", 4_000L, false)));

        var info = sut.createDutchPay(cmd); // 예외 없이 성공

        long sum = info.participants().stream()
                .mapToLong(DutchPayServiceDto.ParticipantInfo::amount).sum();
        assertThat(sum).isEqualTo(8_000L);            // 합 8,000
        assertThat(info.totalAmount()).isEqualTo(10_000L); // total 10,000 (불일치 허용)
    }

    @Nested
    @DisplayName("참가자 수정 — rowId 로 맞춰 정산 표시를 지킨다")
    class ParticipantSync {

        private DutchPayServiceDto.UpdateCommand updateCmd(
                List<DutchPayServiceDto.ParticipantCommand> participants) {
            return new DutchPayServiceDto.UpdateCommand(
                "회식", null, 100_000L, "KRW", SplitMethod.EQUAL,
                LocalDate.of(2026, 8, 1), participants);
        }

        @Test
        @DisplayName("금액만 고쳐도 이미 체크한 정산 완료가 풀리지 않는다")
        void keepsPaidFlagOnUpdate() {
            // 4명이 나눠 낸 회식비에서 한 명이 이미 입금해 체크해 뒀다.
            User u = user(USER_ID);
            DutchPay dp = DutchPay.createDutchPay(u, null, "회식", null, 200_000L, "KRW",
                SplitMethod.EQUAL, LocalDate.of(2026, 8, 1));
            DutchPayParticipant paid = DutchPayParticipant.create(dp, null, "김철수", 50_000L, true);
            ReflectionTestUtils.setField(paid, "rowId", 77L);
            paid.markPaid();
            dp.addParticipant(paid);
            given(dutchPayRepository.findById(1L)).willReturn(Optional.of(dp));
            given(dutchPayRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            // 금액만 40,000 으로 고쳐 저장 — rowId 를 함께 보낸다.
            sut.updateDutchPay(1L, USER_ID, updateCmd(List.of(
                new DutchPayServiceDto.ParticipantCommand(77L, null, "김철수", 40_000L, true))));

            assertThat(paid.getIsPaid()).isEqualTo(YNType.Y);
            assertThat(paid.getPaidAt()).isNotNull();
            assertThat(paid.getAmount()).isEqualTo(40_000L);
            assertThat(paid.getIsDeleted()).isEqualTo(YNType.N);
        }

        @Test
        @DisplayName("목록에서 빠진 참가자는 지워진다")
        void removesMissingParticipant() {
            User u = user(USER_ID);
            DutchPay dp = DutchPay.createDutchPay(u, null, "회식", null, 100_000L, "KRW",
                SplitMethod.EQUAL, LocalDate.of(2026, 8, 1));
            DutchPayParticipant gone = DutchPayParticipant.create(dp, null, "박영희", 50_000L, true);
            ReflectionTestUtils.setField(gone, "rowId", 78L);
            dp.addParticipant(gone);
            given(dutchPayRepository.findById(1L)).willReturn(Optional.of(dp));
            given(dutchPayRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            sut.updateDutchPay(1L, USER_ID, updateCmd(List.of(
                new DutchPayServiceDto.ParticipantCommand(null, null, "김철수", 100_000L, true))));

            assertThat(gone.getIsDeleted()).isEqualTo(YNType.Y);
        }

        /**
         * 한 정산 안의 활성 참가자 이름이 DB UNIQUE 로 묶이면 이 순서가 정합성의 전부다 —
         * 하이버네이트는 한 플러시에서 INSERT 를 UPDATE 보다 먼저 낸다.
         */
        @Test
        @DisplayName("빠진 사람 자리에 같은 이름을 새로 넣는다 — 삭제가 INSERT 보다 먼저 나간다")
        void removalIsFlushedBeforeInsert() {
            User u = user(USER_ID);
            DutchPay dp = DutchPay.createDutchPay(u, null, "회식", null, 100_000L, "KRW",
                SplitMethod.EQUAL, LocalDate.of(2026, 8, 1));
            DutchPayParticipant gone = DutchPayParticipant.create(dp, null, "김철수", 50_000L, true);
            ReflectionTestUtils.setField(gone, "rowId", 78L);
            dp.addParticipant(gone);
            given(dutchPayRepository.findById(1L)).willReturn(Optional.of(dp));
            given(dutchPayRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            List<List<String>> activeNamesPerFlush = new ArrayList<>();
            willAnswer(inv -> {
                activeNamesPerFlush.add(dp.getActiveParticipants().stream()
                    .map(DutchPayParticipant::getParticipantName).toList());
                return null;
            }).given(dutchPayRepository).flush();

            // 같은 이름으로 다시 넣는다(rowId 없음 = 신규).
            sut.updateDutchPay(1L, USER_ID, updateCmd(List.of(
                new DutchPayServiceDto.ParticipantCommand(null, null, "김철수", 100_000L, true))));

            assertThat(gone.getIsDeleted()).isEqualTo(YNType.Y);
            // 첫 flush 시점엔 옛 '김철수' 는 이미 지워졌고 새 행은 아직 없다 — 겹치는 순간이 없다.
            assertThat(activeNamesPerFlush.get(0)).isEmpty();
            assertThat(activeNamesPerFlush.get(1)).containsExactly("김철수");
        }

        @Test
        @DisplayName("두 사람 이름 맞바꾸기 — 최종 이름을 쓰기 전에 임시값으로 비켜 둔다")
        void swappingNamesParksBeforeApplying() {
            User u = user(USER_ID);
            DutchPay dp = DutchPay.createDutchPay(u, null, "회식", null, 100_000L, "KRW",
                SplitMethod.EQUAL, LocalDate.of(2026, 8, 1));
            DutchPayParticipant a = DutchPayParticipant.create(dp, null, "김철수", 50_000L, true);
            DutchPayParticipant b = DutchPayParticipant.create(dp, null, "박영희", 50_000L, false);
            ReflectionTestUtils.setField(a, "rowId", 81L);
            ReflectionTestUtils.setField(b, "rowId", 82L);
            dp.addParticipant(a);
            dp.addParticipant(b);
            given(dutchPayRepository.findById(1L)).willReturn(Optional.of(dp));
            given(dutchPayRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            List<List<String>> namesPerFlush = new ArrayList<>();
            willAnswer(inv -> {
                namesPerFlush.add(dp.getActiveParticipants().stream()
                    .map(DutchPayParticipant::getParticipantName).toList());
                return null;
            }).given(dutchPayRepository).flush();

            sut.updateDutchPay(1L, USER_ID, updateCmd(List.of(
                new DutchPayServiceDto.ParticipantCommand(81L, null, "박영희", 50_000L, true),
                new DutchPayServiceDto.ParticipantCommand(82L, null, "김철수", 50_000L, false))));

            // 첫 flush 에서는 둘 다 임시값이라 서로 부딪히지 않는다.
            assertThat(namesPerFlush.get(0)).containsExactly(" tmp:81", " tmp:82");
            assertThat(namesPerFlush.get(1)).containsExactly("박영희", "김철수");
            assertThat(a.getParticipantName()).isEqualTo("박영희");
            assertThat(b.getParticipantName()).isEqualTo("김철수");
        }

        @Test
        @DisplayName("이름을 안 바꾸는 저장은 임시값을 거치지 않는다 — 쓸데없는 UPDATE 를 만들지 않는다")
        void unchangedNameIsNotParked() {
            User u = user(USER_ID);
            DutchPay dp = DutchPay.createDutchPay(u, null, "회식", null, 100_000L, "KRW",
                SplitMethod.EQUAL, LocalDate.of(2026, 8, 1));
            DutchPayParticipant a = DutchPayParticipant.create(dp, null, "김철수", 50_000L, true);
            ReflectionTestUtils.setField(a, "rowId", 91L);
            dp.addParticipant(a);
            given(dutchPayRepository.findById(1L)).willReturn(Optional.of(dp));
            given(dutchPayRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            List<String> nameAtFirstFlush = new ArrayList<>();
            willAnswer(inv -> {
                if (nameAtFirstFlush.isEmpty()) nameAtFirstFlush.add(a.getParticipantName());
                return null;
            }).given(dutchPayRepository).flush();

            sut.updateDutchPay(1L, USER_ID, updateCmd(List.of(
                new DutchPayServiceDto.ParticipantCommand(91L, null, "김철수", 40_000L, true))));

            assertThat(nameAtFirstFlush).containsExactly("김철수");
            assertThat(a.getAmount()).isEqualTo(40_000L);
        }
    }

    @Nested
    @DisplayName("참가자 이름 중복 — 스코프는 사용자가 아니라 정산 건이다")
    class ParticipantDuplicateName {

        private DutchPayServiceDto.CreateCommand createCmd(
                List<DutchPayServiceDto.ParticipantCommand> participants) {
            return new DutchPayServiceDto.CreateCommand(
                    USER_ID, null, "점심", null, 10_000L, "KRW", SplitMethod.CUSTOM,
                    LocalDate.of(2026, 6, 1), participants);
        }

        /** 종전엔 userRowId 유무로 검사 갈래가 갈려 이 조합이 <b>둘 다 통과</b>했다. */
        @Test
        @DisplayName("등록 사용자 참가자와 이름만 참가자가 같은 이름이면 거부 — 갈라진 검사가 만든 구멍")
        void rejectsRegisteredAndNameOnlyWithSameName() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

            assertThatThrownBy(() -> sut.createDutchPay(createCmd(List.of(
                    new DutchPayServiceDto.ParticipantCommand(null, 50L, "철수", 5_000L, true),
                    new DutchPayServiceDto.ParticipantCommand(null, null, "철수", 5_000L, false)))))
                    .isInstanceOf(InvalidValueException.class)
                    .extracting(e -> ((InvalidValueException) e).getErrorCode())
                    .isEqualTo(DeskErrorCode.DUTCH_PAY_DUPLICATE_PARTICIPANT);
        }

        @Test
        @DisplayName("끝공백·대소문자만 다른 이름도 거부 — DB 콜레이션은 같은 값으로 본다")
        void rejectsNamesThatDifferOnlyBySpacingOrCase() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

            assertThatThrownBy(() -> sut.createDutchPay(createCmd(List.of(
                    new DutchPayServiceDto.ParticipantCommand(null, null, "철수", 5_000L, true),
                    new DutchPayServiceDto.ParticipantCommand(null, null, "철수 ", 5_000L, false)))))
                    .isInstanceOf(InvalidValueException.class);

            assertThatThrownBy(() -> sut.createDutchPay(createCmd(List.of(
                    new DutchPayServiceDto.ParticipantCommand(null, null, "Kim", 5_000L, true),
                    new DutchPayServiceDto.ParticipantCommand(null, null, "kim", 5_000L, false)))))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("이름 앞뒤 공백은 저장 전에 잘린다 — 저장 값의 대소문자는 그대로 둔다")
        void trimsNamesButKeepsCase() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
            given(dutchPayRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            var info = sut.createDutchPay(createCmd(List.of(
                    new DutchPayServiceDto.ParticipantCommand(null, null, "  Kim 철수 ", 10_000L, true))));

            assertThat(info.participants()).extracting(p -> p.participantName())
                    .containsExactly("Kim 철수");
        }

        @Test
        @DisplayName("빈 참가자 이름은 400 으로 거절한다 — NOT NULL 위반 500 이 아니라")
        void rejectsBlankParticipantName() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

            assertThatThrownBy(() -> sut.createDutchPay(createCmd(List.of(
                    new DutchPayServiceDto.ParticipantCommand(null, 50L, "  ", 5_000L, true)))))
                    .isInstanceOf(InvalidValueException.class)
                    .extracting(e -> ((InvalidValueException) e).getErrorCode())
                    .isEqualTo(DeskErrorCode.INVALID_INPUT);
        }

        @Test
        @DisplayName("유니크 위반(동시 저장 경쟁)은 500 이 아니라 409 로 나간다")
        void translatesConstraintViolation() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
            given(dutchPayRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
            willThrow(new DataIntegrityViolationException("UK_dutch_pay_participant_pay_active_name"))
                    .given(dutchPayRepository).flush();

            assertThatThrownBy(() -> sut.createDutchPay(createCmd(List.of(
                    new DutchPayServiceDto.ParticipantCommand(null, null, "철수", 10_000L, true)))))
                    .isInstanceOf(InvalidValueException.class)
                    .extracting(e -> ((InvalidValueException) e).getErrorCode())
                    .isEqualTo(DeskErrorCode.DUTCH_PAY_DUPLICATE_PARTICIPANT);
        }
    }

    // === 결제자 구분 ===

    private DutchPayServiceDto.CreateCommand createWith(
            List<DutchPayServiceDto.ParticipantCommand> participants) {
        return new DutchPayServiceDto.CreateCommand(
                USER_ID, null, "회식", null, 30_000L, "KRW", SplitMethod.EQUAL,
                LocalDate.of(2026, 6, 1), participants);
    }

    @Test
    @DisplayName("createDutchPay — 결제자 표시가 없으면 첫 사람이 결제자(구버전 앱 호환)")
    void createFallsBackToFirstParticipantAsPayer() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        // isPayer 를 아예 모르는 구버전 클라이언트 — null 로 온다
        var info = sut.createDutchPay(createWith(List.of(
                new DutchPayServiceDto.ParticipantCommand(null, null, "A", 10_000L, null),
                new DutchPayServiceDto.ParticipantCommand(null, null, "B", 10_000L, null))));

        // 여기서 막으면 앱을 안 올린 사용자가 정산을 아예 못 만든다
        assertThat(info.participants())
                .filteredOn(DutchPayServiceDto.ParticipantInfo::isPayer)
                .extracting(DutchPayServiceDto.ParticipantInfo::participantName)
                .containsExactly("A");
    }

    @Test
    @DisplayName("createDutchPay — 결제자가 둘이면 거부(화면마다 다른 사람을 결제자로 그린다)")
    void createRejectsWhenMultiplePayers() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        var cmd = createWith(List.of(
                new DutchPayServiceDto.ParticipantCommand(null, null, "A", 10_000L, true),
                new DutchPayServiceDto.ParticipantCommand(null, null, "B", 10_000L, true)));

        assertThatThrownBy(() -> sut.createDutchPay(cmd))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("createDutchPay — 결제자가 첫 번째가 아니어도 그대로 저장된다(순서로 추측하지 않는다)")
    void payerIsStoredNotInferredFromOrder() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        // 두 번째 사람이 결제했다 — 친구가 계산하고 내가 갚는 흔한 경우
        var info = sut.createDutchPay(createWith(List.of(
                new DutchPayServiceDto.ParticipantCommand(null, null, "A", 10_000L, false),
                new DutchPayServiceDto.ParticipantCommand(null, null, "B", 10_000L, true),
                new DutchPayServiceDto.ParticipantCommand(null, null, "C", 10_000L, false))));

        assertThat(info.participants())
                .filteredOn(DutchPayServiceDto.ParticipantInfo::isPayer)
                .extracting(DutchPayServiceDto.ParticipantInfo::participantName)
                .containsExactly("B");
    }

    @Test
    @DisplayName("정산 완료 — 결제자는 빼고 본다(결제자는 갚을 게 없어 체크할 UI 자체가 없다)")
    void settlementIgnoresPayer() {
        DutchPay dp = DutchPay.createDutchPay(user(USER_ID), null, "회식", null, 20_000L, "KRW",
                SplitMethod.CUSTOM, LocalDate.of(2026, 6, 1));
        ReflectionTestUtils.setField(dp, "rowId", 5L);
        DutchPayParticipant payer = DutchPayParticipant.create(dp, null, "결제자", 10_000L, true);
        ReflectionTestUtils.setField(payer, "rowId", 10L);
        DutchPayParticipant debtor = DutchPayParticipant.create(dp, null, "갚을사람", 10_000L, false);
        ReflectionTestUtils.setField(debtor, "rowId", 11L);
        dp.addParticipant(payer);
        dp.addParticipant(debtor);
        given(dutchPayRepository.findById(5L)).willReturn(Optional.of(dp));

        var info = sut.markParticipantPaid(5L, USER_ID, 11L);

        // 결제자는 미납 상태로 남아 있지만 완료다 — 예전엔 결제자까지 체크돼야 완료였다
        assertThat(info.isSettled()).isTrue();
        assertThat(payer.getIsPaid()).isEqualTo(YNType.N);
    }

    @Test
    @DisplayName("전체 정산 — 결제자는 납부 처리하지 않는다")
    void settleAllSkipsPayer() {
        DutchPay dp = DutchPay.createDutchPay(user(USER_ID), null, "회식", null, 20_000L, "KRW",
                SplitMethod.CUSTOM, LocalDate.of(2026, 6, 1));
        ReflectionTestUtils.setField(dp, "rowId", 5L);
        DutchPayParticipant payer = DutchPayParticipant.create(dp, null, "결제자", 10_000L, true);
        DutchPayParticipant debtor = DutchPayParticipant.create(dp, null, "갚을사람", 10_000L, false);
        dp.addParticipant(payer);
        dp.addParticipant(debtor);
        given(dutchPayRepository.findById(5L)).willReturn(Optional.of(dp));
        given(dutchPayRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        sut.settleAll(5L, USER_ID);

        assertThat(debtor.getIsPaid()).isEqualTo(YNType.Y);
        assertThat(payer.getIsPaid()).isEqualTo(YNType.N);
    }
}
