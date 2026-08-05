package com.porest.desk.asset.service;

import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.domain.AssetBalanceHistory;
import com.porest.desk.asset.domain.AssetTransfer;
import com.porest.desk.asset.repository.AssetBalanceHistoryRepository;
import com.porest.desk.asset.type.AssetType;
import com.porest.desk.asset.type.BalanceChannel;
import com.porest.desk.asset.type.BalanceSourceType;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.core.time.UserClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.TreeMap;
import java.util.ArrayList;
import java.time.LocalDate;
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

    /**
     * 투자 평가액 갱신 — HOLDING 채널의 절대 앵커(점프). 토스 시세×수량 등 외부 평가액 반영 지점.
     *
     * <p>예수금 채널을 건드리지 않는다 — 증권계좌로 들어온 이체는 평가액과 무관하게 남아야 한다.
     */
    public void recordValuation(Asset asset, long valuation, LocalDateTime effectiveAt) {
        repository.save(AssetBalanceHistory.of(
            asset.getUser(), asset, BalanceSourceType.VALUATION, BalanceChannel.HOLDING,
            asset.getRowId(), valuation, effectiveAt));
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
        applySplits(assets);
    }

    /**
     * 그 사용자의 모든 자산을 이력에서 다시 계산해 캐시(balance·예수금·평가금액)에 반영한다.
     *
     * <p>스키마가 바뀌어 캐시 컬럼이 비어 있거나(신규 컬럼은 DEFAULT 0 으로 생긴다),
     * 이력을 직접 손봐 캐시와 어긋났을 때 되맞추는 수단이다. 이력이 진실이고 캐시는 파생이라
     * 몇 번 돌려도 결과가 같다.
     *
     * @return 다시 계산한 자산 수
     */
    @Transactional
    public int recomputeAllForUser(Long userRowId) {
        List<Asset> assets = assetRepository.findByUser(userRowId);
        applySplits(assets);
        return assets.size();
    }

    /**
     * 이력 한 번 읽어 자산들의 채널별 잔액을 채운다.
     *
     * <p>{@link Asset#updateBalance(Long)} 처럼 총액만 넣으면 전액이 예수금으로 몰리고
     * 평가금액이 0 이 된다 — 총액은 맞아도 화면이 틀린다(웹 자산 목록은
     * {@code balance = cashBalance + 라이브 평가액} 으로 다시 조립한다).
     */
    private void applySplits(List<Asset> assets) {
        if (assets.isEmpty()) {
            return;
        }
        BalanceResolver resolver = resolverFor(assets);
        for (Asset a : assets) {
            Split split = resolver.splitAt(a.getRowId(), userClock.nowIn(a.getUser().getTimezone()));
            a.updateBalances(split.cash(), split.holding());
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

    /**
     * 매수·매도 — 예수금(CASH) flow. 평가금액은 건드리지 않는다.
     *
     * <p>매수는 대금과 수수료가 함께 빠지고, 매도는 수수료를 뗀 나머지가 들어온다.
     * 보유 평가금액은 시세×수량으로 따로 산정되므로 여기서 손대면 이중 계상이 된다.
     */
    public void recordTrade(Asset asset, Long tradeId, Long cashDelta, LocalDateTime effectiveAt) {
        if (asset == null || cashDelta == null || cashDelta == 0L) {
            return;
        }
        repository.save(AssetBalanceHistory.of(
            asset.getUser(), asset, BalanceSourceType.TRADE, BalanceChannel.CASH,
            tradeId, cashDelta, effectiveAt));
        recompute(asset);
    }

    /** 거래 취소 시 그 거래의 이력 row 를 soft-delete 후 자산 재산정. */
    public void removeTrade(Long tradeId) {
        softDeleteAndRecompute(repository.findActiveBySource(BalanceSourceType.TRADE, tradeId, YNType.N));
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
        applySplits(List.of(asset));
    }


    // === 읽기 (집계 쿼리) =======================================================

    /**
     * 기준시각의 자산별 잔액 — <b>DB 가 집계</b>한다. 이력을 앱으로 가져오지 않는다.
     *
     * @return 자산 rowId → 채널별 잔액. 이력이 없는 자산은 {@link Split#ZERO}.
     */
    public Map<Long, Split> balancesAt(Collection<Asset> assets, LocalDateTime at) {
        List<Long> ids = assets.stream().map(Asset::getRowId).filter(java.util.Objects::nonNull).toList();
        return balancesAtByIds(ids, at);
    }

    public Map<Long, Split> balancesAtByIds(Collection<Long> assetRowIds, LocalDateTime at) {
        Map<Long, Split> result = new LinkedHashMap<>();
        for (Long id : assetRowIds) {
            result.put(id, Split.ZERO);
        }
        if (assetRowIds.isEmpty()) {
            return result;
        }
        for (Object[] row : repository.aggregateBalances(assetRowIds, at)) {
            Long assetId = ((Number) row[0]).longValue();
            BalanceChannel channel = BalanceChannel.valueOf((String) row[1]);
            long amount = ((Number) row[2]).longValue();
            result.merge(assetId, channelSplit(channel, amount), Split::plus);
        }
        return result;
    }

    /** 한 자산의 기준시각 잔액. */
    public Split balanceAt(Asset asset, LocalDateTime at) {
        return balancesAt(List.of(asset), at).getOrDefault(asset.getRowId(), Split.ZERO);
    }

    /**
     * 여러 시점의 자산별 잔액 — 추이 그래프용. <b>쿼리 2회</b>로 끝난다.
     *
     * <p>첫 시점 잔액을 집계로 한 번 잡고, 그 뒤는 일자별 변동을 한 번에 받아 자바에서 누적한다.
     * 시점마다 쿼리를 날리면 12개월에 12번·26주에 26번이 된다.
     *
     * @param points 오름차순 시점들. 마지막 시점 이후 기록은 어디에도 안 들어간다.
     * @return 자산 rowId → 시점별 잔액(points 와 같은 순서·길이)
     */
    public Map<Long, List<Split>> balancesAtPoints(Collection<Asset> assets, List<LocalDateTime> points) {
        List<Long> ids = assets.stream().map(Asset::getRowId).filter(java.util.Objects::nonNull).toList();
        if (points.isEmpty()) {
            return Map.of();
        }
        LocalDateTime first = points.get(0);
        LocalDateTime last = points.get(points.size() - 1);

        Map<Long, Split> running = new LinkedHashMap<>(balancesAtByIds(ids, first));
        Map<Long, List<Split>> out = new LinkedHashMap<>();
        for (Long id : ids) {
            List<Split> seq = new ArrayList<>(points.size());
            seq.add(running.getOrDefault(id, Split.ZERO));
            out.put(id, seq);
        }
        if (ids.isEmpty() || points.size() == 1) {
            return out;
        }

        // 일자별 변동 — 자산·채널·날짜 오름차순으로 돌아온다.
        List<Object[]> deltas = repository.aggregateDailyDeltas(ids, first, last);
        // 날짜 → 그 날짜의 변동들. 시점 경계를 넘길 때마다 스냅샷을 찍는다.
        Map<LocalDate, List<Object[]>> byDate = new TreeMap<>();
        for (Object[] row : deltas) {
            byDate.computeIfAbsent(toLocalDate(row[2]), k -> new ArrayList<>()).add(row);
        }

        int idx = 1; // points.get(0) 은 이미 채웠다
        for (Map.Entry<LocalDate, List<Object[]>> e : byDate.entrySet()) {
            LocalDate date = e.getKey();
            // 이 날짜보다 앞선 시점들은 지금까지의 누적으로 확정한다.
            while (idx < points.size() && points.get(idx).toLocalDate().isBefore(date)) {
                snapshot(out, ids, running, idx);
                idx++;
            }
            for (Object[] row : e.getValue()) {
                apply(running, row);
            }
        }
        while (idx < points.size()) {
            snapshot(out, ids, running, idx);
            idx++;
        }
        return out;
    }

    private void snapshot(Map<Long, List<Split>> out, Collection<Long> ids,
                          Map<Long, Split> running, int idx) {
        for (Long id : ids) {
            out.get(id).add(running.getOrDefault(id, Split.ZERO));
        }
    }

    /** 앵커가 있는 날은 그 값으로 갈아엎고, 없는 날은 flow 를 더한다. */
    private void apply(Map<Long, Split> running, Object[] row) {
        Long assetId = ((Number) row[0]).longValue();
        BalanceChannel channel = BalanceChannel.valueOf((String) row[1]);
        Long anchor = row[3] != null ? ((Number) row[3]).longValue() : null;
        long flowAfter = row[4] != null ? ((Number) row[4]).longValue() : 0L;

        Split cur = running.getOrDefault(assetId, Split.ZERO);
        long cash = cur.cash();
        long holding = cur.holding();
        long next = anchor != null ? anchor + flowAfter
            : (channel == BalanceChannel.HOLDING ? holding : cash) + flowAfter;
        running.put(assetId, channel == BalanceChannel.HOLDING
            ? new Split(cash, next) : new Split(next, holding));
    }

    private static Split channelSplit(BalanceChannel channel, long amount) {
        return channel == BalanceChannel.HOLDING ? new Split(0L, amount) : new Split(amount, 0L);
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        if (value instanceof LocalDate d) {
            return d;
        }
        return LocalDate.parse(value.toString());
    }

    // === 읽기 (자바 계산 — 대조용, 곧 제거) ===

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
     * 기준시각 잔액 계산기. 자산별 이력(시각 오름차순)을 들고 있다가 채널별로 따로 산정해 합친다.
     *
     * <pre>
     *   balanceAt(asset, T) = cashAt(T) + holdingAt(T)
     *   cashAt(T)    = (T 이하 최신 CASH 절대 앵커).amount + 그 이후 CASH flow 합
     *   holdingAt(T) = (T 이하 최신 HOLDING 절대 앵커).amount
     * </pre>
     *
     * <p>채널을 나누기 전에는 평가액 앵커가 그 앞의 이체 flow 를 통째로 삼켜서,
     * 증권계좌로 넣은 돈이 다음 평가 스냅샷에 조용히 사라졌다.
     */
    public static class BalanceResolver {
        private final Map<Long, List<AssetBalanceHistory>> byAsset;

        BalanceResolver(Map<Long, List<AssetBalanceHistory>> byAsset) {
            this.byAsset = byAsset;
        }

        /** 이력 맵으로 직접 만든다 — 집계 쿼리와 값이 같은지 대조하는 테스트에서 쓴다. */
        public static BalanceResolver of(Map<Long, List<AssetBalanceHistory>> byAsset) {
            return new BalanceResolver(byAsset);
        }

        /** 총잔액 = 예수금 + 평가금액. */
        public long balanceAt(Long assetRowId, LocalDateTime at) {
            Split s = splitAt(assetRowId, at);
            return s.cash() + s.holding();
        }

        /** 채널별 잔액. 투자 자산의 예수금을 따로 보여 줄 때 쓴다. */
        public Split splitAt(Long assetRowId, LocalDateTime at) {
            List<AssetBalanceHistory> rows = byAsset.get(assetRowId);
            if (rows == null) {
                return Split.ZERO;
            }
            long cash = 0L;
            long holding = 0L;
            for (AssetBalanceHistory h : rows) { // effective_at, row_id 오름차순
                if (h.getEffectiveAt().isAfter(at)) {
                    break;
                }
                if (h.isHolding()) {
                    // 평가금액은 통째 갱신이라 절대 앵커만 있다. flow 가 섞여 들어와도
                    // 예수금을 건드리지 않도록 같은 채널 안에서 처리한다.
                    holding = h.isAbsolute() ? h.getAmount() : holding + h.getAmount();
                } else if (h.isAbsolute()) {
                    cash = h.getAmount();
                } else {
                    cash += h.getAmount();
                }
            }
            return new Split(cash, holding);
        }
    }

    /** 채널별 잔액 — 총잔액은 둘의 합. */
    public record Split(long cash, long holding) {
        public static final Split ZERO = new Split(0L, 0L);

        public long total() {
            return cash + holding;
        }

        /** 채널별로 더한다 — 집계 결과가 채널마다 한 행씩 오므로 합쳐 하나로 만든다. */
        public Split plus(Split other) {
            return new Split(cash + other.cash, holding + other.holding);
        }
    }
}
