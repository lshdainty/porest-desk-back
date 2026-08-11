package com.porest.desk.constellation.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.type.YNType;
import com.porest.desk.common.exception.DeskErrorCode;
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
import com.porest.desk.constellation.type.StarlightSourceType;
import com.porest.desk.memo.domain.Memo;
import com.porest.desk.todo.domain.Todo;
import com.porest.desk.todo.type.TodoPriority;
import com.porest.desk.todo.type.TodoStatus;
import com.porest.desk.todo.type.TodoType;
import com.porest.desk.user.domain.User;
import com.porest.core.time.UserClock;
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
public class StarlightServiceImpl implements StarlightService {
    static final int MEMO_POINTS = 1;
    static final int MEMO_DAILY_LIMIT = 2;

    private final ConstellationRepository constellationRepository;
    private final UserClock userClock;
    private final ConstellationProfileRepository profileRepository;
    private final TodoStarlightRepository starlightRepository;
    private final ConstellationDailyRepository dailyRepository;
    private final ConstellationCollectionRepository collectionRepository;

    /** 우선순위 가중 별빛 — 디자인 FOREST_WEIGHT(중요 3 · 보통 2 · 여유 1) 정합. */
    static int weightOf(TodoPriority priority) {
        return switch (priority) {
            case HIGH -> 3;
            case MEDIUM -> 2;
            case LOW -> 1;
        };
    }

    @Override
    @Transactional
    public int onTodoStatusToggled(Todo todo) {
        if (todo.getType() != TodoType.TASK) {
            return 0;
        }
        if (todo.getStatus() == TodoStatus.COMPLETED) {
            return earn(todo.getUser(), StarlightSourceType.TODO, todo.getRowId(), weightOf(todo.getPriority()));
        }
        revoke(StarlightSourceType.TODO, todo.getRowId());
        return 0;
    }

    @Override
    @Transactional
    public void onMemoCreated(Memo memo) {
        LocalDate today = userClock.todayIn(memo.getUser().getTimezone());
        long earnedToday = starlightRepository.countActiveMemoEarns(memo.getUser().getRowId(), today);
        if (earnedToday >= MEMO_DAILY_LIMIT) {
            log.debug("메모 별빛 일 한도 초과: userRowId={}, earnedToday={}", memo.getUser().getRowId(), earnedToday);
            return;
        }
        earn(memo.getUser(), StarlightSourceType.MEMO, memo.getRowId(), MEMO_POINTS);
    }

    @Override
    @Transactional
    public void onMemoDeleted(Memo memo) {
        revoke(StarlightSourceType.MEMO, memo.getRowId());
    }

    /**
     * 적립 — 출처당 원장 1행. 오늘의 관측 행이 없으면 보호 정산 후 연다.
     * 목표 도달 시 GROWN 확정 + 도감 스냅샷 + 보호 충전 진행(7일마다 +1, 최대 2).
     *
     * <p>같은 출처의 행이 이미 있으면: 활성이면 이중 적립이라 차단하고, <b>당일 회수분이면
     * 복원</b>한다 — 체크를 껐다 켜는 왕복은 회수(-N)와 짝이 맞아야지, 차단해 버리면 그날
     * 별빛이 영구히 사라진다. 타일 회수분은 복원하지 않는다(지난 날 합계·수집은 불변).
     *
     * @return 실제 적립(복원 포함)된 별빛 — 차단이면 0.
     */
    private int earn(User user, StarlightSourceType sourceType, Long sourceRowId, int points) {
        // 사용자 엔티티를 이미 쥐고 있으므로 rowId 재조회 없이 타임존 문자열로 판단한다.
        LocalDate today = userClock.todayIn(user.getTimezone());

        TodoStarlight existing = starlightRepository
            .findBySourceIncludingRevoked(sourceType, sourceRowId).orElse(null);
        if (existing != null) {
            if (!existing.isRevoked() || !existing.getEarnDate().equals(today)) {
                log.debug("별빛 재적립 차단(평생 1회): sourceType={}, sourceRowId={}, revoked={}, earnDate={}",
                    sourceType, sourceRowId, existing.isRevoked(), existing.getEarnDate());
                return 0;
            }
            existing.restore();
            points = existing.getPoints();
            log.debug("별빛 복원(당일 왕복): sourceType={}, sourceRowId={}, points={}",
                sourceType, sourceRowId, points);
        } else {
            starlightRepository.save(TodoStarlight.earn(user, sourceType, sourceRowId, points, today));
        }

        ConstellationDaily daily = dailyRepository.findByUserAndDate(user.getRowId(), today)
            .orElseGet(() -> {
                reconcileGuards(user.getRowId());
                return dailyRepository.save(ConstellationDaily.open(user, today, dailyTarget(today)));
            });
        // 그날 합계는 원장에서 집계한다 — 캐시로 들고 있으면 적립·회수 어느 한쪽을
        // 빠뜨렸을 때 조용히 어긋난다. 하루치라 행 수가 적어 집계가 싸다.
        int dailyPoints = starlightRepository.sumPointsByUserAndDate(user.getRowId(), today);
        daily.syncPoints(dailyPoints);
        log.debug("별빛 적립: userRowId={}, sourceType={}, points={}, dailyPoints={}",
            user.getRowId(), sourceType, points, dailyPoints);

        if (!daily.isGrown() && dailyPoints >= daily.getConstellation().getStarCount()) {
            daily.grow();
            collectionRepository.save(ConstellationCollection.collect(user, daily.getConstellation(), today));
            findOrCreateProfile(user).recordGrown();
            log.info("별자리 수집: userRowId={}, constellation={}, date={}",
                user.getRowId(), daily.getConstellation().getConstellationKey(), today);
        }
        return points;
    }

    /** 회수 — 당일 적립분만. soft delete 로 unique 행이 남아 재적립은 계속 차단된다. GROWN/도감은 불변. */
    private void revoke(StarlightSourceType sourceType, Long sourceRowId) {
        starlightRepository.findActiveBySource(sourceType, sourceRowId).ifPresent(ledger -> {
            LocalDate today = userClock.todayIn(ledger.getUser().getTimezone());
            if (!ledger.getEarnDate().equals(today)) {
                log.debug("별빛 회수 생략(타일 적립분): sourceType={}, sourceRowId={}, earnDate={}",
                    sourceType, sourceRowId, ledger.getEarnDate());
                return;
            }
            ledger.revoke();
            dailyRepository.findByUserAndDate(ledger.getUser().getRowId(), today)
                .ifPresent(daily -> daily.syncPoints(
                    starlightRepository.sumPointsByUserAndDate(ledger.getUser().getRowId(), today)));
            log.debug("별빛 회수: sourceType={}, sourceRowId={}, points={}", sourceType, sourceRowId, ledger.getPoints());
        });
    }

    @Override
    @Transactional
    public void reconcileGuards(Long userRowId) {
        LocalDate today = userClock.today(userRowId);
        LocalDate latestGrown = dailyRepository.findLatestGrownDate(userRowId).orElse(null);
        if (latestGrown == null || !latestGrown.isBefore(today.minusDays(1))) {
            return; // 수집 이력 없음 or 어제/오늘 수집 → 공백 없음
        }

        LocalDate from = latestGrown.plusDays(1);
        LocalDate yesterday = today.minusDays(1);
        Map<LocalDate, ConstellationDaily> byDate = dailyRepository
            .findByUserAndDateBetween(userRowId, from, yesterday).stream()
            .collect(Collectors.toMap(ConstellationDaily::getObsDate, Function.identity()));

        List<LocalDate> needBridge = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(yesterday); d = d.plusDays(1)) {
            ConstellationDaily row = byDate.get(d);
            if (row == null || row.getGuardUsed() != YNType.Y) {
                needBridge.add(d);
                if (needBridge.size() > ConstellationProfile.GUARD_MAX) {
                    return; // 보유 최대치보다 긴 공백 — 가릴 수 없음(스트릭 리셋, 보호권 보존)
                }
            }
        }
        if (needBridge.isEmpty()) {
            return; // 전부 이미 가려짐
        }

        ConstellationProfile profile = profileRepository.findByUser(userRowId).orElse(null);
        if (profile == null || !profile.canConsume(needBridge.size())) {
            return; // 보호권 부족 — 소비하지 않고 스트릭 리셋
        }
        profile.consumeGuards(needBridge.size());
        for (LocalDate d : needBridge) {
            ConstellationDaily row = byDate.get(d);
            if (row != null) {
                row.markGuardUsed();
            } else {
                dailyRepository.save(ConstellationDaily.restBridged(profile.getUser(), d, dailyTarget(d)));
            }
        }
        log.info("스트릭 보호 소비: userRowId={}, bridgedDays={}, 남은 보호={}",
            userRowId, needBridge, profile.getGuardCount());
    }

    @Override
    public Constellation dailyTarget(LocalDate date) {
        List<Constellation> actives = constellationRepository.findAllActive();
        if (actives.isEmpty()) {
            throw new EntityNotFoundException(DeskErrorCode.CONSTELLATION_NOT_FOUND);
        }
        int idx = Math.floorMod(date.toEpochDay(), actives.size());
        return actives.get(idx);
    }

    private ConstellationProfile findOrCreateProfile(User user) {
        return profileRepository.findByUser(user.getRowId())
            .orElseGet(() -> profileRepository.save(ConstellationProfile.createProfile(user)));
    }
}
