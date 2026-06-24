package com.porest.desk.asset.service;

import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.domain.AssetBalanceHistory;
import com.porest.desk.asset.domain.AssetTransfer;
import com.porest.desk.asset.repository.AssetBalanceHistoryRepository;
import com.porest.desk.asset.type.BalanceSourceType;
import com.porest.desk.expense.type.ExpenseType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 자산 잔액 이력(asset_balance_history) 적재 + 기준시각 잔액 조회 (단일 진실 공급원).
 *
 * <p>잔액이 바뀌는 모든 서비스 경로에서 이 컴포넌트로 이력을 남긴다. 적재/삭제 직후
 * 해당 자산의 {@code asset.balance} 를 {@code balanceAt(now)} 로 재산정하므로,
 * 개별 자산 잔액 · summary · trend · byType 이 전부 이력이라는 한 소스에서 나와 항상 일치한다.
 * 조회는 {@link BalanceResolver} 로 "기준시각 이하 최신 절대 앵커 + 그 이후 flow 합" 을 계산한다.
 * 호출자(@Transactional)의 트랜잭션에 합류한다.
 */
@Service
@RequiredArgsConstructor
public class AssetBalanceHistoryService {

    private final AssetBalanceHistoryRepository repository;

    // === 쓰기 ===

    /** 자산 생성 — 초기 잔액 절대 앵커. */
    public void recordInit(Asset asset, LocalDateTime effectiveAt) {
        long initial = asset.getInitialBalance() != null ? asset.getInitialBalance() : 0L;
        repository.save(AssetBalanceHistory.of(
            asset.getUser(), asset, BalanceSourceType.INIT, asset.getRowId(), initial, effectiveAt));
        recompute(asset);
    }

    /** 사용자의 수동 잔액 수정 — 절대 앵커(점프). 가계부 통계엔 영향 없음. */
    public void recordManual(Asset asset, long newBalance, LocalDateTime effectiveAt) {
        repository.save(AssetBalanceHistory.of(
            asset.getUser(), asset, BalanceSourceType.MANUAL, asset.getRowId(), newBalance, effectiveAt));
        recompute(asset);
    }

    /** 투자 평가액 갱신 — 절대 앵커. 토스 연동 자산의 종가 평가액을 하루 1회 스냅샷으로 적재(추이 반영). */
    public void recordValuation(Asset asset, long valuation, LocalDateTime effectiveAt) {
        repository.save(AssetBalanceHistory.of(
            asset.getUser(), asset, BalanceSourceType.VALUATION, asset.getRowId(), valuation, effectiveAt));
        recompute(asset);
    }

    /** 수입/지출 거래 — flow(INCOME=+, EXPENSE=-). asset 미연결/널이면 no-op. */
    public void recordExpense(Asset asset, Long expenseId, ExpenseType type, Long amount, LocalDateTime effectiveAt) {
        if (asset == null || type == null || amount == null) {
            return;
        }
        long signed = (type == ExpenseType.INCOME) ? amount : -amount;
        repository.save(AssetBalanceHistory.of(
            asset.getUser(), asset, BalanceSourceType.EXPENSE, expenseId, signed, effectiveAt));
        recompute(asset);
    }

    /** 거래 변경/삭제 시 해당 expense 의 이력 row 들을 soft-delete 후 영향 자산 재산정. */
    public void removeExpense(Long expenseId) {
        softDeleteAndRecompute(repository.findActiveBySource(BalanceSourceType.EXPENSE, expenseId, YNType.N));
    }

    /** 자산 이체 — 출금자산 -(amount+fee), 입금자산 +amount 의 flow 두 row. */
    public void recordTransfer(AssetTransfer transfer) {
        LocalDateTime effectiveAt = transfer.getTransferDate().atStartOfDay();
        long fee = transfer.getFee() != null ? transfer.getFee() : 0L;
        repository.save(AssetBalanceHistory.of(
            transfer.getUser(), transfer.getFromAsset(), BalanceSourceType.TRANSFER, transfer.getRowId(),
            -(transfer.getAmount() + fee), effectiveAt));
        repository.save(AssetBalanceHistory.of(
            transfer.getUser(), transfer.getToAsset(), BalanceSourceType.TRANSFER, transfer.getRowId(),
            transfer.getAmount(), effectiveAt));
        recompute(transfer.getFromAsset());
        recompute(transfer.getToAsset());
    }

    /** 이체 삭제 시 양쪽 자산의 이력 row 를 soft-delete 후 영향 자산 재산정. */
    public void removeTransfer(Long transferId) {
        softDeleteAndRecompute(repository.findActiveBySource(BalanceSourceType.TRANSFER, transferId, YNType.N));
    }

    private void softDeleteAndRecompute(List<AssetBalanceHistory> rows) {
        Set<Asset> affected = new LinkedHashSet<>();
        for (AssetBalanceHistory h : rows) {
            h.softDelete();
            affected.add(h.getAsset());
        }
        for (Asset a : affected) {
            recompute(a);
        }
    }

    /** 해당 자산의 현재 잔액(balanceAt(now))을 이력으로부터 재산정해 asset.balance 캐시에 반영. */
    private void recompute(Asset asset) {
        if (asset == null) {
            return;
        }
        long balance = resolverFor(List.of(asset)).balanceAt(asset.getRowId(), LocalDateTime.now());
        asset.updateBalance(balance);
    }

    // === 읽기 ===

    /** 주어진 자산들의 이력을 1회 조회해 기준시각 잔액 계산기를 만든다. */
    public BalanceResolver resolverFor(Collection<Asset> assets) {
        List<Long> ids = assets.stream().map(Asset::getRowId).toList();
        List<AssetBalanceHistory> rows = ids.isEmpty()
            ? List.of()
            : repository.findActiveByAssetIds(ids, YNType.N);
        Map<Long, List<AssetBalanceHistory>> byAsset = rows.stream()
            .collect(Collectors.groupingBy(h -> h.getAsset().getRowId(), LinkedHashMap::new, Collectors.toList()));
        return new BalanceResolver(byAsset);
    }

    /**
     * 기준시각 잔액 계산기. 자산별 이력(시각 오름차순)을 들고 있다가
     * balanceAt(asset, T) = (T 이하 최신 절대 앵커).amount + 그 이후 flow 합 을 반환.
     */
    public static class BalanceResolver {
        private final Map<Long, List<AssetBalanceHistory>> byAsset;

        BalanceResolver(Map<Long, List<AssetBalanceHistory>> byAsset) {
            this.byAsset = byAsset;
        }

        public long balanceAt(Long assetRowId, LocalDateTime at) {
            List<AssetBalanceHistory> rows = byAsset.get(assetRowId);
            if (rows == null) {
                return 0L;
            }
            long running = 0L;
            for (AssetBalanceHistory h : rows) { // effective_at, row_id 오름차순
                if (h.getEffectiveAt().isAfter(at)) {
                    break;
                }
                if (h.isAbsolute()) {
                    running = h.getAmount();
                } else {
                    running += h.getAmount();
                }
            }
            return running;
        }
    }
}
