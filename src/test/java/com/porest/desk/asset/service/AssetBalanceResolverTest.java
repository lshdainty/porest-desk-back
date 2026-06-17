package com.porest.desk.asset.service;

import com.porest.desk.asset.domain.AssetBalanceHistory;
import com.porest.desk.asset.service.AssetBalanceHistoryService.BalanceResolver;
import com.porest.desk.asset.type.BalanceSourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 자산 잔액 재계산 공식(BalanceResolver.balanceAt) 정상 동작 정확성 테스트.
 *
 * <p>잔액 = (기준시각 T 이하 최신 absolute 앵커 amount) + (그 앵커 이후 ~ T 이하 flow amount 합).
 * flow 부호는 도메인 규칙으로 직접 인코딩(EXPENSE=-, INCOME=+, transfer from=-(amount+fee)/to=+amount).
 * balanceAt 는 입력 리스트가 (effective_at, row_id) 오름차순 정렬됐다고 가정하므로 정렬해서 넣는다.
 */
class AssetBalanceResolverTest {

    private AssetBalanceHistory row(BalanceSourceType type, long amount, LocalDateTime at) {
        return AssetBalanceHistory.of(null, null, type, null, amount, at);
    }

    private BalanceResolver resolver(long assetRowId, List<AssetBalanceHistory> rows) {
        return new BalanceResolver(Map.of(assetRowId, rows));
    }

    private static final LocalDateTime T = LocalDateTime.of(2026, 6, 16, 0, 0);

    @Test
    @DisplayName("초기 0 → 지출 10,000 → 잔액 -10,000")
    void initThenExpense() {
        var r = resolver(1L, List.of(
                row(BalanceSourceType.INIT, 0L, LocalDateTime.of(2026, 6, 1, 0, 0)),
                row(BalanceSourceType.EXPENSE, -10_000L, LocalDateTime.of(2026, 6, 10, 0, 0))));
        assertThat(r.balanceAt(1L, T)).isEqualTo(-10_000L);
    }

    @Test
    @DisplayName("지출 후 수입 → 잔액 = -지출 + 수입 (flow 누적)")
    void expenseThenIncome() {
        var r = resolver(1L, List.of(
                row(BalanceSourceType.INIT, 0L, LocalDateTime.of(2026, 6, 1, 0, 0)),
                row(BalanceSourceType.EXPENSE, -10_000L, LocalDateTime.of(2026, 6, 5, 0, 0)),
                row(BalanceSourceType.EXPENSE, 30_000L, LocalDateTime.of(2026, 6, 8, 0, 0)))); // INCOME=+amount
        assertThat(r.balanceAt(1L, T)).isEqualTo(20_000L);
    }

    @Test
    @DisplayName("이체 부호 — from=-(amount+fee), to=+amount (수수료는 from 부담)")
    void transferSigns() {
        var from = resolver(1L, List.of(
                row(BalanceSourceType.INIT, 100_000L, LocalDateTime.of(2026, 6, 1, 0, 0)),
                row(BalanceSourceType.TRANSFER, -31_000L, LocalDateTime.of(2026, 6, 10, 0, 0)))); // -(30,000+1,000)
        var to = resolver(2L, List.of(
                row(BalanceSourceType.INIT, 0L, LocalDateTime.of(2026, 6, 1, 0, 0)),
                row(BalanceSourceType.TRANSFER, 30_000L, LocalDateTime.of(2026, 6, 10, 0, 0))));
        assertThat(from.balanceAt(1L, T)).isEqualTo(69_000L);
        assertThat(to.balanceAt(2L, T)).isEqualTo(30_000L);
    }

    @Test
    @DisplayName("MANUAL 절대 앵커가 이전 flow 를 무효화하고 점프시킨다")
    void manualAbsoluteReset() {
        var r = resolver(1L, List.of(
                row(BalanceSourceType.INIT, 0L, LocalDateTime.of(2026, 6, 1, 0, 0)),
                row(BalanceSourceType.EXPENSE, -50_000L, LocalDateTime.of(2026, 6, 3, 0, 0)),
                row(BalanceSourceType.MANUAL, 200_000L, LocalDateTime.of(2026, 6, 5, 0, 0)), // 리셋
                row(BalanceSourceType.EXPENSE, -10_000L, LocalDateTime.of(2026, 6, 8, 0, 0))));
        assertThat(r.balanceAt(1L, T)).isEqualTo(190_000L); // 200,000 - 10,000 (이전 -50,000 폐기)
    }

    @Test
    @DisplayName("기준시각 cutoff — at 이후 행은 제외(과거 시점 잔액)")
    void cutoffExcludesFuture() {
        var r = resolver(1L, List.of(
                row(BalanceSourceType.INIT, 0L, LocalDateTime.of(2026, 6, 1, 0, 0)),
                row(BalanceSourceType.EXPENSE, -10_000L, LocalDateTime.of(2026, 6, 5, 0, 0)),
                row(BalanceSourceType.EXPENSE, -20_000L, LocalDateTime.of(2026, 6, 20, 0, 0))));
        // 06-10 기준: 06-20 거래는 isAfter → 제외
        assertThat(r.balanceAt(1L, LocalDateTime.of(2026, 6, 10, 0, 0))).isEqualTo(-10_000L);
    }

    @Test
    @DisplayName("이력 없는 자산은 잔액 0")
    void noHistoryIsZero() {
        var r = resolver(1L, List.of(row(BalanceSourceType.INIT, 0L, LocalDateTime.of(2026, 6, 1, 0, 0))));
        assertThat(r.balanceAt(99L, T)).isEqualTo(0L); // 키 없음 → 0
    }
}
