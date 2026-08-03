package com.porest.desk.constellation.service;

import com.porest.desk.constellation.domain.Constellation;
import com.porest.desk.constellation.domain.ConstellationDaily;
import com.porest.desk.constellation.domain.ConstellationProfile;
import com.porest.desk.constellation.repository.ConstellationCollectionRepository;
import com.porest.desk.constellation.repository.ConstellationDailyRepository;
import com.porest.desk.constellation.repository.ConstellationProfileRepository;
import com.porest.desk.constellation.repository.ConstellationRepository;
import com.porest.desk.constellation.repository.TodoStarlightRepository;
import com.porest.desk.constellation.service.dto.ConstellationServiceDto;
import com.porest.desk.constellation.type.DailyStatus;
import com.porest.desk.constellation.type.StarlightSourceType;
import com.porest.desk.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import com.porest.core.time.ServiceClock;
import com.porest.core.time.UserClock;

/**
 * 별자리 조회 서비스 — 오늘 현황(보호 정산 위임/내역 합산/스트릭), 밤하늘 REST 채움, 도감 매핑 검증.
 */
@ExtendWith(MockitoExtension.class)
class ConstellationServiceImplTest {

    @Mock private ConstellationRepository constellationRepository;
    @Mock private ConstellationProfileRepository profileRepository;
    @Mock private TodoStarlightRepository starlightRepository;
    @Mock private ConstellationDailyRepository dailyRepository;
    @Mock private ConstellationCollectionRepository collectionRepository;
    @Mock private StarlightService starlightService;
    // 날짜 판정용 — mock 이면 null 이 흘러 NPE. 실물을 주입하되 사용자 조회는 비어
    // 서비스 기준(Asia/Seoul)으로 폴백한다.
    @Spy private UserClock userClock = new UserClock(rowId -> null, new ServiceClock("Asia/Seoul"));

    @InjectMocks private ConstellationServiceImpl sut;

    private static final long USER_ID = 1L;
    private final LocalDate today = LocalDate.now();

    private User user() {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", USER_ID);
        return u;
    }

    private Constellation constellation(long rowId, String key, int starCount, int sortOrder) {
        Constellation c = Constellation.createConstellation(
            key, key + "명", key + "-en", "설명", "desc", "blue", starCount, "{\"pts\":[],\"edges\":[]}", sortOrder);
        ReflectionTestUtils.setField(c, "rowId", rowId);
        return c;
    }

    private ConstellationDaily daily(LocalDate date, Constellation c, int points, boolean grown, boolean guardUsed) {
        ConstellationDaily d = ConstellationDaily.open(user(), date, c);
        d.addPoints(points);
        if (grown) {
            d.grow();
        }
        if (guardUsed) {
            d.markGuardUsed();
        }
        return d;
    }

    // ── 오늘 현황 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getToday — 보호 정산 위임 + 오늘 행 기준 진행/내역/보호권/누적 매핑")
    void getTodayWithDailyRow() {
        Constellation c = constellation(10L, "dipper", 7, 1);
        ConstellationDaily todayRow = daily(today, c, 5, false, false);
        ConstellationProfile profile = ConstellationProfile.createProfile(user());
        ReflectionTestUtils.setField(profile, "guardCount", 1);

        given(dailyRepository.findByUserAndDate(USER_ID, today)).willReturn(Optional.of(todayRow));
        given(starlightRepository.sumActivePointsByDate(USER_ID, today))
            .willReturn(Map.of(StarlightSourceType.TODO, 4, StarlightSourceType.MEMO, 1));
        given(profileRepository.findByUser(USER_ID)).willReturn(Optional.of(profile));
        given(collectionRepository.countByUser(USER_ID)).willReturn(32L);
        given(dailyRepository.findByUserAndDateBetween(eq(USER_ID), any(), eq(today)))
            .willReturn(List.of(todayRow));

        ConstellationServiceDto.TodayInfo info = sut.getToday(USER_ID);

        verify(starlightService).reconcileGuards(USER_ID);
        assertThat(info.constellation().constellationKey()).isEqualTo("dipper");
        assertThat(info.points()).isEqualTo(5);
        assertThat(info.goal()).isEqualTo(7);
        assertThat(info.collected()).isFalse();
        assertThat(info.todoPoints()).isEqualTo(4);
        assertThat(info.memoPoints()).isEqualTo(1);
        assertThat(info.guardCount()).isEqualTo(1);
        assertThat(info.totalCollected()).isEqualTo(32L);
    }

    @Test
    @DisplayName("getToday — 오늘 행이 없으면 순환 목표를 쓰고 진행 0")
    void getTodayWithoutDailyRow() {
        Constellation c = constellation(10L, "cass", 5, 2);
        given(dailyRepository.findByUserAndDate(USER_ID, today)).willReturn(Optional.empty());
        given(starlightService.dailyTarget(today)).willReturn(c);
        given(starlightRepository.sumActivePointsByDate(USER_ID, today)).willReturn(Map.of());
        given(profileRepository.findByUser(USER_ID)).willReturn(Optional.empty());
        given(collectionRepository.countByUser(USER_ID)).willReturn(0L);
        given(dailyRepository.findByUserAndDateBetween(eq(USER_ID), any(), eq(today))).willReturn(List.of());

        ConstellationServiceDto.TodayInfo info = sut.getToday(USER_ID);

        assertThat(info.constellation().constellationKey()).isEqualTo("cass");
        assertThat(info.points()).isZero();
        assertThat(info.goal()).isEqualTo(5);
        assertThat(info.collected()).isFalse();
        assertThat(info.guardCount()).isZero();
        assertThat(info.streak()).isZero();
    }

    @Test
    @DisplayName("스트릭 — 오늘 수집 + 연속 GROWN, 가림(guard_used) 날은 세지 않고 잇는다")
    void streakCountsGrownAndBridgesGuardedDays() {
        Constellation c = constellation(10L, "dipper", 7, 1);
        // today GROWN, D-1 GROWN, D-2 가림(REST+guard), D-3 GROWN, D-4 없음 → 스트릭 3
        ConstellationDaily d0 = daily(today, c, 7, true, false);
        ConstellationDaily d1 = daily(today.minusDays(1), c, 7, true, false);
        ConstellationDaily d2 = ConstellationDaily.restBridged(user(), today.minusDays(2), c);
        ConstellationDaily d3 = daily(today.minusDays(3), c, 7, true, false);

        given(dailyRepository.findByUserAndDate(USER_ID, today)).willReturn(Optional.of(d0));
        given(starlightRepository.sumActivePointsByDate(USER_ID, today)).willReturn(Map.of());
        given(profileRepository.findByUser(USER_ID)).willReturn(Optional.empty());
        given(collectionRepository.countByUser(USER_ID)).willReturn(4L);
        given(dailyRepository.findByUserAndDateBetween(eq(USER_ID), any(), eq(today)))
            .willReturn(List.of(d3, d2, d1, d0));

        ConstellationServiceDto.TodayInfo info = sut.getToday(USER_ID);

        assertThat(info.streak()).isEqualTo(3);
    }

    @Test
    @DisplayName("스트릭 — 어제 흐린 밤(가림 없음)이면 오늘 수집만 1")
    void streakBreaksOnUnguardedWithered() {
        Constellation c = constellation(10L, "dipper", 7, 1);
        ConstellationDaily d0 = daily(today, c, 7, true, false);
        ConstellationDaily d1 = daily(today.minusDays(1), c, 2, false, false); // WITHERED, 가림 없음
        ConstellationDaily d2 = daily(today.minusDays(2), c, 7, true, false);

        given(dailyRepository.findByUserAndDate(USER_ID, today)).willReturn(Optional.of(d0));
        given(starlightRepository.sumActivePointsByDate(USER_ID, today)).willReturn(Map.of());
        given(profileRepository.findByUser(USER_ID)).willReturn(Optional.empty());
        given(collectionRepository.countByUser(USER_ID)).willReturn(2L);
        given(dailyRepository.findByUserAndDateBetween(eq(USER_ID), any(), eq(today)))
            .willReturn(List.of(d2, d1, d0));

        ConstellationServiceDto.TodayInfo info = sut.getToday(USER_ID);

        assertThat(info.streak()).isEqualTo(1);
    }

    // ── 나의 밤하늘 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getSky — 요청 일수만큼 반환, 무행일은 REST 로 채움")
    void getSkyFillsRestDays() {
        Constellation c = constellation(10L, "orion", 7, 3);
        ConstellationDaily grown = daily(today.minusDays(1), c, 7, true, false);
        given(dailyRepository.findByUserAndDateBetween(USER_ID, today.minusDays(2), today))
            .willReturn(List.of(grown));

        List<ConstellationServiceDto.SkyDay> sky = sut.getSky(USER_ID, 3);

        assertThat(sky).hasSize(3);
        assertThat(sky.get(0).date()).isEqualTo(today.minusDays(2));
        assertThat(sky.get(0).status()).isEqualTo(DailyStatus.REST);
        assertThat(sky.get(0).constellationKey()).isNull();
        assertThat(sky.get(1).status()).isEqualTo(DailyStatus.GROWN);
        assertThat(sky.get(1).constellationKey()).isEqualTo("orion");
        assertThat(sky.get(1).colorKey()).isEqualTo("blue");
        assertThat(sky.get(2).date()).isEqualTo(today);
        assertThat(sky.get(2).status()).isEqualTo(DailyStatus.REST);
    }

    // ── 도감 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getCollection — 전체 별자리에 수집 통계 매핑, 미수집은 0/null")
    void getCollectionMapsStats() {
        Constellation c1 = constellation(10L, "dipper", 7, 1);
        Constellation c2 = constellation(11L, "cass", 5, 2);
        given(constellationRepository.findAll()).willReturn(List.of(c1, c2));
        given(collectionRepository.findStatsByUser(USER_ID)).willReturn(List.of(
            new ConstellationCollectionRepository.CollectionStat(10L, 32L, today.minusDays(1))
        ));

        ConstellationServiceDto.CollectionInfo info = sut.getCollection(USER_ID);

        assertThat(info.entries()).hasSize(2);
        assertThat(info.entries().get(0).collectCount()).isEqualTo(32L);
        assertThat(info.entries().get(0).lastCollectedDate()).isEqualTo(today.minusDays(1));
        assertThat(info.entries().get(1).collectCount()).isZero();
        assertThat(info.entries().get(1).lastCollectedDate()).isNull();
        assertThat(info.collectedKinds()).isEqualTo(1);
        assertThat(info.totalCollected()).isEqualTo(32L);
    }

    @Test
    @DisplayName("getCatalog — 정렬 순서대로 마스터 매핑(starMap 포함)")
    void getCatalog() {
        Constellation c1 = constellation(10L, "dipper", 7, 1);
        given(constellationRepository.findAll()).willReturn(List.of(c1));

        List<ConstellationServiceDto.ConstellationInfo> catalog = sut.getCatalog();

        assertThat(catalog).hasSize(1);
        assertThat(catalog.get(0).constellationKey()).isEqualTo("dipper");
        assertThat(catalog.get(0).starCount()).isEqualTo(7);
        assertThat(catalog.get(0).starMap()).contains("pts");
    }
}
