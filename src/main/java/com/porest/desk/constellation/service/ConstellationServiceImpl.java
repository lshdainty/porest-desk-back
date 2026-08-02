package com.porest.desk.constellation.service;

import com.porest.core.type.YNType;
import com.porest.desk.constellation.domain.Constellation;
import com.porest.desk.constellation.domain.ConstellationDaily;
import com.porest.desk.constellation.repository.ConstellationCollectionRepository;
import com.porest.desk.constellation.repository.ConstellationDailyRepository;
import com.porest.desk.constellation.repository.ConstellationProfileRepository;
import com.porest.desk.constellation.repository.ConstellationRepository;
import com.porest.desk.constellation.repository.TodoStarlightRepository;
import com.porest.desk.constellation.service.dto.ConstellationServiceDto;
import com.porest.desk.constellation.type.DailyStatus;
import com.porest.desk.constellation.type.StarlightSourceType;
import com.porest.desk.common.time.UserClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ConstellationServiceImpl implements ConstellationService {
    /** 스트릭 역방향 탐색 상한 — 보호 없인 하루 공백에도 끊기므로 충분히 큰 안전 상한. */
    private static final int STREAK_LOOKBACK_DAYS = 400;

    private final ConstellationRepository constellationRepository;
    private final UserClock userClock;
    private final ConstellationProfileRepository profileRepository;
    private final TodoStarlightRepository starlightRepository;
    private final ConstellationDailyRepository dailyRepository;
    private final ConstellationCollectionRepository collectionRepository;
    private final StarlightService starlightService;

    @Override
    public List<ConstellationServiceDto.ConstellationInfo> getCatalog() {
        return constellationRepository.findAll().stream()
            .map(ConstellationServiceDto.ConstellationInfo::from)
            .toList();
    }

    @Override
    @Transactional // 보호 정산(lazy settlement) 때문에 쓰기 트랜잭션
    public ConstellationServiceDto.TodayInfo getToday(Long userRowId) {
        log.debug("오늘의 별자리 현황 조회: userRowId={}", userRowId);
        starlightService.reconcileGuards(userRowId);

        LocalDate today = userClock.today(userRowId);
        ConstellationDaily daily = dailyRepository.findByUserAndDate(userRowId, today).orElse(null);
        // 오늘 행이 있으면 그 행의 별자리(적립 시점 고정)를, 없으면 오늘의 순환 목표를 사용
        Constellation target = daily != null ? daily.getConstellation() : starlightService.dailyTarget(today);

        Map<StarlightSourceType, Integer> breakdown = starlightRepository.sumActivePointsByDate(userRowId, today);
        int todoPoints = breakdown.getOrDefault(StarlightSourceType.TODO, 0);
        int memoPoints = breakdown.getOrDefault(StarlightSourceType.MEMO, 0);

        return new ConstellationServiceDto.TodayInfo(
            ConstellationServiceDto.ConstellationInfo.from(target),
            daily != null ? daily.getPoints() : 0,
            target.getStarCount(),
            daily != null && daily.isGrown(),
            todoPoints,
            memoPoints,
            computeStreak(userRowId, today),
            profileRepository.findByUser(userRowId)
                .map(profile -> profile.getGuardCount()).orElse(0),
            collectionRepository.countByUser(userRowId)
        );
    }

    @Override
    public List<ConstellationServiceDto.SkyDay> getSky(Long userRowId, int days) {
        log.debug("나의 밤하늘 조회: userRowId={}, days={}", userRowId, days);
        LocalDate today = userClock.today(userRowId);
        LocalDate from = today.minusDays(days - 1L);

        Map<LocalDate, ConstellationDaily> byDate = dailyRepository
            .findByUserAndDateBetween(userRowId, from, today).stream()
            .collect(Collectors.toMap(ConstellationDaily::getObsDate, Function.identity()));

        List<ConstellationServiceDto.SkyDay> skyDays = new ArrayList<>(days);
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            ConstellationDaily row = byDate.get(d);
            if (row == null) {
                skyDays.add(new ConstellationServiceDto.SkyDay(d, DailyStatus.REST, null, null, 0, false));
            } else {
                skyDays.add(new ConstellationServiceDto.SkyDay(
                    d,
                    row.getStatus(),
                    row.getConstellation().getConstellationKey(),
                    row.getConstellation().getColorKey(),
                    row.getPoints(),
                    row.getGuardUsed() == YNType.Y
                ));
            }
        }
        return skyDays;
    }

    @Override
    public ConstellationServiceDto.CollectionInfo getCollection(Long userRowId) {
        log.debug("별자리 도감 조회: userRowId={}", userRowId);
        Map<Long, ConstellationCollectionRepository.CollectionStat> statByConstellation =
            collectionRepository.findStatsByUser(userRowId).stream()
                .collect(Collectors.toMap(
                    ConstellationCollectionRepository.CollectionStat::constellationRowId,
                    Function.identity()
                ));

        List<ConstellationServiceDto.CollectionEntry> entries = constellationRepository.findAll().stream()
            .map(constellation -> {
                ConstellationCollectionRepository.CollectionStat stat = statByConstellation.get(constellation.getRowId());
                return new ConstellationServiceDto.CollectionEntry(
                    ConstellationServiceDto.ConstellationInfo.from(constellation),
                    stat != null ? stat.count() : 0L,
                    stat != null ? stat.lastCollectedDate() : null
                );
            })
            .toList();

        int collectedKinds = (int) entries.stream().filter(entry -> entry.collectCount() > 0).count();
        long totalCollected = entries.stream().mapToLong(ConstellationServiceDto.CollectionEntry::collectCount).sum();
        return new ConstellationServiceDto.CollectionInfo(entries, collectedKinds, totalCollected);
    }

    /**
     * 연속 관측(스트릭) — 오늘부터 역방향으로 GROWN 일수를 센다.
     * 보호 가림(guard_used=Y) 날은 끊지 않되 세지도 않는다. 오늘 미수집은 진행 중으로 보고 건너뛴다.
     */
    private int computeStreak(Long userRowId, LocalDate today) {
        Map<LocalDate, ConstellationDaily> byDate = dailyRepository
            .findByUserAndDateBetween(userRowId, today.minusDays(STREAK_LOOKBACK_DAYS), today).stream()
            .collect(Collectors.toMap(ConstellationDaily::getObsDate, Function.identity()));

        int streak = 0;
        ConstellationDaily todayRow = byDate.get(today);
        if (todayRow != null && todayRow.isGrown()) {
            streak++;
        }
        for (LocalDate d = today.minusDays(1); !d.isBefore(today.minusDays(STREAK_LOOKBACK_DAYS)); d = d.minusDays(1)) {
            ConstellationDaily row = byDate.get(d);
            if (row == null) {
                break;
            }
            if (row.isGrown()) {
                streak++;
            } else if (row.getGuardUsed() != YNType.Y) {
                break;
            }
            // guard_used=Y — 가려진 날: 세지 않고 연속 유지
        }
        return streak;
    }
}
