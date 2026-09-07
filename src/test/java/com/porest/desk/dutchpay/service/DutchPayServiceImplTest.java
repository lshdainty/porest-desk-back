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
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.support.exception.ConstraintViolations;
import org.springframework.dao.DataIntegrityViolationException;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

        /**
         * 활성 결제자가 DB UNIQUE 로 묶이면 <b>이 순서가 정합성의 전부다</b>. UPDATE 는 한 문장씩
         * 나가므로 강등과 승격이 같은 플러시에 있으면 어느 쪽을 먼저 내도 잠깐 결제자가 둘이 된다.
         *
         * <p>H2 테스트 스키마에는 그 UNIQUE 가 없다(생성 컬럼을 낀 제약이라 JPA 로 선언할 수 없고,
         * {@code ddl-auto: create-drop} 은 엔티티만 보고 만든다). 그래서 <b>실제 1062 를 낼 수 있는
         * 테스트가 없다</b> — 대신 플러시마다 활성 결제자 Y 를 세어 중간 상태를 붙든다.
         * 이름 파킹을 고정한 {@link #swappingNamesParksBeforeApplying} 와 같은 방식이다.
         *
         * <p>되돌려 보는 법(네거티브 컨트롤): 서비스 ② 의 {@code parkPayerForHandover()} 루프를
         * 지우면 스냅샷이 {@code [0, 1]} 이 아니라 {@code [1, 1]} 이 되어 곧바로 깨진다.
         */
        @Test
        @DisplayName("결제자를 다른 사람에게 넘겨도 중간에 둘이 되지 않는다 — [1명]→[0명]→[1명]")
        void payerHandoverNeverHasTwoPayersAtOnce() {
            User u = user(USER_ID);
            DutchPay dp = DutchPay.createDutchPay(u, null, "회식", null, 100_000L, "KRW",
                SplitMethod.EQUAL, LocalDate.of(2026, 8, 1));
            DutchPayParticipant oldPayer = DutchPayParticipant.create(dp, null, "김철수", 50_000L, true);
            DutchPayParticipant newPayer = DutchPayParticipant.create(dp, null, "박영희", 50_000L, false);
            ReflectionTestUtils.setField(oldPayer, "rowId", 101L);
            ReflectionTestUtils.setField(newPayer, "rowId", 102L);
            dp.addParticipant(oldPayer);
            dp.addParticipant(newPayer);
            given(dutchPayRepository.findById(1L)).willReturn(Optional.of(dp));
            given(dutchPayRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            List<Long> payerCountPerFlush = activePayerCountPerFlush(dp);

            // "위 사람을 결제자로" — 화면에서 결제자 라디오만 옮긴 저장.
            sut.updateDutchPay(1L, USER_ID, updateCmd(List.of(
                new DutchPayServiceDto.ParticipantCommand(101L, null, "김철수", 50_000L, false),
                new DutchPayServiceDto.ParticipantCommand(102L, null, "박영희", 50_000L, true))));

            assertThat(payerCountPerFlush).containsExactly(0L, 1L);
            assertThat(oldPayer.isPayer()).isFalse();
            assertThat(newPayer.isPayer()).isTrue();
        }

        /** 승격 대상이 <b>신규 행</b>이어도 같다 — INSERT 는 강등 UPDATE 보다 먼저 나갈 수 있다. */
        @Test
        @DisplayName("신규 참가자를 결제자로 올려도 중간에 둘이 되지 않는다")
        void promotingNewParticipantNeverHasTwoPayersAtOnce() {
            User u = user(USER_ID);
            DutchPay dp = DutchPay.createDutchPay(u, null, "회식", null, 100_000L, "KRW",
                SplitMethod.EQUAL, LocalDate.of(2026, 8, 1));
            DutchPayParticipant oldPayer = DutchPayParticipant.create(dp, null, "김철수", 100_000L, true);
            ReflectionTestUtils.setField(oldPayer, "rowId", 101L);
            dp.addParticipant(oldPayer);
            given(dutchPayRepository.findById(1L)).willReturn(Optional.of(dp));
            given(dutchPayRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            List<Long> payerCountPerFlush = activePayerCountPerFlush(dp);

            sut.updateDutchPay(1L, USER_ID, updateCmd(List.of(
                new DutchPayServiceDto.ParticipantCommand(101L, null, "김철수", 50_000L, false),
                new DutchPayServiceDto.ParticipantCommand(null, null, "박영희", 50_000L, true))));

            assertThat(payerCountPerFlush).containsExactly(0L, 1L);
            assertThat(oldPayer.isPayer()).isFalse();
            assertThat(dp.getPayer().getParticipantName()).isEqualTo("박영희");
        }

        /**
         * QA #80 — <b>금액만 고쳤는데 결제자가 옮겨가던</b> 자리.
         *
         * <p>수정이 생성 경로의 폴백("표시가 없으면 첫 사람")을 그대로 쓰고 있었다. 참가자 순서는
         * 클라이언트가 정하므로 정렬만 달라져도 결제자가 따라 움직였다.
         *
         * <p>되돌려 보는 법(네거티브 컨트롤): {@code resolveUpdatePayerIndex} 의 "기존 결제자 유지"
         * 블록을 지워 {@code resolveCreatePayerIndex} 처럼 0 을 돌려주게 하면 곧바로 깨진다.
         */
        @Test
        @DisplayName("결제자 표시 없이 금액만 고치면 결제자는 그대로다 — 첫 사람으로 옮기지 않는다")
        void amountOnlyUpdateKeepsStoredPayer() {
            User u = user(USER_ID);
            DutchPay dp = DutchPay.createDutchPay(u, null, "회식", null, 100_000L, "KRW",
                SplitMethod.EQUAL, LocalDate.of(2026, 8, 1));
            DutchPayParticipant first = DutchPayParticipant.create(dp, null, "김철수", 50_000L, false);
            DutchPayParticipant payer = DutchPayParticipant.create(dp, null, "박영희", 50_000L, true);
            ReflectionTestUtils.setField(first, "rowId", 101L);
            ReflectionTestUtils.setField(payer, "rowId", 102L);
            dp.addParticipant(first);
            dp.addParticipant(payer);
            given(dutchPayRepository.findById(1L)).willReturn(Optional.of(dp));
            given(dutchPayRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            // isPayer 를 아예 안 실은 저장 — 금액 두 줄만 고쳤다.
            sut.updateDutchPay(1L, USER_ID, updateCmd(List.of(
                new DutchPayServiceDto.ParticipantCommand(101L, null, "김철수", 40_000L, null),
                new DutchPayServiceDto.ParticipantCommand(102L, null, "박영희", 60_000L, null))));

            assertThat(first.isPayer()).isFalse();
            assertThat(payer.isPayer()).isTrue();
            assertThat(first.getAmount()).isEqualTo(40_000L);
            assertThat(payer.getAmount()).isEqualTo(60_000L);
        }

        /** 순서만 바꿔 보낸 저장도 마찬가지다 — 결제자는 목록 위치가 아니라 저장된 값이다. */
        @Test
        @DisplayName("참가자 순서만 바뀐 저장도 결제자를 옮기지 않는다")
        void reorderingDoesNotMovePayer() {
            User u = user(USER_ID);
            DutchPay dp = DutchPay.createDutchPay(u, null, "회식", null, 100_000L, "KRW",
                SplitMethod.EQUAL, LocalDate.of(2026, 8, 1));
            DutchPayParticipant payer = DutchPayParticipant.create(dp, null, "김철수", 50_000L, true);
            DutchPayParticipant other = DutchPayParticipant.create(dp, null, "박영희", 50_000L, false);
            ReflectionTestUtils.setField(payer, "rowId", 101L);
            ReflectionTestUtils.setField(other, "rowId", 102L);
            dp.addParticipant(payer);
            dp.addParticipant(other);
            given(dutchPayRepository.findById(1L)).willReturn(Optional.of(dp));
            given(dutchPayRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            // 결제자가 두 번째로 내려간 목록 — 표시는 여전히 없다.
            sut.updateDutchPay(1L, USER_ID, updateCmd(List.of(
                new DutchPayServiceDto.ParticipantCommand(102L, null, "박영희", 50_000L, null),
                new DutchPayServiceDto.ParticipantCommand(101L, null, "김철수", 50_000L, null))));

            assertThat(payer.isPayer()).isTrue();
            assertThat(other.isPayer()).isFalse();
        }

        /**
         * 지킬 결제자가 없어진 경우 — 생성과 같은 규칙으로 되돌아간다.
         *
         * <p>결제자를 0명으로 두면 {@code getDebtors()} 가 전원을 돌려줘 전체 정산이 결제자까지
         * 납부 처리하고, 화면은 화면대로 첫 사람을 결제자처럼 그린다. 지킬 것이 없을 때는
         * 추측이 아니라 규칙이다.
         */
        @Test
        @DisplayName("기존 결제자가 목록에서 빠지고 표시도 없으면 첫 사람이 결제자가 된다")
        void fallsBackToFirstWhenStoredPayerIsRemoved() {
            User u = user(USER_ID);
            DutchPay dp = DutchPay.createDutchPay(u, null, "회식", null, 100_000L, "KRW",
                SplitMethod.EQUAL, LocalDate.of(2026, 8, 1));
            DutchPayParticipant payer = DutchPayParticipant.create(dp, null, "김철수", 50_000L, true);
            DutchPayParticipant other = DutchPayParticipant.create(dp, null, "박영희", 50_000L, false);
            ReflectionTestUtils.setField(payer, "rowId", 101L);
            ReflectionTestUtils.setField(other, "rowId", 102L);
            dp.addParticipant(payer);
            dp.addParticipant(other);
            given(dutchPayRepository.findById(1L)).willReturn(Optional.of(dp));
            given(dutchPayRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            List<Long> payerCountPerFlush = activePayerCountPerFlush(dp);

            sut.updateDutchPay(1L, USER_ID, updateCmd(List.of(
                new DutchPayServiceDto.ParticipantCommand(102L, null, "박영희", 100_000L, null))));

            assertThat(payer.getIsDeleted()).isEqualTo(YNType.Y);
            assertThat(other.isPayer()).isTrue();
            // 지워진 행은 활성 집합에서 빠지므로 유일성에서도 빠진다 — 중간에 둘이 되지 않는다.
            assertThat(payerCountPerFlush).containsExactly(0L, 1L);
        }

        /** 지워지는 행의 {@code is_payer} 는 그대로 둔다 — "누가 냈는지" 는 기록으로 남는다. */
        @Test
        @DisplayName("삭제된 결제자 행의 결제자 표시는 지우지 않는다")
        void deletedPayerRowKeepsItsFlag() {
            User u = user(USER_ID);
            DutchPay dp = DutchPay.createDutchPay(u, null, "회식", null, 100_000L, "KRW",
                SplitMethod.EQUAL, LocalDate.of(2026, 8, 1));
            DutchPayParticipant payer = DutchPayParticipant.create(dp, null, "김철수", 100_000L, true);
            ReflectionTestUtils.setField(payer, "rowId", 101L);
            dp.addParticipant(payer);
            given(dutchPayRepository.findById(1L)).willReturn(Optional.of(dp));
            given(dutchPayRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            sut.updateDutchPay(1L, USER_ID, updateCmd(List.of(
                new DutchPayServiceDto.ParticipantCommand(null, null, "박영희", 100_000L, true))));

            assertThat(payer.getIsDeleted()).isEqualTo(YNType.Y);
            assertThat(payer.getIsPayer()).isEqualTo(YNType.Y);
        }

        @Test
        @DisplayName("수정에서도 결제자가 둘이면 거부한다")
        void updateRejectsWhenMultiplePayersMarked() {
            User u = user(USER_ID);
            DutchPay dp = DutchPay.createDutchPay(u, null, "회식", null, 100_000L, "KRW",
                SplitMethod.EQUAL, LocalDate.of(2026, 8, 1));
            DutchPayParticipant a = DutchPayParticipant.create(dp, null, "김철수", 50_000L, true);
            DutchPayParticipant b = DutchPayParticipant.create(dp, null, "박영희", 50_000L, false);
            ReflectionTestUtils.setField(a, "rowId", 101L);
            ReflectionTestUtils.setField(b, "rowId", 102L);
            dp.addParticipant(a);
            dp.addParticipant(b);
            given(dutchPayRepository.findById(1L)).willReturn(Optional.of(dp));

            var cmd = updateCmd(List.of(
                new DutchPayServiceDto.ParticipantCommand(101L, null, "김철수", 50_000L, true),
                new DutchPayServiceDto.ParticipantCommand(102L, null, "박영희", 50_000L, true)));

            assertThatThrownBy(() -> sut.updateDutchPay(1L, USER_ID, cmd))
                .isInstanceOf(InvalidValueException.class)
                .extracting(e -> ((InvalidValueException) e).getErrorCode())
                .isEqualTo(DeskErrorCode.DUTCH_PAY_INVALID_PAYER);
        }

        /**
         * 결제자 UNIQUE 가 걸렸을 때 <b>이름 중복이라고 답하지 않는다</b>.
         *
         * <p>한 테이블에 UNIQUE 가 둘이라(활성 이름·활성 결제자) 구분하지 않으면 결제자가 부딪힌
         * 요청이 있지도 않은 "같은 참가자를 중복으로 추가할 수 없어요" 를 듣는다 — QA #81 이
         * 공통 핸들러에서 잡은 것과 같은 종류의 오역이다.
         *
         * <p>되돌려 보는 법(네거티브 컨트롤): {@code flushOrRejectDuplicate} 의 제약 이름 분기를
         * 지우면 DUTCH_007 이 DUTCH_005 로 바뀌어 깨진다.
         */
        @Test
        @DisplayName("결제자 유일성 위반은 참가자 이름 중복이 아니라 결제자 충돌로 답한다")
        void payerUniqueViolationIsNotReportedAsDuplicateName() {
            User u = user(USER_ID);
            DutchPay dp = DutchPay.createDutchPay(u, null, "회식", null, 100_000L, "KRW",
                SplitMethod.EQUAL, LocalDate.of(2026, 8, 1));
            DutchPayParticipant a = DutchPayParticipant.create(dp, null, "김철수", 100_000L, true);
            ReflectionTestUtils.setField(a, "rowId", 101L);
            dp.addParticipant(a);
            given(dutchPayRepository.findById(1L)).willReturn(Optional.of(dp));
            // 첫 flush(③)는 통과하고 마지막 flush 에서 DB 가 거절하는 모양.
            willAnswer(inv -> null).willThrow(
                    ConstraintViolations.unique("UK_dutch_pay_participant_active_payer"))
                .given(dutchPayRepository).flush();

            var cmd = updateCmd(List.of(
                new DutchPayServiceDto.ParticipantCommand(101L, null, "김철수", 100_000L, true)));

            assertThatThrownBy(() -> sut.updateDutchPay(1L, USER_ID, cmd))
                .isInstanceOf(InvalidValueException.class)
                .extracting(e -> ((InvalidValueException) e).getErrorCode())
                .isEqualTo(DeskErrorCode.DUTCH_PAY_PAYER_CONFLICT);
        }

        /** 같은 자리에서 <b>이름</b> UNIQUE 가 걸리면 답은 그대로 이름 중복이다. */
        @Test
        @DisplayName("이름 유일성 위반은 여전히 참가자 이름 중복으로 답한다")
        void nameUniqueViolationStillReportsDuplicateName() {
            User u = user(USER_ID);
            DutchPay dp = DutchPay.createDutchPay(u, null, "회식", null, 100_000L, "KRW",
                SplitMethod.EQUAL, LocalDate.of(2026, 8, 1));
            DutchPayParticipant a = DutchPayParticipant.create(dp, null, "김철수", 100_000L, true);
            ReflectionTestUtils.setField(a, "rowId", 101L);
            dp.addParticipant(a);
            given(dutchPayRepository.findById(1L)).willReturn(Optional.of(dp));
            willAnswer(inv -> null).willThrow(
                    ConstraintViolations.unique("UK_dutch_pay_participant_active_name"))
                .given(dutchPayRepository).flush();

            var cmd = updateCmd(List.of(
                new DutchPayServiceDto.ParticipantCommand(101L, null, "김철수", 100_000L, true)));

            assertThatThrownBy(() -> sut.updateDutchPay(1L, USER_ID, cmd))
                .isInstanceOf(InvalidValueException.class)
                .extracting(e -> ((InvalidValueException) e).getErrorCode())
                .isEqualTo(DeskErrorCode.DUTCH_PAY_DUPLICATE_PARTICIPANT);
        }

        /** participants 자체를 안 보낸 요청 — 서비스는 참가자를 손대지 않는다. */
        @Test
        @DisplayName("participants 가 null 이면 참가자를 건드리지 않는다")
        void nullParticipantsLeavesEveryoneAlone() {
            User u = user(USER_ID);
            DutchPay dp = DutchPay.createDutchPay(u, null, "회식", null, 100_000L, "KRW",
                SplitMethod.EQUAL, LocalDate.of(2026, 8, 1));
            DutchPayParticipant a = DutchPayParticipant.create(dp, null, "김철수", 50_000L, true);
            DutchPayParticipant b = DutchPayParticipant.create(dp, null, "박영희", 50_000L, false);
            ReflectionTestUtils.setField(a, "rowId", 101L);
            ReflectionTestUtils.setField(b, "rowId", 102L);
            dp.addParticipant(a);
            dp.addParticipant(b);
            given(dutchPayRepository.findById(1L)).willReturn(Optional.of(dp));
            given(dutchPayRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            sut.updateDutchPay(1L, USER_ID, updateCmd(null));

            assertThat(dp.getActiveParticipants()).containsExactly(a, b);
            assertThat(a.isPayer()).isTrue();
        }

        /** 플러시가 일어난 시점마다 활성 결제자(Y) 수를 찍어 둔다. */
        private List<Long> activePayerCountPerFlush(DutchPay dp) {
            List<Long> counts = new ArrayList<>();
            willAnswer(inv -> {
                counts.add(dp.getActiveParticipants().stream()
                    .filter(DutchPayParticipant::isPayer).count());
                return null;
            }).given(dutchPayRepository).flush();
            return counts;
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
            willThrow(ConstraintViolations.unique("UK_dutch_pay_participant_pay_active_name"))
                    .given(dutchPayRepository).flush();

            assertThatThrownBy(() -> sut.createDutchPay(createCmd(List.of(
                    new DutchPayServiceDto.ParticipantCommand(null, null, "철수", 10_000L, true)))))
                    .isInstanceOf(InvalidValueException.class)
                    .extracting(e -> ((InvalidValueException) e).getErrorCode())
                    .isEqualTo(DeskErrorCode.DUTCH_PAY_DUPLICATE_PARTICIPANT);
        }
        /**
         * QA #81 — <b>UNIQUE 가 아닌 위반은 이 도메인이 손대지 않는다.</b>
         *
         * <p>종전엔 여기서 {@code DataIntegrityViolationException} 을 종류와 무관하게 전부
         * "이름이 중복돼요" 로 번역했다. 그러면 값을 하나 빼먹고 보낸 요청이 <b>있지도 않은
         * 중복</b>을 이유로 거절당한다. 그런 위반은 그대로 올려 공통 핸들러가 400 으로 답하게 둔다.
         *
         * <p>되돌려 보는 법(네거티브 컨트롤): 서비스의
         * {@code if (!IntegrityViolations.isUnique(e)) throw e;} 한 줄을 지우면 곧바로 깨진다.
         */
        @Test
        @DisplayName("NOT NULL 위반은 참가자 이름 중복으로 번역하지 않고 그대로 올린다")
        void doesNotTranslateNonUniqueViolation() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
            given(dutchPayRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
            willThrow(ConstraintViolations.notNull("TOTAL_AMOUNT")).given(dutchPayRepository).flush();

            assertThatThrownBy(() -> sut.createDutchPay(createCmd(List.of(
                    new DutchPayServiceDto.ParticipantCommand(null, null, "철수", 10_000L, true)))))
                    .isInstanceOf(DataIntegrityViolationException.class);
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

    /**
     * 결제자는 <b>반드시 한 명</b>이다(사용자 결정, QA 2026-09-07 #80) — 0명을 만들 수 있는 길을
     * 전부 막는다. 그 길은 셋이었다: 생성에서 아무도 안 고르기 · 수정에서 결제자를 내려놓기 ·
     * 참가자를 전원 지우기(사람이 0명이면 결제자도 0명이다).
     *
     * <p>막는 자리와 <b>안 막는 자리</b>를 가르는 것은 요청이 {@code isPayer} 를 아는지다.
     * 구버전 앱은 이 필드를 모르므로 고를 방법이 없다 — 거기까지 400 을 주면 앱을 안 올린
     * 사용자가 정산을 아예 못 만든다(스토어를 안 써서 자동 업데이트가 없고 {@code min_build}
     * 하한도 0 이다). 그래서 <b>키가 아예 없는 요청에만</b> 첫 사람 폴백을 남긴다.
     *
     * <p>되돌려 보는 법(네거티브 컨트롤): {@code requireInferablePayer} 의 본문을 지우면
     * 이 묶음의 400 기대가 전부 깨지고, {@code isPayerAware} 를 {@code false} 고정으로 바꾸면
     * 새 클라이언트의 "아무도 안 골랐다" 가 다시 조용히 첫 사람으로 저장된다.
     */
    @Nested
    @DisplayName("결제자 0명 — 만들 수 있는 길을 전부 막는다")
    class PayerRequired {

        @Test
        @DisplayName("생성 — isPayer 를 아는 요청이 아무도 안 고르면 400")
        void createRejectsWhenPayerAwareClientMarksNobody() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

            // 새 클라이언트는 안 고른 사람에게도 false 를 싣는다 — 값만 보면 구버전과 같아 보인다.
            var cmd = createWith(List.of(
                    new DutchPayServiceDto.ParticipantCommand(null, null, "A", 10_000L, false),
                    new DutchPayServiceDto.ParticipantCommand(null, null, "B", 10_000L, false)));

            assertThatThrownBy(() -> sut.createDutchPay(cmd))
                    .isInstanceOf(InvalidValueException.class)
                    .extracting(e -> ((InvalidValueException) e).getErrorCode())
                    .isEqualTo(DeskErrorCode.DUTCH_PAY_PAYER_REQUIRED);
            verify(dutchPayRepository, never()).save(any());
        }

        /**
         * 같은 요청이 키를 <b>아예 안 실으면</b> 통과한다 — 이 대비가 이 항목의 전부다.
         * ({@link DutchPayServiceImplTest#createFallsBackToFirstParticipantAsPayer} 가 그 쪽을 지킨다.)
         */
        @Test
        @DisplayName("생성 — 일부만 isPayer 를 실어도 '아는 요청' 으로 보고 400")
        void createTreatsPartiallyMarkedRequestAsPayerAware() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

            var cmd = createWith(List.of(
                    new DutchPayServiceDto.ParticipantCommand(null, null, "A", 10_000L, false),
                    new DutchPayServiceDto.ParticipantCommand(null, null, "B", 10_000L, null)));

            // 첫 사람을 추측해 저장하는 것보다 누가 냈는지 다시 묻는 편이 낫다.
            assertThatThrownBy(() -> sut.createDutchPay(cmd))
                    .isInstanceOf(InvalidValueException.class)
                    .extracting(e -> ((InvalidValueException) e).getErrorCode())
                    .isEqualTo(DeskErrorCode.DUTCH_PAY_PAYER_REQUIRED);
        }

        /**
         * 빈 배열과 {@code null} 둘 다 막는다 — 생성에서는 뜻이 같다("나눌 사람이 없다").
         * 수정에서만 {@code null} 이 "참가자는 안 건드린다" 라는 다른 뜻을 가진다.
         */
        @Test
        @DisplayName("생성 — 참가자가 0명이면 400(고를 사람 자체가 없다)")
        void createRejectsEmptyParticipants() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

            assertThatThrownBy(() -> sut.createDutchPay(createWith(List.of())))
                    .isInstanceOf(InvalidValueException.class)
                    .extracting(e -> ((InvalidValueException) e).getErrorCode())
                    .isEqualTo(DeskErrorCode.DUTCH_PAY_PAYER_REQUIRED);
            assertThatThrownBy(() -> sut.createDutchPay(createWith(null)))
                    .isInstanceOf(InvalidValueException.class)
                    .extracting(e -> ((InvalidValueException) e).getErrorCode())
                    .isEqualTo(DeskErrorCode.DUTCH_PAY_PAYER_REQUIRED);
            verify(dutchPayRepository, never()).save(any());
        }

        /**
         * 저장된 결제자가 목록에 그대로 있어도 <b>전원 false</b> 면 거부한다.
         *
         * <p>"표시 없음 = 안 건드림"(#315) 은 표시를 <b>안 실은</b> 요청에만 준다. 아는 요청이
         * 전원 false 로 말했는데 서버가 슬쩍 기존 결제자를 지키면, 클라이언트가 보낸 값과 저장된
         * 값이 갈려 화면과 데이터가 어긋나던 그 증상으로 되돌아간다.
         */
        @Test
        @DisplayName("수정 — 전원 false 면 기존 결제자를 지키지 않고 400")
        void updateRejectsWhenPayerAwareClientUnmarksEveryone() {
            User u = user(USER_ID);
            DutchPay dp = DutchPay.createDutchPay(u, null, "회식", null, 100_000L, "KRW",
                SplitMethod.EQUAL, LocalDate.of(2026, 8, 1));
            DutchPayParticipant payer = DutchPayParticipant.create(dp, null, "김철수", 50_000L, true);
            DutchPayParticipant other = DutchPayParticipant.create(dp, null, "박영희", 50_000L, false);
            ReflectionTestUtils.setField(payer, "rowId", 101L);
            ReflectionTestUtils.setField(other, "rowId", 102L);
            dp.addParticipant(payer);
            dp.addParticipant(other);
            given(dutchPayRepository.findById(1L)).willReturn(Optional.of(dp));

            var cmd = new DutchPayServiceDto.UpdateCommand(
                "회식", null, 100_000L, "KRW", SplitMethod.EQUAL, LocalDate.of(2026, 8, 1),
                List.of(
                    new DutchPayServiceDto.ParticipantCommand(101L, null, "김철수", 50_000L, false),
                    new DutchPayServiceDto.ParticipantCommand(102L, null, "박영희", 50_000L, false)));

            assertThatThrownBy(() -> sut.updateDutchPay(1L, USER_ID, cmd))
                    .isInstanceOf(InvalidValueException.class)
                    .extracting(e -> ((InvalidValueException) e).getErrorCode())
                    .isEqualTo(DeskErrorCode.DUTCH_PAY_PAYER_REQUIRED);
            // 저장된 결제자는 그대로다 — 거절은 아무것도 바꾸지 않는다.
            assertThat(payer.isPayer()).isTrue();
        }

        /**
         * 참가자 전원 삭제({@code participants: []})도 결제자 0명을 만든다 — 같은 자리에서 막는다.
         *
         * <p>컨트롤러 {@code @Size(min = 1)} 가 먼저 끊지만 서비스에도 둔다. 그리고 <b>끊는
         * 시점</b>이 중요하다: 요청에서 빠진 참가자를 지우는 루프보다 앞이라, 거절된 요청이
         * 참가자를 반쯤 지워 두고 나가지 않는다.
         */
        @Test
        @DisplayName("수정 — participants 빈 배열은 400이고 기존 참가자를 지우지 않는다")
        void updateRejectsEmptyParticipantsWithoutDeletingAnyone() {
            User u = user(USER_ID);
            DutchPay dp = DutchPay.createDutchPay(u, null, "회식", null, 100_000L, "KRW",
                SplitMethod.EQUAL, LocalDate.of(2026, 8, 1));
            DutchPayParticipant payer = DutchPayParticipant.create(dp, null, "김철수", 50_000L, true);
            DutchPayParticipant other = DutchPayParticipant.create(dp, null, "박영희", 50_000L, false);
            ReflectionTestUtils.setField(payer, "rowId", 101L);
            ReflectionTestUtils.setField(other, "rowId", 102L);
            dp.addParticipant(payer);
            dp.addParticipant(other);
            given(dutchPayRepository.findById(1L)).willReturn(Optional.of(dp));

            var cmd = new DutchPayServiceDto.UpdateCommand(
                "회식", null, 100_000L, "KRW", SplitMethod.EQUAL, LocalDate.of(2026, 8, 1),
                List.of());

            assertThatThrownBy(() -> sut.updateDutchPay(1L, USER_ID, cmd))
                    .isInstanceOf(InvalidValueException.class)
                    .extracting(e -> ((InvalidValueException) e).getErrorCode())
                    .isEqualTo(DeskErrorCode.DUTCH_PAY_PAYER_REQUIRED);
            assertThat(dp.getActiveParticipants()).hasSize(2);
            assertThat(payer.getIsDeleted()).isEqualTo(YNType.N);
            assertThat(other.getIsDeleted()).isEqualTo(YNType.N);
        }

        /**
         * 구버전 앱의 수정은 그대로 통과한다 — 키가 없으면 저장된 결제자를 지킨다(#315).
         * 여기가 깨지면 옛 앱이 금액 한 줄도 못 고친다.
         */
        @Test
        @DisplayName("수정 — 키를 아예 안 실은 요청은 여전히 통과하고 결제자를 지킨다")
        void updateWithoutTheFieldStillKeepsStoredPayer() {
            User u = user(USER_ID);
            DutchPay dp = DutchPay.createDutchPay(u, null, "회식", null, 100_000L, "KRW",
                SplitMethod.EQUAL, LocalDate.of(2026, 8, 1));
            DutchPayParticipant payer = DutchPayParticipant.create(dp, null, "김철수", 50_000L, true);
            DutchPayParticipant other = DutchPayParticipant.create(dp, null, "박영희", 50_000L, false);
            ReflectionTestUtils.setField(payer, "rowId", 101L);
            ReflectionTestUtils.setField(other, "rowId", 102L);
            dp.addParticipant(payer);
            dp.addParticipant(other);
            given(dutchPayRepository.findById(1L)).willReturn(Optional.of(dp));
            given(dutchPayRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            sut.updateDutchPay(1L, USER_ID, new DutchPayServiceDto.UpdateCommand(
                "회식", null, 100_000L, "KRW", SplitMethod.EQUAL, LocalDate.of(2026, 8, 1),
                List.of(
                    new DutchPayServiceDto.ParticipantCommand(101L, null, "김철수", 40_000L, null),
                    new DutchPayServiceDto.ParticipantCommand(102L, null, "박영희", 60_000L, null))));

            assertThat(payer.isPayer()).isTrue();
            assertThat(other.isPayer()).isFalse();
        }
    }
}
