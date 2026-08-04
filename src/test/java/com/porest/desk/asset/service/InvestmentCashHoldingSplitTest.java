package com.porest.desk.asset.service;

import com.porest.desk.asset.domain.AssetBalanceHistory;
import com.porest.desk.asset.service.AssetBalanceHistoryService.BalanceResolver;
import com.porest.desk.asset.service.AssetBalanceHistoryService.Split;
import com.porest.desk.asset.type.BalanceChannel;
import com.porest.desk.asset.type.BalanceSourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 투자 계좌의 예수금/평가금액 분리 — 실제 증권 계좌 사용 흐름을 그대로 넣어 검증한다.
 *
 * <p>분리 전에는 평가액이 절대 앵커로 찍히면서 <b>그 앞의 이체를 통째로 삼켰다.</b>
 * 증권계좌에 100만원을 넣어도 그날 16시 스냅샷이 도는 순간 사라지고, 보내는 쪽 계좌에서는
 * 이미 빠져 있어 순자산이 100만원 줄어든 채로 고정됐다.
 *
 * <p>여기 테스트는 전부 "예수금과 평가금액은 서로를 덮지 않는다" 를 각도만 바꿔 확인한다.
 */
@DisplayName("투자 계좌 예수금/평가금액 분리")
class InvestmentCashHoldingSplitTest {

    private static final long ACC = 1L;

    /** 시각 오름차순으로 넣어 주는 빌더 — resolver 는 정렬된 입력을 가정한다. */
    private static class History {
        private final List<AssetBalanceHistory> rows = new ArrayList<>();

        History cash(BalanceSourceType type, long amount, LocalDateTime at) {
            rows.add(AssetBalanceHistory.of(null, null, type, BalanceChannel.CASH, null, amount, at));
            return this;
        }

        History holding(long valuation, LocalDateTime at) {
            rows.add(AssetBalanceHistory.of(null, null, BalanceSourceType.VALUATION,
                BalanceChannel.HOLDING, null, valuation, at));
            return this;
        }

        BalanceResolver build() {
            rows.sort(Comparator.comparing(AssetBalanceHistory::getEffectiveAt));
            return new BalanceResolver(Map.of(ACC, List.copyOf(rows)));
        }
    }

    private static LocalDateTime at(int day, int hour) {
        return LocalDateTime.of(2026, 8, day, hour, 0);
    }

    @Nested
    @DisplayName("사라지던 이체 — 회귀 방지")
    class TransferSurvives {

        /**
         * 실제로 보고된 증상 그대로:
         * 8/3 16:00 평가 80만 → 8/4 09:00 100만 입금 → 8/4 16:00 평가 스냅샷(80만).
         * 분리 전에는 마지막 앵커 80만이 이체 flow 를 삼켜 잔액이 80만으로 돌아갔다.
         */
        @Test
        @DisplayName("입금 후 평가 스냅샷이 돌아도 예수금 100만원이 남는다")
        void snapshotDoesNotEatTransfer() {
            BalanceResolver r = new History()
                .cash(BalanceSourceType.INIT, 0L, at(1, 9))
                .holding(800_000L, at(3, 16))            // 삼성전자 10주 × 8만
                .cash(BalanceSourceType.TRANSFER, 1_000_000L, at(4, 9))
                .holding(800_000L, at(4, 16))            // 수량 그대로라 평가액 동일
                .build();

            Split s = r.splitAt(ACC, at(4, 23));
            assertThat(s.cash()).isEqualTo(1_000_000L);   // 매수 대기 자금
            assertThat(s.holding()).isEqualTo(800_000L);  // 보유 평가금액
            assertThat(s.total()).isEqualTo(1_800_000L);
        }

        @Test
        @DisplayName("평가 스냅샷을 30일 돌려도 예수금은 그대로다")
        void manySnapshotsKeepCash() {
            History h = new History()
                .cash(BalanceSourceType.INIT, 0L, at(1, 9))
                .cash(BalanceSourceType.TRANSFER, 1_000_000L, at(1, 10));
            for (int d = 1; d <= 30; d++) {
                h.holding(800_000L + d, at(d, 16));
            }

            Split s = h.build().splitAt(ACC, at(30, 23));
            assertThat(s.cash()).isEqualTo(1_000_000L);
            assertThat(s.holding()).isEqualTo(800_030L);
            assertThat(s.total()).isEqualTo(1_800_030L);
        }

        @Test
        @DisplayName("자산 편집(평가액 재산정)이 이체를 지우지 않는다 — 메모만 고쳐도 안전")
        void assetEditDoesNotEatTransfer() {
            BalanceResolver r = new History()
                .cash(BalanceSourceType.INIT, 0L, at(1, 9))
                .holding(800_000L, at(1, 9))
                .cash(BalanceSourceType.TRANSFER, 1_000_000L, at(4, 9))
                .holding(800_000L, at(4, 11))            // 편집 저장 → 평가액 재산정
                .build();

            assertThat(r.balanceAt(ACC, at(4, 23))).isEqualTo(1_800_000L);
        }
    }

    @Nested
    @DisplayName("증권 계좌 사용 흐름")
    class BrokerageFlow {

        @Test
        @DisplayName("입금 100만 → 60만어치 매수 → 예수금 40만 + 평가 60만 = 100만")
        void depositThenBuy() {
            BalanceResolver r = new History()
                .cash(BalanceSourceType.INIT, 0L, at(1, 9))
                .cash(BalanceSourceType.TRANSFER, 1_000_000L, at(1, 10))
                // 매수 = 예수금이 나가고(지출 flow) 보유가 생긴다
                .cash(BalanceSourceType.EXPENSE, -600_000L, at(2, 10))
                .holding(600_000L, at(2, 10))
                .build();

            Split s = r.splitAt(ACC, at(2, 23));
            assertThat(s.cash()).isEqualTo(400_000L);
            assertThat(s.holding()).isEqualTo(600_000L);
            assertThat(s.total()).isEqualTo(1_000_000L);  // 매수는 총액을 바꾸지 않는다
        }

        @Test
        @DisplayName("주가가 오르면 평가금액만 오른다 — 예수금 40만은 그대로")
        void priceRiseMovesOnlyHolding() {
            BalanceResolver r = new History()
                .cash(BalanceSourceType.INIT, 0L, at(1, 9))
                .cash(BalanceSourceType.TRANSFER, 1_000_000L, at(1, 10))
                .cash(BalanceSourceType.EXPENSE, -600_000L, at(2, 10))
                .holding(600_000L, at(2, 10))
                .holding(750_000L, at(5, 16))            // +25%
                .build();

            Split s = r.splitAt(ACC, at(5, 23));
            assertThat(s.cash()).isEqualTo(400_000L);
            assertThat(s.holding()).isEqualTo(750_000L);
            assertThat(s.total()).isEqualTo(1_150_000L);
        }

        @Test
        @DisplayName("전량 매도 → 평가 0, 예수금에 매도대금이 들어온다")
        void sellAll() {
            BalanceResolver r = new History()
                .cash(BalanceSourceType.INIT, 0L, at(1, 9))
                .cash(BalanceSourceType.TRANSFER, 1_000_000L, at(1, 10))
                .cash(BalanceSourceType.EXPENSE, -600_000L, at(2, 10))
                .holding(600_000L, at(2, 10))
                .holding(750_000L, at(5, 16))
                // 매도 = 보유가 사라지고 예수금이 들어온다
                .cash(BalanceSourceType.EXPENSE, 750_000L, at(6, 10))
                .holding(0L, at(6, 10))
                .build();

            Split s = r.splitAt(ACC, at(6, 23));
            assertThat(s.cash()).isEqualTo(1_150_000L);
            assertThat(s.holding()).isZero();
            assertThat(s.total()).isEqualTo(1_150_000L);
        }

        @Test
        @DisplayName("출금 — 예수금에서 은행으로 50만 빼면 예수금만 준다")
        void withdraw() {
            BalanceResolver r = new History()
                .cash(BalanceSourceType.INIT, 0L, at(1, 9))
                .cash(BalanceSourceType.TRANSFER, 1_000_000L, at(1, 10))
                .holding(600_000L, at(2, 10))
                .cash(BalanceSourceType.TRANSFER, -500_000L, at(7, 10))
                .build();

            Split s = r.splitAt(ACC, at(7, 23));
            assertThat(s.cash()).isEqualTo(500_000L);
            assertThat(s.holding()).isEqualTo(600_000L);
            assertThat(s.total()).isEqualTo(1_100_000L);
        }
    }

    @Nested
    @DisplayName("과거 시점 조회 — 추이 그래프가 쓰는 경로")
    class PointInTime {

        private BalanceResolver history() {
            return new History()
                .cash(BalanceSourceType.INIT, 0L, at(1, 9))
                .cash(BalanceSourceType.TRANSFER, 1_000_000L, at(4, 9))
                .holding(800_000L, at(4, 16))
                .cash(BalanceSourceType.EXPENSE, -600_000L, at(6, 10))
                .holding(1_400_000L, at(6, 16))
                .build();
        }

        @Test
        @DisplayName("입금 전 — 둘 다 0")
        void beforeDeposit() {
            assertThat(history().splitAt(ACC, at(3, 23))).isEqualTo(new Split(0L, 0L));
        }

        @Test
        @DisplayName("입금 직후·평가 전 — 예수금 100만, 평가 0")
        void afterDepositBeforeValuation() {
            Split s = history().splitAt(ACC, at(4, 12));
            assertThat(s.cash()).isEqualTo(1_000_000L);
            assertThat(s.holding()).isZero();
        }

        @Test
        @DisplayName("매수 후 — 예수금 40만, 평가 140만")
        void afterBuy() {
            Split s = history().splitAt(ACC, at(6, 23));
            assertThat(s.cash()).isEqualTo(400_000L);
            assertThat(s.holding()).isEqualTo(1_400_000L);
            assertThat(s.total()).isEqualTo(1_800_000L);
        }

        @Test
        @DisplayName("기준시각 경계 — 같은 시각의 기록은 포함한다")
        void boundaryInclusive() {
            assertThat(history().splitAt(ACC, at(4, 9)).cash()).isEqualTo(1_000_000L);
        }
    }

    @Nested
    @DisplayName("투자가 아닌 자산 — 계산이 종전과 같아야 한다")
    class NonInvestment {

        @Test
        @DisplayName("은행 계좌 — 평가금액 채널이 없어 예수금이 곧 잔액")
        void bankAccount() {
            BalanceResolver r = new History()
                .cash(BalanceSourceType.INIT, 3_000_000L, at(1, 9))
                .cash(BalanceSourceType.EXPENSE, -45_000L, at(2, 12))
                .cash(BalanceSourceType.TRANSFER, -1_000_000L, at(4, 9))
                .build();

            Split s = r.splitAt(ACC, at(9, 23));
            assertThat(s.holding()).isZero();
            assertThat(s.cash()).isEqualTo(1_955_000L);
            assertThat(s.total()).isEqualTo(1_955_000L);
        }

        @Test
        @DisplayName("수동 잔액 조정은 종전대로 예수금을 덮어쓴다(점프)")
        void manualJump() {
            BalanceResolver r = new History()
                .cash(BalanceSourceType.INIT, 3_000_000L, at(1, 9))
                .cash(BalanceSourceType.EXPENSE, -45_000L, at(2, 12))
                .cash(BalanceSourceType.MANUAL, 2_500_000L, at(3, 9))   // 통장 보고 맞춤
                .cash(BalanceSourceType.EXPENSE, -30_000L, at(4, 12))
                .build();

            assertThat(r.balanceAt(ACC, at(9, 23))).isEqualTo(2_470_000L);
        }

        @Test
        @DisplayName("신용카드 — 쓰면 음수로 누적")
        void creditCard() {
            BalanceResolver r = new History()
                .cash(BalanceSourceType.INIT, 0L, at(1, 9))
                .cash(BalanceSourceType.EXPENSE, -448_600L, at(3, 12))
                .build();

            assertThat(r.balanceAt(ACC, at(9, 23))).isEqualTo(-448_600L);
        }
    }

    @Nested
    @DisplayName("가장자리")
    class Edges {

        @Test
        @DisplayName("이력이 없는 자산은 0")
        void noHistory() {
            BalanceResolver r = new BalanceResolver(Map.of());
            assertThat(r.splitAt(ACC, at(9, 23))).isEqualTo(Split.ZERO);
            assertThat(r.balanceAt(ACC, at(9, 23))).isZero();
        }

        @Test
        @DisplayName("평가금액만 있고 예수금 기록이 없어도 총액은 평가금액")
        void holdingOnly() {
            BalanceResolver r = new History().holding(800_000L, at(1, 16)).build();

            Split s = r.splitAt(ACC, at(9, 23));
            assertThat(s.cash()).isZero();
            assertThat(s.holding()).isEqualTo(800_000L);
            assertThat(s.total()).isEqualTo(800_000L);
        }

        @Test
        @DisplayName("보유를 전부 지우면 평가금액 0 — 마지막 평가액이 남지 않는다")
        void holdingsCleared() {
            BalanceResolver r = new History()
                .cash(BalanceSourceType.TRANSFER, 1_000_000L, at(1, 10))
                .holding(800_000L, at(2, 16))
                .holding(0L, at(3, 11))
                .build();

            Split s = r.splitAt(ACC, at(9, 23));
            assertThat(s.holding()).isZero();
            assertThat(s.total()).isEqualTo(1_000_000L);
        }

        @Test
        @DisplayName("예수금이 마이너스여도 총액은 두 채널의 합 그대로")
        void negativeCash() {
            BalanceResolver r = new History()
                .cash(BalanceSourceType.TRANSFER, 100_000L, at(1, 10))
                .cash(BalanceSourceType.EXPENSE, -150_000L, at(2, 10))
                .holding(800_000L, at(2, 16))
                .build();

            Split s = r.splitAt(ACC, at(9, 23));
            assertThat(s.cash()).isEqualTo(-50_000L);
            assertThat(s.total()).isEqualTo(750_000L);
        }

        @Test
        @DisplayName("채널 배정이 없으면(구버전 행) 예수금으로 본다 — 계산이 종전과 같다")
        void nullChannelFallsBackToCash() {
            AssetBalanceHistory legacy =
                AssetBalanceHistory.of(null, null, BalanceSourceType.INIT, null, 500_000L, at(1, 9));
            BalanceResolver r = new BalanceResolver(Map.of(ACC, List.of(legacy)));

            Split s = r.splitAt(ACC, at(9, 23));
            assertThat(s.cash()).isEqualTo(500_000L);
            assertThat(s.holding()).isZero();
        }
    }

    @Test
    @DisplayName("순자산 시나리오 — 은행 195.5만 + 증권(예수금 40만 + 평가 140만) = 375.5만")
    void netWorthAcrossAccounts() {
        long bank = new History()
            .cash(BalanceSourceType.INIT, 3_000_000L, at(1, 9))
            .cash(BalanceSourceType.EXPENSE, -45_000L, at(2, 12))
            .cash(BalanceSourceType.TRANSFER, -1_000_000L, at(4, 9))
            .build()
            .balanceAt(ACC, at(9, 23));

        Split brokerage = new History()
            .cash(BalanceSourceType.TRANSFER, 1_000_000L, at(4, 9))
            .cash(BalanceSourceType.EXPENSE, -600_000L, at(6, 10))
            .holding(1_400_000L, at(6, 16))
            .build()
            .splitAt(ACC, at(9, 23));

        assertThat(bank).isEqualTo(1_955_000L);
        assertThat(brokerage.cash()).isEqualTo(400_000L);
        assertThat(brokerage.holding()).isEqualTo(1_400_000L);
        assertThat(bank + brokerage.total()).isEqualTo(3_755_000L);
    }
}
