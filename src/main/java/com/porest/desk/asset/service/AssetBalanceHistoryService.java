package com.porest.desk.asset.service;

import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.domain.AssetBalanceHistory;
import com.porest.desk.asset.domain.AssetTransfer;
import com.porest.desk.asset.repository.AssetBalanceHistoryRepository;
import com.porest.desk.asset.type.AssetType;
import com.porest.desk.asset.type.BalanceSourceType;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.core.time.UserClock;
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
import com.porest.desk.asset.repository.AssetRepository;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

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
    private final UserClock userClock;
    private final AssetRepository assetRepository;

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

    /** 투자 평가액 갱신 — 절대 앵커(점프). 토스 시세×수량 등 외부 평가액 반영 지점(추이용). */
    public void recordValuation(Asset asset, long valuation, LocalDateTime effectiveAt) {
        repository.save(AssetBalanceHistory.of(
            asset.getUser(), asset, BalanceSourceType.VALUATION, asset.getRowId(), valuation, effectiveAt));
        recompute(asset);
    }

    /** 수입/지출 거래 — flow(INCOME=+, EXPENSE=-). asset 미연결/널이면 no-op. */
    public void recordExpense(Asset asset, Long expenseId, ExpenseType type, Long amount, LocalDateTime effectiveAt) {
        recordExpense(asset, expenseId, type, amount, effectiveAt, true);
    }

    /**
     * @param recompute false 면 이력만 남기고 잔액 재산정을 미룬다(대량 적재용).
     *                  재산정은 그 자산의 <b>전체 이력을 다시 읽어</b> 계산하므로 행마다 하면
     *                  N 행에 대해 O(N²) 이 된다(1만 행이면 수천만 건 읽기). 호출자가 끝나고
     *                  {@link #recomputeAssets(Collection)} 로 자산당 한 번만 수행해야 한다.
     */
    public void recordExpense(Asset asset, Long expenseId, ExpenseType type, Long amount,
                              LocalDateTime effectiveAt, boolean recompute) {
        if (asset == null || type == null || amount == null) {
            return;
        }
        Asset target = balanceTargetOf(asset);
        long signed = (type == ExpenseType.INCOME) ? amount : -amount;
        repository.save(AssetBalanceHistory.of(
            asset.getUser(), target, BalanceSourceType.EXPENSE, expenseId, signed, effectiveAt));
        if (recompute) {
            recompute(target);
        }
    }

    /**
     * 잔액이 실제로 움직이는 자산.
     *
     * <p>체크카드는 계좌에 1:1 로 물린 결제 수단이라 자체 잔액이 없다 — 긁는 즉시 연결 계좌에서
     * 빠져나가므로 flow 를 카드가 아니라 그 계좌 앞으로 쌓는다. 카드 앞으로 쌓으면 통장 잔액이
     * 실제보다 많게 남는다(순자산 총액만 우연히 맞고 계좌·카드 배분이 전부 틀린다).
     *
     * <p>신용카드는 다르다 — 결제일에 {@code CardPaymentService} 가 이체로 몰아서 정산하므로
     * 그때까지 카드가 사용액을 들고 있어야 한다.
     *
     * <p>연결 계좌를 아직 안 고른 체크카드는 종전대로 카드 앞으로 — 지정 전 데이터를 잃지 않는다.
     */
    public Asset balanceTargetOf(Asset asset) {
        if (asset != null && asset.getAssetType() == AssetType.CHECK_CARD
            && asset.getPaymentAsset() != null) {
            return asset.getPaymentAsset();
        }
        return asset;
    }

    /**
     * 미뤄둔 잔액 재산정 — 자산 목록을 한 번의 이력 조회로 처리한다.
     *
     * <p>대량 적재(가져오기) 직후 호출한다. 넘어온 id 로 자산을 다시 읽는 이유는,
     * 적재 루프가 건별 트랜잭션이라 그때의 엔티티가 이미 detached 라서다 —
     * detached 엔티티에 잔액을 써도 반영되지 않는다.
     */
    @Transactional
    public void recomputeAssets(Collection<Long> assetRowIds) {
        if (assetRowIds == null || assetRowIds.isEmpty()) {
            return;
        }
        List<Asset> assets = assetRowIds.stream()
            .map(assetRepository::findById)
            .flatMap(Optional::stream)
            // 체크카드는 잔액이 연결 계좌에서 움직인다 — 재산정 대상도 그 계좌여야 한다.
            // 카드만 돌리면 flow 를 받은 통장이 갱신되지 않고 남는다.
            .map(this::balanceTargetOf)
            .distinct()
            .toList();
        if (assets.isEmpty()) {
            return;
        }
        BalanceResolver resolver = resolverFor(assets);
        for (Asset a : assets) {
            a.updateBalance(resolver.balanceAt(a.getRowId(), userClock.nowIn(a.getUser().getTimezone())));
        }
    }

    /**
     * 체크카드 연결 계좌를 지정·변경했을 때, 그 카드로 쓴 <b>기존</b> 지출 이력도 새 계좌로 옮긴다.
     *
     * <p>안 옮기면 과거 지출은 카드에, 신규 지출은 계좌에 쌓여 어느 쪽 잔액도 맞지 않는다.
     * 옮긴 뒤 이전 소속 자산까지 전부 재산정해야 빠져나간 만큼이 되돌려진다.
     */
    @Transactional
    public void relinkCheckCardHistory(Asset card, Asset newAccount) {
        if (card == null || newAccount == null || card.getAssetType() != AssetType.CHECK_CARD) {
            return;
        }
        List<AssetBalanceHistory> rows =
            repository.findActiveExpenseHistoryPaidWith(card.getRowId(), YNType.N);
        Set<Asset> affected = new LinkedHashSet<>();
        affected.add(card);
        affected.add(newAccount);
        for (AssetBalanceHistory h : rows) {
            affected.add(h.getAsset()); // 옮기기 전 소속 — 여기서도 빠져야 한다
            h.moveTo(newAccount);
        }
        for (Asset a : affected) {
            recompute(a);
        }
    }

    /** 거래 변경/삭제 시 해당 expense 의 이력 row 들을 soft-delete 후 영향 자산 재산정. */
    public void removeExpense(Long expenseId) {
        softDeleteAndRecompute(repository.findActiveBySource(BalanceSourceType.EXPENSE, expenseId, YNType.N));
    }

    /** 자산 이체 — 출금자산 -(amount+fee), 입금자산 +amount 의 flow 두 row. */
    public void recordTransfer(AssetTransfer transfer) {
        // transfer_date 가 DATETIME 이라 실제 시각 그대로 — 지출(expense_date)과 같은 기준.
        LocalDateTime effectiveAt = transfer.getTransferDate();
        long fee = transfer.getFee() != null ? transfer.getFee() : 0L;
        repository.save(AssetBalanceHistory.of(
            transfer.getUser(), transfer.getFromAsset(), BalanceSourceType.TRANSFER, transfer.getRowId(),
            -(transfer.getAmount() + fee), effectiveAt));
        // 입금 자산에는 원금만 — 이자는 부채를 줄이지 않고 은행으로 나가는 비용이라
        // 별도 지출 거래로 잡힌다(대출 상환에서만 0 보다 크다).
        repository.save(AssetBalanceHistory.of(
            transfer.getUser(), transfer.getToAsset(), BalanceSourceType.TRANSFER, transfer.getRowId(),
            transfer.principalAmount(), effectiveAt));
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
        // effective_at 은 사용자 벽시계 기준 컬럼(클라이언트가 보내는 거래 일시와 같은 축)이므로
        // 기준 시각도 사용자 타임존으로 잡는다. UTC 로 비교하면 오늘 거래가 미래로 취급된다.
        long balance = resolverFor(List.of(asset))
            .balanceAt(asset.getRowId(), userClock.nowIn(asset.getUser().getTimezone()));
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
