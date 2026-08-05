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
 * 조회는 <b>SQL 집계</b>로 "기준시각 이하 최신 절대 앵커 + 그 이후 flow 합" 을 구한다 —
 * 이력을 앱으로 가져와 접지 않는다. 자산에 잔액 캐시 컬럼도 두지 않는다(금액을 낡은 값으로
 * 판단하는 사고가 반복돼서 없앴다).
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
            asset.getUser(), asset, BalanceSourceType.INIT, asset.getRowId(),
            normalizeAnchor(asset, initial), effectiveAt));
    }

    /** 사용자의 수동 잔액 수정 — 절대 앵커(점프). 가계부 통계엔 영향 없음. */
    public void recordManual(Asset asset, long newBalance, LocalDateTime effectiveAt) {
        repository.save(AssetBalanceHistory.of(
            asset.getUser(), asset, BalanceSourceType.MANUAL, asset.getRowId(),
            normalizeAnchor(asset, newBalance), effectiveAt));
    }

    /**
     * 사용자가 넣은 절대 잔액을 규약에 맞춰 정규화한다.
     *
     * <p>신용카드는 <b>미결제 사용액이 음수</b>다. 잔액을 움직이는 모든 경로가 이미 그 규약을
     * 따른다 — 결제하면 {@code -amount}, 환불하면 {@code +amount}, 대금을 갚으면 {@code +원금}
     * 이라 0 으로 수렴한다. 화면은 "현재 사용액" 을 묻고 사용자는 당연히 양수를 치므로,
     * 막지 않고 여기서 뒤집는다.
     *
     * <p>여기서 안 잡으면 부호가 섞인다 — 잔액 자체는 부호에 무관한 소비자(순자산 {@code abs},
     * 청구액, 한도 게이지)를 통과해 버리고, 부호를 그대로 더하는 화면 합계에서만 어긋난다.
     */
    private long normalizeAnchor(Asset asset, long amount) {
        return asset != null && asset.getAssetType() == AssetType.CREDIT_CARD
            ? -Math.abs(amount)
            : amount;
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
    }

    /** 수입/지출 거래 — flow(INCOME=+, EXPENSE=-). asset 미연결/널이면 no-op. */
    public void recordExpense(Asset asset, Long expenseId, ExpenseType type, Long amount,
                              LocalDateTime effectiveAt) {
        if (asset == null || type == null || amount == null) {
            return;
        }
        Asset target = balanceTargetOf(asset);
        long signed = (type == ExpenseType.INCOME) ? amount : -amount;
        repository.save(AssetBalanceHistory.of(
            asset.getUser(), target, BalanceSourceType.EXPENSE, expenseId, signed, effectiveAt));
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
        }
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
