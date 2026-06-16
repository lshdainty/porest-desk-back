package com.porest.desk.asset.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.domain.AssetTransfer;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.asset.repository.AssetTransferRepository;
import com.porest.desk.asset.service.AssetBalanceHistoryService.BalanceResolver;
import com.porest.desk.asset.service.dto.AssetServiceDto;
import com.porest.desk.asset.type.AssetType;
import com.porest.desk.card.domain.CardCatalog;
import com.porest.desk.card.repository.CardCatalogRepository;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AssetServiceImpl implements AssetService {
    private static final Set<AssetType> DEBT_TYPES = Set.of(AssetType.CREDIT_CARD, AssetType.LOAN);

    private final AssetRepository assetRepository;
    private final AssetTransferRepository assetTransferRepository;
    private final UserRepository userRepository;
    private final CardCatalogRepository cardCatalogRepository;
    private final AssetBalanceHistoryService balanceHistoryService;

    @Override
    @Transactional
    public AssetServiceDto.AssetInfo createAsset(AssetServiceDto.CreateAssetCommand command) {
        log.debug("자산 등록 시작: userRowId={}, assetName={}", command.userRowId(), command.assetName());

        User user = userRepository.findById(command.userRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

        CardCatalog cardCatalog = resolveCardCatalog(command.cardCatalogRowId());
        Asset paymentAsset = resolvePaymentAsset(command.paymentAssetRowId(), command.userRowId());

        Asset asset = Asset.createAsset(
            user,
            command.assetName(),
            command.assetType(),
            command.balance(),
            command.currency() != null ? command.currency() : "KRW",
            command.color(),
            command.institution(),
            command.memo(),
            command.sortOrder() != null ? command.sortOrder() : 0,
            command.isIncludedInTotal(),
            cardCatalog,
            command.creditLimit(),
            command.paymentDay(),
            paymentAsset
        );

        assetRepository.save(asset);
        // 잔액 이력: 초기 잔액 절대 앵커
        balanceHistoryService.recordInit(asset, LocalDateTime.now());
        log.info("자산 등록 완료: assetId={}, userRowId={}", asset.getRowId(), command.userRowId());

        return AssetServiceDto.AssetInfo.from(asset);
    }

    @Override
    public List<AssetServiceDto.AssetInfo> getAssets(Long userRowId) {
        log.debug("자산 목록 조회: userRowId={}", userRowId);

        return assetRepository.findByUser(userRowId).stream()
            .map(AssetServiceDto.AssetInfo::from)
            .toList();
    }

    @Override
    public AssetServiceDto.AssetInfo getAsset(Long assetId, Long userRowId) {
        log.debug("자산 상세 조회: assetId={}", assetId);

        Asset asset = findAssetOrThrow(assetId);
        validateAssetOwnership(asset, userRowId);
        return AssetServiceDto.AssetInfo.from(asset);
    }

    @Override
    @Transactional
    public AssetServiceDto.AssetInfo updateAsset(Long assetId, Long userRowId, AssetServiceDto.UpdateAssetCommand command) {
        log.debug("자산 수정 시작: assetId={}", assetId);

        Asset asset = findAssetOrThrow(assetId);
        validateAssetOwnership(asset, userRowId);

        CardCatalog cardCatalog = resolveCardCatalog(command.cardCatalogRowId());
        // paymentAssetRowId 가 들어온 경우에만 로드 — null 이면 기존 연관 유지(partial update).
        Asset paymentAsset = resolvePaymentAsset(command.paymentAssetRowId(), userRowId);

        // 필수 필드(NOT NULL)는 null 이면 기존 값 유지 — partial update 허용.
        // 선택 필드(color/institution/memo) 는 null 을 clear 로 간주.
        Long oldBalance = asset.getBalance();
        Long newBalance = command.balance() != null ? command.balance() : asset.getBalance();
        asset.updateAsset(
            command.assetName() != null ? command.assetName() : asset.getAssetName(),
            command.assetType() != null ? command.assetType() : asset.getAssetType(),
            command.currency()  != null ? command.currency()  : asset.getCurrency(),
            command.color(),
            command.institution(),
            command.memo(),
            command.isIncludedInTotal(),
            cardCatalog,
            command.creditLimit(),
            command.paymentDay(),
            paymentAsset
        );

        // 잔액을 직접 수정(점프)한 경우에만 MANUAL 절대 앵커 적재 — 가계부 통계엔 영향 없음.
        if (!Objects.equals(oldBalance, newBalance)) {
            balanceHistoryService.recordManual(asset, newBalance, LocalDateTime.now());
        }

        log.info("자산 수정 완료: assetId={}", assetId);
        return AssetServiceDto.AssetInfo.from(asset);
    }

    @Override
    @Transactional
    public void deleteAsset(Long assetId, Long userRowId) {
        log.debug("자산 삭제 시작: assetId={}", assetId);

        Asset asset = findAssetOrThrow(assetId);
        validateAssetOwnership(asset, userRowId);
        asset.deleteAsset();

        log.info("자산 삭제 완료: assetId={}", assetId);
    }

    @Override
    public AssetServiceDto.AssetSummary getAssetSummary(Long userRowId, Integer year, Integer month) {
        log.debug("자산 요약 조회: userRowId={}, year={}, month={}", userRowId, year, month);

        List<Asset> included = includedAssets(userRowId);
        BalanceResolver resolver = balanceHistoryService.resolverFor(included);

        LocalDate today = LocalDate.now();
        boolean isPastPeriod = year != null && month != null
            && !(year == today.getYear() && month == today.getMonthValue());

        LocalDateTime asOf;
        LocalDateTime prevAsOf;
        if (!isPastPeriod) {
            // 현재 월(또는 year/month 미지정): 지금 시각 기준
            asOf = LocalDateTime.now();
            prevAsOf = today.withDayOfMonth(1).minusDays(1).atTime(LocalTime.MAX);
        } else {
            // 과거 월: 선택 월 말 / 전월 말 시점
            LocalDate selectedMonthEnd = LocalDate.of(year, month, 1).with(TemporalAdjusters.lastDayOfMonth());
            asOf = selectedMonthEnd.atTime(LocalTime.MAX);
            prevAsOf = selectedMonthEnd.minusMonths(1)
                .with(TemporalAdjusters.lastDayOfMonth()).atTime(LocalTime.MAX);
        }
        return buildSummaryAt(included, resolver, asOf, prevAsOf);
    }

    /** 기준시각(asOf) 잔액으로 요약을 구성하고, 전월 시점(prevAsOf) 순자산과의 증감을 계산. */
    private AssetServiceDto.AssetSummary buildSummaryAt(List<Asset> included, BalanceResolver resolver,
                                                        LocalDateTime asOf, LocalDateTime prevAsOf) {
        long totalBalance = 0, totalAssets = 0, totalDebt = 0;
        Map<AssetType, long[]> byTypeAcc = new EnumMap<>(AssetType.class); // long[2] = { sumBalance, count }
        for (Asset a : included) {
            long bal = resolver.balanceAt(a.getRowId(), asOf);
            totalBalance += bal;
            if (DEBT_TYPES.contains(a.getAssetType())) {
                totalDebt += Math.abs(bal);
            } else {
                totalAssets += bal;
            }
            long[] acc = byTypeAcc.computeIfAbsent(a.getAssetType(), k -> new long[]{0, 0});
            acc[0] += bal;
            acc[1] += 1;
        }
        long netWorth = totalAssets - totalDebt;

        long lastMonthNetWorth = netWorthAt(included, resolver, prevAsOf);
        long changeAmount = netWorth - lastMonthNetWorth;
        double changePercent = lastMonthNetWorth == 0
            ? 0.0
            : Math.round(((double) changeAmount / Math.abs(lastMonthNetWorth)) * 1000.0) / 10.0;

        List<AssetServiceDto.AssetTypeSummary> byType = byTypeAcc.entrySet().stream()
            .map(e -> new AssetServiceDto.AssetTypeSummary(e.getKey(), e.getValue()[0], (int) e.getValue()[1]))
            .toList();

        return new AssetServiceDto.AssetSummary(
            totalBalance, totalAssets, totalDebt, netWorth,
            lastMonthNetWorth, changeAmount, changePercent, byType
        );
    }

    @Override
    public List<AssetServiceDto.NetWorthTrendPoint> getNetWorthTrend(Long userRowId, Integer months) {
        int n = (months == null || months < 1) ? 12 : Math.min(months, 36);
        log.debug("순자산 추이 조회: userRowId={}, months={}", userRowId, n);

        List<Asset> included = includedAssets(userRowId);
        BalanceResolver resolver = balanceHistoryService.resolverFor(included);

        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        List<AssetServiceDto.NetWorthTrendPoint> points = new ArrayList<>(n);
        for (int i = n - 1; i >= 0; i--) {
            LocalDate m = today.minusMonths(i);
            // 현재 월(i=0)은 지금 시각, 과거 월은 월말 23:59:59.999999 기준 → 현재 점 = summary netWorth 와 동일
            LocalDateTime asOf = (i == 0)
                ? now
                : m.with(TemporalAdjusters.lastDayOfMonth()).atTime(LocalTime.MAX);
            long nw = netWorthAt(included, resolver, asOf);
            points.add(new AssetServiceDto.NetWorthTrendPoint(m.getYear(), m.getMonthValue(), nw));
        }
        return points;
    }

    @Override
    public List<AssetServiceDto.AssetBalancePoint> getAssetBalanceTrend(Long assetId, Long userRowId, Integer weeks) {
        int n = (weeks == null || weeks < 1) ? 12 : Math.min(weeks, 104);
        log.debug("자산 잔액 추이 조회: assetId={}, weeks={}", assetId, n);

        Asset asset = findAssetOrThrow(assetId);
        validateAssetOwnership(asset, userRowId);

        BalanceResolver resolver = balanceHistoryService.resolverFor(List.of(asset));

        // window: 이번 주 월요일 기준 n-1주 전 ~ 이번 주
        LocalDate today = LocalDate.now();
        LocalDate currentMonday = today.with(DayOfWeek.MONDAY);
        LocalDate firstMonday = currentMonday.minusWeeks(n - 1);
        LocalDateTime now = LocalDateTime.now();

        List<AssetServiceDto.AssetBalancePoint> points = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            LocalDate weekStart = firstMonday.plusWeeks(i);
            // 그 주 일요일 끝 시점(미래면 지금) 기준 잔액
            LocalDateTime asOf = weekStart.plusDays(6).atTime(LocalTime.MAX);
            if (asOf.isAfter(now)) {
                asOf = now;
            }
            long balance = resolver.balanceAt(asset.getRowId(), asOf);
            points.add(new AssetServiceDto.AssetBalancePoint(weekStart, balance));
        }
        return points;
    }

    /** 기준시각의 순자산 = Σ(비채무 잔액) − Σ|채무 잔액|. summary/trend 가 공유. */
    private long netWorthAt(List<Asset> included, BalanceResolver resolver, LocalDateTime at) {
        long assets = 0, debt = 0;
        for (Asset a : included) {
            long bal = resolver.balanceAt(a.getRowId(), at);
            if (DEBT_TYPES.contains(a.getAssetType())) {
                debt += Math.abs(bal);
            } else {
                assets += bal;
            }
        }
        return assets - debt;
    }

    private List<Asset> includedAssets(Long userRowId) {
        return assetRepository.findByUser(userRowId).stream()
            .filter(a -> a.getIsIncludedInTotal() == YNType.Y)
            .toList();
    }

    @Override
    @Transactional
    public void reorderAssets(Long userRowId, List<AssetServiceDto.ReorderItem> items) {
        log.debug("자산 정렬 변경: userRowId={}, count={}", userRowId, items.size());

        for (AssetServiceDto.ReorderItem item : items) {
            Asset asset = findAssetOrThrow(item.assetId());
            asset.updateSortOrder(item.sortOrder());
        }

        log.info("자산 정렬 변경 완료: userRowId={}", userRowId);
    }

    @Override
    @Transactional
    public AssetServiceDto.TransferInfo createTransfer(AssetServiceDto.CreateTransferCommand command) {
        log.debug("자산 이체 시작: from={}, to={}, amount={}", command.fromAssetRowId(), command.toAssetRowId(), command.amount());

        User user = userRepository.findById(command.userRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

        // 같은 자산으로의 이체는 무의미·잘못된 잔액 이력 유발 — 차단(ASSET_TRANSFER_SAME_ASSET 코드는 있었으나 미적용이었음).
        if (command.fromAssetRowId() != null
            && command.fromAssetRowId().equals(command.toAssetRowId())) {
            throw new InvalidValueException(DeskErrorCode.ASSET_TRANSFER_SAME_ASSET);
        }

        Asset fromAsset = findAssetOrThrow(command.fromAssetRowId());
        validateAssetOwnership(fromAsset, command.userRowId());
        Asset toAsset = findAssetOrThrow(command.toAssetRowId());
        validateAssetOwnership(toAsset, command.userRowId());

        AssetTransfer transfer = AssetTransfer.createTransfer(
            user, fromAsset, toAsset,
            command.amount(), command.fee(), command.description(), command.transferDate()
        );

        assetTransferRepository.save(transfer);
        // 자산 잔액 이력: 출금/입금 flow 2건 적재 → recompute 가 양쪽 잔액 반영 (단일 writer)
        balanceHistoryService.recordTransfer(transfer);
        log.info("자산 이체 완료: transferId={}", transfer.getRowId());

        return AssetServiceDto.TransferInfo.from(transfer);
    }

    @Override
    public List<AssetServiceDto.TransferInfo> getTransfers(Long userRowId, LocalDate startDate, LocalDate endDate) {
        log.debug("자산 이체 목록 조회: userRowId={}", userRowId);

        return assetTransferRepository.findByUser(userRowId, startDate, endDate).stream()
            .map(AssetServiceDto.TransferInfo::from)
            .toList();
    }

    @Override
    @Transactional
    public void deleteTransfer(Long transferId, Long userRowId) {
        log.debug("자산 이체 삭제 시작: transferId={}", transferId);

        AssetTransfer transfer = assetTransferRepository.findById(transferId)
            .orElseThrow(() -> {
                log.warn("자산 이체 조회 실패: transferId={}", transferId);
                return new EntityNotFoundException(DeskErrorCode.ASSET_TRANSFER_NOT_FOUND);
            });
        validateTransferOwnership(transfer, userRowId);

        transfer.deleteTransfer();
        // 자산 잔액 이력: 양쪽 flow soft-delete → recompute 가 양쪽 잔액 반영 (단일 writer)
        balanceHistoryService.removeTransfer(transferId);
        log.info("자산 이체 삭제 완료: transferId={}", transferId);
    }

    private CardCatalog resolveCardCatalog(Long cardCatalogRowId) {
        if (cardCatalogRowId == null) {
            return null;
        }
        return cardCatalogRepository.findById(cardCatalogRowId)
            .orElseThrow(() -> {
                log.warn("카드 카탈로그 조회 실패 - 존재하지 않는 카드: rowId={}", cardCatalogRowId);
                return new EntityNotFoundException(DeskErrorCode.CARD_CATALOG_NOT_FOUND);
            });
    }

    private Asset resolvePaymentAsset(Long paymentAssetRowId, Long userRowId) {
        if (paymentAssetRowId == null) {
            return null;
        }
        Asset paymentAsset = findAssetOrThrow(paymentAssetRowId);
        validateAssetOwnership(paymentAsset, userRowId);
        return paymentAsset;
    }

    private void validateAssetOwnership(Asset asset, Long userRowId) {
        if (!asset.getUser().getRowId().equals(userRowId)) {
            log.warn("자산 소유권 검증 실패 - assetId={}, ownerRowId={}, requestUserRowId={}",
                asset.getRowId(), asset.getUser().getRowId(), userRowId);
            throw new ForbiddenException(DeskErrorCode.ASSET_ACCESS_DENIED);
        }
    }

    private void validateTransferOwnership(AssetTransfer transfer, Long userRowId) {
        if (!transfer.getUser().getRowId().equals(userRowId)) {
            log.warn("자산 이체 소유권 검증 실패 - transferId={}, ownerRowId={}, requestUserRowId={}",
                transfer.getRowId(), transfer.getUser().getRowId(), userRowId);
            throw new ForbiddenException(DeskErrorCode.ASSET_ACCESS_DENIED);
        }
    }

    private Asset findAssetOrThrow(Long assetId) {
        return assetRepository.findById(assetId)
            .orElseThrow(() -> {
                log.warn("자산 조회 실패 - 존재하지 않는 자산: assetId={}", assetId);
                return new EntityNotFoundException(DeskErrorCode.ASSET_NOT_FOUND);
            });
    }
}
