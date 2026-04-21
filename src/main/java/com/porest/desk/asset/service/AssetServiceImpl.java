package com.porest.desk.asset.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.domain.AssetTransfer;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.asset.repository.AssetTransferRepository;
import com.porest.desk.asset.service.dto.AssetServiceDto;
import com.porest.desk.asset.type.AssetType;
import com.porest.desk.card.domain.CardCatalog;
import com.porest.desk.card.repository.CardCatalogRepository;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final ExpenseRepository expenseRepository;

    @Override
    @Transactional
    public AssetServiceDto.AssetInfo createAsset(AssetServiceDto.CreateAssetCommand command) {
        log.debug("자산 등록 시작: userRowId={}, assetName={}", command.userRowId(), command.assetName());

        User user = userRepository.findById(command.userRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

        CardCatalog cardCatalog = resolveCardCatalog(command.cardCatalogRowId());

        Asset asset = Asset.createAsset(
            user,
            command.assetName(),
            command.assetType(),
            command.balance(),
            command.currency() != null ? command.currency() : "KRW",
            command.icon(),
            command.color(),
            command.institution(),
            command.memo(),
            command.sortOrder() != null ? command.sortOrder() : 0,
            cardCatalog
        );

        assetRepository.save(asset);
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

        asset.updateAsset(
            command.assetName(),
            command.assetType(),
            command.balance(),
            command.currency(),
            command.icon(),
            command.color(),
            command.institution(),
            command.memo(),
            command.isIncludedInTotal(),
            cardCatalog
        );

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
    public AssetServiceDto.AssetSummary getAssetSummary(Long userRowId) {
        log.debug("자산 요약 조회: userRowId={}", userRowId);

        List<Asset> assets = assetRepository.findByUser(userRowId);
        List<Asset> included = assets.stream()
            .filter(a -> a.getIsIncludedInTotal() == com.porest.core.type.YNType.Y)
            .toList();

        // 현재 자산 요약은 asset.balance (실시간) 사용
        Long totalBalance = included.stream().mapToLong(Asset::getBalance).sum();
        Long totalAssets = included.stream()
            .filter(a -> !DEBT_TYPES.contains(a.getAssetType()))
            .mapToLong(Asset::getBalance)
            .sum();
        Long totalDebt = included.stream()
            .filter(a -> DEBT_TYPES.contains(a.getAssetType()))
            .mapToLong(a -> Math.abs(a.getBalance()))
            .sum();
        long netWorth = totalAssets - totalDebt;

        // 지난달 말 순자산 — 월별 누적 집계(3쿼리)에서 마지막 완결 월 값 추출
        LocalDate today = LocalDate.now();
        LocalDate lastMonthEnd = today.withDayOfMonth(1).minusDays(1);
        // summary용 trend 데이터 — 이번달 포함 2개월이면 지난달 말 값만 뽑으면 됨
        Map<String, Long> monthlyNetWorth = buildMonthlyNetWorthMap(userRowId, included, lastMonthEnd, 1);
        String lastMonthKey = monthKey(lastMonthEnd.getYear(), lastMonthEnd.getMonthValue());
        long lastMonthNetWorth = monthlyNetWorth.getOrDefault(lastMonthKey, 0L);
        long changeAmount = netWorth - lastMonthNetWorth;
        double changePercent = lastMonthNetWorth == 0
            ? 0.0
            : Math.round(((double) changeAmount / Math.abs(lastMonthNetWorth)) * 1000.0) / 10.0;

        List<AssetServiceDto.AssetTypeSummary> byType = assets.stream()
            .collect(Collectors.groupingBy(Asset::getAssetType))
            .entrySet().stream()
            .map(entry -> new AssetServiceDto.AssetTypeSummary(
                entry.getKey(),
                entry.getValue().stream().mapToLong(Asset::getBalance).sum(),
                entry.getValue().size()
            ))
            .toList();

        return new AssetServiceDto.AssetSummary(
            totalBalance,
            totalAssets,
            totalDebt,
            netWorth,
            lastMonthNetWorth,
            changeAmount,
            changePercent,
            byType
        );
    }

    @Override
    public List<AssetServiceDto.NetWorthTrendPoint> getNetWorthTrend(Long userRowId, Integer months) {
        int n = (months == null || months < 1) ? 12 : Math.min(months, 36);
        log.debug("순자산 추이 조회: userRowId={}, months={}", userRowId, n);

        List<Asset> included = assetRepository.findByUser(userRowId).stream()
            .filter(a -> a.getIsIncludedInTotal() == com.porest.core.type.YNType.Y)
            .toList();

        LocalDate today = LocalDate.now();
        // 3쿼리 (expense 월별 + transfer_in 월별 + transfer_out 월별) + Java 누적으로 월 수 무관
        Map<String, Long> monthlyNetWorth = buildMonthlyNetWorthMap(userRowId, included, today, n - 1);

        java.util.List<AssetServiceDto.NetWorthTrendPoint> points = new java.util.ArrayList<>(n);
        for (int i = n - 1; i >= 0; i--) {
            LocalDate m = today.minusMonths(i);
            String key = monthKey(m.getYear(), m.getMonthValue());
            long nw = monthlyNetWorth.getOrDefault(key, 0L);
            points.add(new AssetServiceDto.NetWorthTrendPoint(m.getYear(), m.getMonthValue(), nw));
        }
        return points;
    }

    /**
     * 월별 순자산 맵 구축 (key = "yyyy-MM", value = 해당 월 말 순자산).
     * 쿼리 3회(expense monthly + transfer_in monthly + transfer_out monthly) + 자바 누적으로
     * 자산 수 N, 월 수 M 에 대해 N+1 없이 고정 쿼리 수 보장.
     *
     * @param endDate 포함할 가장 늦은 날짜 (이번달 = today, 지난달 = lastMonthEnd)
     * @param monthsBack endDate 기준으로 몇 달 전까지 trend 포함할지 (0 = 이번 달만)
     */
    private Map<String, Long> buildMonthlyNetWorthMap(Long userRowId, List<Asset> included,
                                                       LocalDate endDate, int monthsBack) {
        // 1) 자산 rowId → Asset
        Map<Long, Asset> byId = included.stream()
            .collect(Collectors.toMap(Asset::getRowId, a -> a));

        // 2) 3쿼리로 사용자 단위 월별 합계 일괄 조회
        List<Object[]> expenseRows = expenseRepository.sumMonthlyByUserGroupedByAssetAndType(userRowId, endDate);
        List<Object[]> transferInRows = assetTransferRepository.sumMonthlyTransferInByUserGroupedByAsset(userRowId, endDate);
        List<Object[]> transferOutRows = assetTransferRepository.sumMonthlyTransferOutByUserGroupedByAsset(userRowId, endDate);

        // 3) (assetRowId, yyyy-MM) → delta 맵 생성
        //    delta = INCOME + transfer_in − EXPENSE − transfer_out
        Map<Long, Map<String, Long>> deltaByAssetMonth = new java.util.HashMap<>();
        for (Object[] row : expenseRows) {
            Long assetRowId = ((Number) row[0]).longValue();
            int y = ((Number) row[1]).intValue();
            int mo = ((Number) row[2]).intValue();
            ExpenseType type = (ExpenseType) row[3];
            long amt = ((Number) row[4]).longValue();
            long signed = (type == ExpenseType.INCOME) ? amt : -amt;
            deltaByAssetMonth
                .computeIfAbsent(assetRowId, k -> new java.util.HashMap<>())
                .merge(monthKey(y, mo), signed, Long::sum);
        }
        for (Object[] row : transferInRows) {
            Long assetRowId = ((Number) row[0]).longValue();
            int y = ((Number) row[1]).intValue();
            int mo = ((Number) row[2]).intValue();
            long amt = ((Number) row[3]).longValue();
            deltaByAssetMonth
                .computeIfAbsent(assetRowId, k -> new java.util.HashMap<>())
                .merge(monthKey(y, mo), amt, Long::sum);
        }
        for (Object[] row : transferOutRows) {
            Long assetRowId = ((Number) row[0]).longValue();
            int y = ((Number) row[1]).intValue();
            int mo = ((Number) row[2]).intValue();
            long amt = ((Number) row[3]).longValue();
            deltaByAssetMonth
                .computeIfAbsent(assetRowId, k -> new java.util.HashMap<>())
                .merge(monthKey(y, mo), -amt, Long::sum);
        }

        // 4) 자산별 월 순차 누적 → 각 자산의 (월 → 말일 잔액) 맵
        //    출력은 월별 순자산 합계만 필요하므로 자산 단위 map 대신 직접 누적
        Map<String, Long> monthlyNetWorth = new java.util.HashMap<>();

        // 반복할 월 범위: endDate 기준 monthsBack부터 endDate 월까지
        java.util.List<String> monthKeys = new java.util.ArrayList<>(monthsBack + 1);
        for (int i = monthsBack; i >= 0; i--) {
            LocalDate m = endDate.minusMonths(i);
            monthKeys.add(monthKey(m.getYear(), m.getMonthValue()));
        }

        for (Asset asset : included) {
            Long assetRowId = asset.getRowId();
            Map<String, Long> deltas = deltaByAssetMonth.getOrDefault(assetRowId, java.util.Collections.emptyMap());

            // 해당 자산의 earliest 거래 월부터 누적 (범위 밖 월도 포함해서 running 을 잡기 위함)
            // deltas 키를 정렬
            java.util.List<String> allKeys = new java.util.ArrayList<>(deltas.keySet());
            java.util.Collections.sort(allKeys);

            long running = asset.getInitialBalance() != null ? asset.getInitialBalance() : 0L;
            int sign = DEBT_TYPES.contains(asset.getAssetType()) ? -1 : +1;

            // trend 시작월 이전의 모든 delta는 running에 선반영
            String earliestInRange = monthKeys.get(0);
            int k = 0;
            while (k < allKeys.size() && allKeys.get(k).compareTo(earliestInRange) < 0) {
                running += deltas.get(allKeys.get(k));
                k++;
            }

            // trend 범위 월별 순차 누적
            for (String mk : monthKeys) {
                // 그 월에 해당하는 delta 를 찾아 running 에 더함
                if (k < allKeys.size() && allKeys.get(k).equals(mk)) {
                    running += deltas.get(mk);
                    k++;
                }
                // 부호 적용 후 해당 월 총합에 누적
                long contribution = (sign > 0) ? running : -Math.abs(running);
                monthlyNetWorth.merge(mk, contribution, Long::sum);
            }
        }

        return monthlyNetWorth;
    }

    private static String monthKey(int year, int month) {
        return String.format("%04d-%02d", year, month);
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

        Asset fromAsset = findAssetOrThrow(command.fromAssetRowId());
        validateAssetOwnership(fromAsset, command.userRowId());
        Asset toAsset = findAssetOrThrow(command.toAssetRowId());
        validateAssetOwnership(toAsset, command.userRowId());

        AssetTransfer transfer = AssetTransfer.createTransfer(
            user, fromAsset, toAsset,
            command.amount(), command.fee(), command.description(), command.transferDate()
        );

        // 잔액 업데이트
        Long fee = command.fee() != null ? command.fee() : 0L;
        fromAsset.updateBalance(fromAsset.getBalance() - command.amount() - fee);
        toAsset.updateBalance(toAsset.getBalance() + command.amount());

        assetTransferRepository.save(transfer);
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

        // 잔액 복원
        Asset fromAsset = transfer.getFromAsset();
        Asset toAsset = transfer.getToAsset();
        fromAsset.updateBalance(fromAsset.getBalance() + transfer.getAmount() + transfer.getFee());
        toAsset.updateBalance(toAsset.getBalance() - transfer.getAmount());

        transfer.deleteTransfer();
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
