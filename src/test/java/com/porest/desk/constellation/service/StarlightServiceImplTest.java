package com.porest.desk.constellation.service;

import com.porest.core.type.YNType;
import com.porest.desk.constellation.domain.Constellation;
import com.porest.desk.constellation.domain.ConstellationCollection;
import com.porest.desk.constellation.domain.ConstellationDaily;
import com.porest.desk.constellation.domain.ConstellationProfile;
import com.porest.desk.constellation.domain.TodoStarlight;
import com.porest.desk.constellation.repository.ConstellationCollectionRepository;
import com.porest.desk.constellation.repository.ConstellationDailyRepository;
import com.porest.desk.constellation.repository.ConstellationProfileRepository;
import com.porest.desk.constellation.repository.ConstellationRepository;
import com.porest.desk.constellation.repository.TodoStarlightRepository;
import com.porest.desk.constellation.type.DailyStatus;
import com.porest.desk.constellation.type.StarlightSourceType;
import com.porest.desk.memo.domain.Memo;
import com.porest.desk.todo.domain.Todo;
import com.porest.desk.todo.type.TodoPriority;
import com.porest.desk.todo.type.TodoType;
import com.porest.desk.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 별빛 적립 엔진 — 우선순위 가중, 평생 1회, 당일 회수, 수집 확정(불변), 보호 정산 규칙 검증.
 * 날짜는 코드베이스 관례(LocalDate.now())에 맞춰 상대 fixture 로 구성.
 */
@ExtendWith(MockitoExtension.class)
class StarlightServiceImplTest {

    @Mock private ConstellationRepository constellationRepository;
    @Mock private ConstellationProfileRepository profileRepository;
    @Mock private TodoStarlightRepository starlightRepository;
    @Mock private ConstellationDailyRepository dailyRepository;
    @Mock private ConstellationCollectionRepository collectionRepository;

    @InjectMocks private StarlightServiceImpl sut;

    private static final long USER_ID = 1L;
    private final LocalDate today = LocalDate.now();

    private User user() {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", USER_ID);
        return u;
    }

    private Constellation dipper() {
        Constellation c = Constellation.createConstellation(
            "dipper", "북두칠성", "Big Dipper", "국자 모양 일곱 별", "Seven bright stars", "blue", 7, "{\"pts\":[],\"edges\":[]}", 1);
        ReflectionTestUtils.setField(c, "rowId", 10L);
        return c;
    }

    private Todo task(long rowId, TodoPriority priority) {
        Todo todo = Todo.createTodo(user(), "t", "c", priority, null, today, null, TodoType.TASK);
        ReflectionTestUtils.setField(todo, "rowId", rowId);
        todo.toggleStatus(); // COMPLETED 상태로
        return todo;
    }

    private ConstellationDaily openDaily(Constellation constellation, int points) {
        ConstellationDaily daily = ConstellationDaily.open(user(), today, constellation);
        daily.addPoints(points);
        return daily;
    }

    // ── 적립 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("HIGH 완료 → 3점 적립, 오늘 관측 행에 가산")
    void earnHighPriorityThreePoints() {
        ConstellationDaily daily = openDaily(dipper(), 0);
        given(starlightRepository.existsBySourceIncludingRevoked(StarlightSourceType.TODO, 7L)).willReturn(false);
        given(dailyRepository.findByUserAndDate(USER_ID, today)).willReturn(Optional.of(daily));

        sut.onTodoStatusToggled(task(7L, TodoPriority.HIGH));

        ArgumentCaptor<TodoStarlight> captor = ArgumentCaptor.forClass(TodoStarlight.class);
        verify(starlightRepository).save(captor.capture());
        assertThat(captor.getValue().getPoints()).isEqualTo(3);
        assertThat(captor.getValue().getEarnDate()).isEqualTo(today);
        assertThat(daily.getPoints()).isEqualTo(3);
        assertThat(daily.getStatus()).isEqualTo(DailyStatus.WITHERED); // 목표(7) 미달
    }

    @Test
    @DisplayName("우선순위 가중 — MEDIUM 2점, LOW 1점")
    void earnWeights() {
        assertThat(StarlightServiceImpl.weightOf(TodoPriority.HIGH)).isEqualTo(3);
        assertThat(StarlightServiceImpl.weightOf(TodoPriority.MEDIUM)).isEqualTo(2);
        assertThat(StarlightServiceImpl.weightOf(TodoPriority.LOW)).isEqualTo(1);
    }

    @Test
    @DisplayName("NOTE 타입 완료는 별빛 대상 아님")
    void noteTypeIgnored() {
        Todo note = Todo.createTodo(user(), "n", "c", TodoPriority.HIGH, null, today, null, TodoType.NOTE);
        ReflectionTestUtils.setField(note, "rowId", 7L);
        note.toggleStatus();

        sut.onTodoStatusToggled(note);

        verify(starlightRepository, never()).save(any());
    }

    @Test
    @DisplayName("평생 1회 — 회수 이력 포함 기존 출처는 재적립 무시")
    void lifetimeOncePerSource() {
        given(starlightRepository.existsBySourceIncludingRevoked(StarlightSourceType.TODO, 7L)).willReturn(true);

        sut.onTodoStatusToggled(task(7L, TodoPriority.HIGH));

        verify(starlightRepository, never()).save(any());
        verify(dailyRepository, never()).findByUserAndDate(any(), any());
    }

    @Test
    @DisplayName("오늘 첫 적립 — 보호 정산 후 관측 행을 연다 (그날의 순환 목표 별자리)")
    void firstEarnOfDayOpensDaily() {
        Constellation c = dipper();
        given(starlightRepository.existsBySourceIncludingRevoked(StarlightSourceType.TODO, 7L)).willReturn(false);
        given(dailyRepository.findByUserAndDate(USER_ID, today)).willReturn(Optional.empty());
        given(dailyRepository.findLatestGrownDate(USER_ID)).willReturn(Optional.empty()); // reconcile no-op
        given(constellationRepository.findAllActive()).willReturn(List.of(c));
        given(dailyRepository.save(any(ConstellationDaily.class))).willAnswer(inv -> inv.getArgument(0));

        sut.onTodoStatusToggled(task(7L, TodoPriority.MEDIUM));

        ArgumentCaptor<ConstellationDaily> captor = ArgumentCaptor.forClass(ConstellationDaily.class);
        verify(dailyRepository).save(captor.capture());
        assertThat(captor.getValue().getObsDate()).isEqualTo(today);
        assertThat(captor.getValue().getConstellation()).isSameAs(c);
        assertThat(captor.getValue().getPoints()).isEqualTo(2);
    }

    // ── 수집 확정 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("목표 도달 → GROWN 확정 + 도감 수집 저장 + 보호 충전 진행")
    void reachGoalGrowsAndCollects() {
        Constellation c = dipper(); // 목표 7
        ConstellationDaily daily = openDaily(c, 5);
        ConstellationProfile profile = ConstellationProfile.createProfile(user());
        given(starlightRepository.existsBySourceIncludingRevoked(StarlightSourceType.TODO, 7L)).willReturn(false);
        given(dailyRepository.findByUserAndDate(USER_ID, today)).willReturn(Optional.of(daily));
        given(profileRepository.findByUser(USER_ID)).willReturn(Optional.of(profile));

        sut.onTodoStatusToggled(task(7L, TodoPriority.HIGH)); // 5 + 3 = 8 ≥ 7

        assertThat(daily.isGrown()).isTrue();
        ArgumentCaptor<ConstellationCollection> captor = ArgumentCaptor.forClass(ConstellationCollection.class);
        verify(collectionRepository).save(captor.capture());
        assertThat(captor.getValue().getCollectedDate()).isEqualTo(today);
        assertThat(profile.getGrownSinceCharge()).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 GROWN 인 날 추가 적립 → 수집 재발행 없음(하루 1수집)")
    void alreadyGrownNoDoubleCollect() {
        Constellation c = dipper();
        ConstellationDaily daily = openDaily(c, 7);
        daily.grow();
        given(starlightRepository.existsBySourceIncludingRevoked(StarlightSourceType.TODO, 8L)).willReturn(false);
        given(dailyRepository.findByUserAndDate(USER_ID, today)).willReturn(Optional.of(daily));

        sut.onTodoStatusToggled(task(8L, TodoPriority.LOW));

        assertThat(daily.getPoints()).isEqualTo(8);
        verify(collectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("첫 수집 시 프로필이 없으면 생성 후 진행 기록")
    void createsProfileOnFirstGrown() {
        Constellation c = dipper();
        ConstellationDaily daily = openDaily(c, 6);
        given(starlightRepository.existsBySourceIncludingRevoked(StarlightSourceType.TODO, 7L)).willReturn(false);
        given(dailyRepository.findByUserAndDate(USER_ID, today)).willReturn(Optional.of(daily));
        given(profileRepository.findByUser(USER_ID)).willReturn(Optional.empty());
        given(profileRepository.save(any(ConstellationProfile.class))).willAnswer(inv -> inv.getArgument(0));

        sut.onTodoStatusToggled(task(7L, TodoPriority.MEDIUM)); // 6 + 2 ≥ 7

        ArgumentCaptor<ConstellationProfile> captor = ArgumentCaptor.forClass(ConstellationProfile.class);
        verify(profileRepository).save(captor.capture());
        assertThat(captor.getValue().getGrownSinceCharge()).isEqualTo(1);
    }

    // ── 회수 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("당일 완료 해제 → 원장 soft 회수 + 관측 행 감점")
    void revokeSameDay() {
        Constellation c = dipper();
        ConstellationDaily daily = openDaily(c, 3);
        TodoStarlight ledger = TodoStarlight.earn(user(), StarlightSourceType.TODO, 7L, 3, today);
        given(starlightRepository.findActiveBySource(StarlightSourceType.TODO, 7L)).willReturn(Optional.of(ledger));
        given(dailyRepository.findByUserAndDate(USER_ID, today)).willReturn(Optional.of(daily));

        Todo todo = task(7L, TodoPriority.HIGH);
        todo.toggleStatus(); // COMPLETED → PENDING (해제)
        sut.onTodoStatusToggled(todo);

        assertThat(ledger.getIsDeleted()).isEqualTo(YNType.Y);
        assertThat(daily.getPoints()).isZero();
    }

    @Test
    @DisplayName("타일 적립분 해제 → 회수하지 않음 (과거 밤하늘 불변)")
    void revokeOtherDayIgnored() {
        TodoStarlight ledger = TodoStarlight.earn(user(), StarlightSourceType.TODO, 7L, 3, today.minusDays(1));
        given(starlightRepository.findActiveBySource(StarlightSourceType.TODO, 7L)).willReturn(Optional.of(ledger));

        Todo todo = task(7L, TodoPriority.HIGH);
        todo.toggleStatus();
        sut.onTodoStatusToggled(todo);

        assertThat(ledger.getIsDeleted()).isEqualTo(YNType.N);
        verify(dailyRepository, never()).findByUserAndDate(any(), any());
    }

    @Test
    @DisplayName("GROWN 확정 후 당일 해제 → 감점만, 수집/GROWN 은 불변")
    void revokeAfterGrownKeepsCollection() {
        Constellation c = dipper();
        ConstellationDaily daily = openDaily(c, 7);
        daily.grow();
        TodoStarlight ledger = TodoStarlight.earn(user(), StarlightSourceType.TODO, 7L, 3, today);
        given(starlightRepository.findActiveBySource(StarlightSourceType.TODO, 7L)).willReturn(Optional.of(ledger));
        given(dailyRepository.findByUserAndDate(USER_ID, today)).willReturn(Optional.of(daily));

        Todo todo = task(7L, TodoPriority.HIGH);
        todo.toggleStatus();
        sut.onTodoStatusToggled(todo);

        assertThat(daily.getPoints()).isEqualTo(4);
        assertThat(daily.isGrown()).isTrue(); // 스냅샷 불변
    }

    // ── 메모 별빛 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("메모 작성 → +1 별빛")
    void memoEarnsOnePoint() {
        Constellation c = dipper();
        ConstellationDaily daily = openDaily(c, 0);
        Memo memo = Memo.createMemo(user(), null, "m", "c", null, null);
        ReflectionTestUtils.setField(memo, "rowId", 20L);
        given(starlightRepository.countActiveMemoEarns(USER_ID, today)).willReturn(0L);
        given(starlightRepository.existsBySourceIncludingRevoked(StarlightSourceType.MEMO, 20L)).willReturn(false);
        given(dailyRepository.findByUserAndDate(USER_ID, today)).willReturn(Optional.of(daily));

        sut.onMemoCreated(memo);

        ArgumentCaptor<TodoStarlight> captor = ArgumentCaptor.forClass(TodoStarlight.class);
        verify(starlightRepository).save(captor.capture());
        assertThat(captor.getValue().getPoints()).isEqualTo(1);
        assertThat(captor.getValue().getSourceType()).isEqualTo(StarlightSourceType.MEMO);
    }

    @Test
    @DisplayName("메모 별빛 일 한도 2 — 초과분 무시")
    void memoDailyLimit() {
        Memo memo = Memo.createMemo(user(), null, "m", "c", null, null);
        ReflectionTestUtils.setField(memo, "rowId", 21L);
        given(starlightRepository.countActiveMemoEarns(USER_ID, today)).willReturn(2L);

        sut.onMemoCreated(memo);

        verify(starlightRepository, never()).save(any());
    }

    @Test
    @DisplayName("메모 삭제 → 당일 적립분 회수")
    void memoDeleteRevokes() {
        Constellation c = dipper();
        ConstellationDaily daily = openDaily(c, 1);
        Memo memo = Memo.createMemo(user(), null, "m", "c", null, null);
        ReflectionTestUtils.setField(memo, "rowId", 20L);
        TodoStarlight ledger = TodoStarlight.earn(user(), StarlightSourceType.MEMO, 20L, 1, today);
        given(starlightRepository.findActiveBySource(StarlightSourceType.MEMO, 20L)).willReturn(Optional.of(ledger));
        given(dailyRepository.findByUserAndDate(USER_ID, today)).willReturn(Optional.of(daily));

        sut.onMemoDeleted(memo);

        assertThat(ledger.getIsDeleted()).isEqualTo(YNType.Y);
        assertThat(daily.getPoints()).isZero();
    }

    // ── 보호(구름 가림) 정산 ────────────────────────────────────────────────

    @Test
    @DisplayName("어제 수집(GROWN) → 공백 없음, 정산 no-op")
    void reconcileNoGap() {
        given(dailyRepository.findLatestGrownDate(USER_ID)).willReturn(Optional.of(today.minusDays(1)));

        sut.reconcileGuards(USER_ID);

        verify(profileRepository, never()).findByUser(any());
    }

    @Test
    @DisplayName("공백 1일 + 보호 1 → 소비하고 REST 가림 행 생성")
    void reconcileBridgesOneDayGap() {
        Constellation c = dipper();
        ConstellationProfile profile = ConstellationProfile.createProfile(user());
        ReflectionTestUtils.setField(profile, "guardCount", 1);
        given(dailyRepository.findLatestGrownDate(USER_ID)).willReturn(Optional.of(today.minusDays(2)));
        given(dailyRepository.findByUserAndDateBetween(USER_ID, today.minusDays(1), today.minusDays(1)))
            .willReturn(List.of());
        given(profileRepository.findByUser(USER_ID)).willReturn(Optional.of(profile));
        given(constellationRepository.findAllActive()).willReturn(List.of(c));
        given(dailyRepository.save(any(ConstellationDaily.class))).willAnswer(inv -> inv.getArgument(0));

        sut.reconcileGuards(USER_ID);

        assertThat(profile.getGuardCount()).isZero();
        ArgumentCaptor<ConstellationDaily> captor = ArgumentCaptor.forClass(ConstellationDaily.class);
        verify(dailyRepository).save(captor.capture());
        assertThat(captor.getValue().getObsDate()).isEqualTo(today.minusDays(1));
        assertThat(captor.getValue().getStatus()).isEqualTo(DailyStatus.REST);
        assertThat(captor.getValue().getGuardUsed()).isEqualTo(YNType.Y);
    }

    @Test
    @DisplayName("공백 2일 + 보호 1 → 부족하면 소비하지 않음(스트릭 리셋, 보호 보존)")
    void reconcileInsufficientGuardsKeepsThem() {
        ConstellationProfile profile = ConstellationProfile.createProfile(user());
        ReflectionTestUtils.setField(profile, "guardCount", 1);
        given(dailyRepository.findLatestGrownDate(USER_ID)).willReturn(Optional.of(today.minusDays(3)));
        given(dailyRepository.findByUserAndDateBetween(USER_ID, today.minusDays(2), today.minusDays(1)))
            .willReturn(List.of());
        given(profileRepository.findByUser(USER_ID)).willReturn(Optional.of(profile));

        sut.reconcileGuards(USER_ID);

        assertThat(profile.getGuardCount()).isEqualTo(1);
        verify(dailyRepository, never()).save(any());
    }

    @Test
    @DisplayName("공백 중 흐린 밤(WITHERED) 행 → 가림 마킹으로 브리지")
    void reconcileMarksExistingWitheredRow() {
        Constellation c = dipper();
        ConstellationProfile profile = ConstellationProfile.createProfile(user());
        ReflectionTestUtils.setField(profile, "guardCount", 1);
        ConstellationDaily withered = ConstellationDaily.open(user(), today.minusDays(1), c);
        withered.addPoints(2);
        given(dailyRepository.findLatestGrownDate(USER_ID)).willReturn(Optional.of(today.minusDays(2)));
        given(dailyRepository.findByUserAndDateBetween(USER_ID, today.minusDays(1), today.minusDays(1)))
            .willReturn(List.of(withered));
        given(profileRepository.findByUser(USER_ID)).willReturn(Optional.of(profile));

        sut.reconcileGuards(USER_ID);

        assertThat(withered.getGuardUsed()).isEqualTo(YNType.Y);
        assertThat(withered.getStatus()).isEqualTo(DailyStatus.WITHERED); // 상태는 유지, 가림만
        verify(dailyRepository, never()).save(any());
    }

    // ── 일일 목표 순환 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("dailyTarget — epochDay 기반 결정적 순환 (같은 날 = 같은 별자리, 다음 날 = 다음 순번)")
    void dailyTargetDeterministicRotation() {
        Constellation c1 = dipper();
        Constellation c2 = Constellation.createConstellation(
            "cass", "카시오페이아", "Cassiopeia", "W자 여왕", "The W queen", "violet", 5, "{\"pts\":[],\"edges\":[]}", 2);
        given(constellationRepository.findAllActive()).willReturn(List.of(c1, c2));

        LocalDate date = LocalDate.of(2026, 7, 10);
        Constellation first = sut.dailyTarget(date);
        Constellation again = sut.dailyTarget(date);
        Constellation next = sut.dailyTarget(date.plusDays(1));

        assertThat(again).isSameAs(first);
        assertThat(next).isNotSameAs(first);
        assertThat(sut.dailyTarget(date.plusDays(2))).isSameAs(first); // 2개 순환
    }
}
