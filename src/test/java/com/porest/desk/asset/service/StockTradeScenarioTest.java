package com.porest.desk.asset.service;

import com.porest.core.time.UserClock;
import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.domain.AssetHolding;
import com.porest.desk.asset.domain.AssetTrade;
import com.porest.desk.asset.repository.AssetHoldingRepository;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.asset.repository.AssetTradeRepository;
import com.porest.desk.asset.service.dto.AssetServiceDto;
import com.porest.desk.asset.service.dto.AssetTradeServiceDto.CreateTradeCommand;
import com.porest.desk.asset.type.AssetType;
import com.porest.desk.asset.type.HoldingType;
import com.porest.desk.asset.type.TradeType;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 증권계좌를 현실 그대로 굴려본다 — 입금 → 매수 → 시세 변동 → 부분 매도 → 전량 매도.
 *
 * <p>매 단계에서 예수금·수량·원가·평단가·실현손익이 맞는지 본다. 총액만 맞고 칸이 틀리면
 * 화면 숫자가 어긋나므로(웹 목록은 예수금 + 라이브 평가액으로 총액을 다시 조립한다)
 * 칸별로 확인한다.
 *
 * <p>원가는 이동평균이고 수수료는 매수 시 원가에 들어간다. 매도 수수료는 대금에서 뺀다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockTradeScenarioTest {

    @Mock private AssetTradeRepository tradeRepository;
    @Mock private AssetHoldingRepository holdingRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private UserRepository userRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private AssetBalanceHistoryService balanceHistoryService;
    @Mock private UserClock userClock;
    @Mock private AssetService assetService;

    /** 예수금 충당 이체를 흉내 낸다 — 통장은 빠지고 예수금은 들어온다. */
    private record Funding(long rowId, long amount) {}

    private final List<Funding> transfers = new ArrayList<>();
    private final List<Long> deletedTransferIds = new ArrayList<>();
    @InjectMocks private AssetTradeServiceImpl sut;

    private static final long USER_ID = 1L;
    private static final long ASSET_ID = 11L;
    private static final String SAMSUNG = "005930";
    private static final long BANK_ID = 10L;

    private User user;
    private Asset account;
    /** 인메모리 보유 목록 — 저장소 대용. */
    private final List<AssetHolding> holdings = new ArrayList<>();
    private final List<AssetTrade> trades = new ArrayList<>();
    private final List<Expense> expenses = new ArrayList<>();
    /** 예수금 — recordTrade 가 flow 를 남기면 여기에 누적한다. */
    private long cash;
    /** 결제 계좌(통장)로 나간 flow 누적 — 예수금과 따로 본다. */
    private long bankFlow;
    private Asset bank;

    @BeforeEach
    void setUp() {
        user = User.createUser(1L, "tester", "테스터", "tester@porest.cloud");
        ReflectionTestUtils.setField(user, "rowId", USER_ID);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(userClock.now(USER_ID)).willReturn(LocalDateTime.of(2026, 8, 31, 0, 0));

        account = Asset.createAsset(user, "주식계좌", AssetType.INVESTMENT, 0L, "KRW",
            null, null, null, null, 0, YNType.Y, null, null, null, null);
        ReflectionTestUtils.setField(account, "rowId", ASSET_ID);
        given(assetRepository.findById(ASSET_ID)).willReturn(Optional.of(account));

        bank = Asset.createAsset(user, "급여통장", AssetType.BANK_ACCOUNT, 0L, "KRW",
            null, null, null, null, 0, YNType.Y, null, null, null, null);
        ReflectionTestUtils.setField(bank, "rowId", BANK_ID);
        given(assetRepository.findById(BANK_ID)).willReturn(Optional.of(bank));

        given(holdingRepository.findActiveByAsset(ASSET_ID)).willAnswer(inv -> List.copyOf(holdings));
        willAnswer(inv -> {
            AssetHolding h = inv.getArgument(0);
            if (!holdings.contains(h)) {
                holdings.add(h);
            }
            return h;
        }).given(holdingRepository).save(any(AssetHolding.class));

        willAnswer(inv -> {
            AssetTrade t = inv.getArgument(0);
            ReflectionTestUtils.setField(t, "rowId", (long) (trades.size() + 1));
            trades.add(t);
            return t;
        }).given(tradeRepository).save(any(AssetTrade.class));
        given(tradeRepository.findById(any())).willAnswer(inv -> {
            Long id = inv.getArgument(0);
            return trades.stream().filter(t -> id.equals(t.getRowId())).findFirst();
        });
        // 재계산용 조회 — 그 종목의 활성 거래를 (거래일시, row_id) 오름차순으로.
        given(tradeRepository.findForReplay(any(), any(), any())).willAnswer(inv -> {
            Long holdingRowId = inv.getArgument(1);
            String key = inv.getArgument(2);
            return trades.stream()
                .filter(t -> t.getIsDeleted() == YNType.N)
                .filter(t -> (holdingRowId != null && holdingRowId.equals(t.getHoldingRowId()))
                    || (t.getHoldingRowId() == null && key != null && key.equals(t.getHoldingKey())))
                .sorted(java.util.Comparator.comparing(AssetTrade::getTradeDate)
                    .thenComparing(AssetTrade::getRowId))
                .toList();
        });

        willAnswer(inv -> {
            Expense e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "rowId", 900L + expenses.size());
            expenses.add(e);
            return e;
        }).given(expenseRepository).save(any(Expense.class));
        given(expenseRepository.findById(any())).willAnswer(inv -> {
            Long id = inv.getArgument(0);
            return expenses.stream().filter(e -> id.equals(e.getRowId())).findFirst();
        });

        // 예수금 flow 를 실제로 누적해 잔액을 따라간다 — 자산의 캐시도 함께 맞춰야
        // 다음 매수의 예수금 검사가 현실처럼 동작한다.
        willAnswer(inv -> {
            Asset target = inv.getArgument(0);
            long delta = inv.getArgument(2);
            if (BANK_ID == target.getRowId()) {
                bankFlow += delta;
            } else {
                cash += delta;
            }
            return null;
        }).given(balanceHistoryService).recordTrade(any(), any(), any(Long.class), any());
        // 취소는 그 거래가 남긴 flow 를 되돌린다.
        willAnswer(inv -> {
            Long id = inv.getArgument(0);
            // 매매 flow 는 결제 계좌와 무관하게 언제나 예수금이다 — 결제 계좌는 이체로
            // 예수금을 채울 뿐이고, 그 이체는 deleteTransfer 가 따로 되돌린다.
            trades.stream().filter(t -> id.equals(t.getRowId())).findFirst()
                .ifPresent(t -> cash -= t.cashDelta());
            return null;
        }).given(balanceHistoryService).removeTrade(any());

        // 예수금 충당 이체 — 실제 서비스가 하는 것과 같은 방향으로 두 자산을 움직인다.
        willAnswer(inv -> {
            AssetServiceDto.CreateTransferCommand c = inv.getArgument(0);
            long id = 900L + transfers.size();
            transfers.add(new Funding(id, c.amount()));
            bankFlow -= c.amount();
            cash += c.amount();
            AssetServiceDto.TransferInfo info = org.mockito.Mockito.mock(AssetServiceDto.TransferInfo.class);
            org.mockito.Mockito.lenient().when(info.rowId()).thenReturn(id);
            return info;
        }).given(assetService).createTransfer(any());

        willAnswer(inv -> {
            Long id = inv.getArgument(0);
            deletedTransferIds.add(id);
            transfers.stream().filter(t -> id.equals(t.rowId())).findFirst().ifPresent(t -> {
                bankFlow += t.amount();
                cash -= t.amount();
            });
            return null;
        }).given(assetService).deleteTransfer(any(), any());
        // 예수금 검증이 캐시 대신 이력 집계를 본다 — 흉내 낸 예수금을 그대로 돌려준다.
        // 예수금 검증이 이력 집계를 본다 — 흉내 낸 예수금을 그대로 돌려준다(평가금액은 안 쓴다).
        willAnswer(inv -> new AssetBalanceHistoryService.Split(cash, 0L))
            .given(balanceHistoryService).balanceAt(any(), any());
    }

    // === 헬퍼 ==================================================================

    private void deposit(long amount) {
        cash += amount; // 통장 → 증권계좌 이체로 들어온 예수금
    }

    private void syncAsset(long holdingValuation) {
    }

    private CreateTradeCommand trade(TradeType type, String qty, long amount, long fee, int day) {
        return new CreateTradeCommand(USER_ID, ASSET_ID, type, HoldingType.STOCK,
            null, SAMSUNG, true,
            new BigDecimal(qty), amount, fee, LocalDateTime.of(2026, 8, day, 10, 0), null, null);
    }

    private AssetHolding samsung() {
        return holdings.stream()
            .filter(h -> SAMSUNG.equals(h.holdingKey()) && h.getIsDeleted() == YNType.N)
            .findFirst().orElse(null);
    }

    /** 저장된 거래를 id 로 다시 꺼낸다 — 재계산이 값을 갈아끼웠는지 보려면 원본을 봐야 한다. */
    private AssetTrade tradeOf(Long rowId) {
        return trades.stream().filter(t -> rowId.equals(t.getRowId())).findFirst().orElseThrow();
    }

    private Expense savedRealizedExpense() {
        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(captor.capture());
        return captor.getValue();
    }

    // === 현실 흐름 =============================================================

    @Nested
    @DisplayName("입금 → 매수 → 시세 변동 → 부분 매도 → 전량 매도")
    class FullCycle {

        @Test
        @DisplayName("매수 — 예수금에서 대금과 수수료가 빠지고 수량·원가가 선다")
        void buy() {
            deposit(10_000_000L);

            // 삼성전자 100주를 7,000,000원에 매수, 수수료 5,000원
            sut.createTrade(trade(TradeType.BUY, "100", 7_000_000L, 5_000L, 3));

            assertThat(cash).isEqualTo(2_995_000L);           // 1,000만 - (700만 + 5천)
            assertThat(samsung().getQuantity()).isEqualByComparingTo("100");
            assertThat(samsung().getTotalCost()).isEqualTo(7_005_000L); // 수수료는 취득원가
            assertThat(samsung().avgPrice()).isEqualByComparingTo("70050"); // 7,005,000 / 100
        }

        @Test
        @DisplayName("추가 매수 — 평단가가 이동평균으로 섞인다")
        void buyMore() {
            deposit(15_000_000L);
            sut.createTrade(trade(TradeType.BUY, "100", 7_000_000L, 5_000L, 3));
            // 주가가 내려 50주를 3,000,000원에 추가 매수 (수수료 2,000)
            sut.createTrade(trade(TradeType.BUY, "50", 3_000_000L, 2_000L, 5));

            assertThat(cash).isEqualTo(4_993_000L);           // 1,500만 - 7,005,000 - 3,002,000
            assertThat(samsung().getQuantity()).isEqualByComparingTo("150");
            assertThat(samsung().getTotalCost()).isEqualTo(10_007_000L);
            // 평단가 = 10,007,000 / 150 — 70,050 과 60,040 이 수량 비율로 섞인다
            assertThat(samsung().avgPrice()).isEqualByComparingTo("66713.33333333");
        }

        @Test
        @DisplayName("시세가 올라도 예수금과 원가는 그대로다")
        void valuationDoesNotTouchCashOrCost() {
            deposit(10_000_000L);
            sut.createTrade(trade(TradeType.BUY, "100", 7_000_000L, 5_000L, 3));
            long cashAfterBuy = cash;
            long costAfterBuy = samsung().getTotalCost();

            syncAsset(8_000_000L); // 평가액만 800만으로 갱신 (시세 상승)

            // 시세가 움직여도 예수금과 원가는 안 건드린다 — 평가금액만 갈아엎힌다.
            assertThat(cash).isEqualTo(cashAfterBuy);
            assertThat(samsung().getTotalCost()).isEqualTo(costAfterBuy);
        }

        @Test
        @DisplayName("부분 매도 — 판 만큼의 원가만 빠지고 그 차액이 실현손익이 된다")
        void sellPartial() {
            deposit(10_000_000L);
            sut.createTrade(trade(TradeType.BUY, "100", 7_000_000L, 5_000L, 3)); // 원가 7,005,000
            long cashBefore = cash;

            // 40주를 3,200,000원에 매도, 수수료 3,000
            sut.createTrade(trade(TradeType.SELL, "40", 3_200_000L, 3_000L, 10));

            // 판 만큼의 원가 = 7,005,000 * 40/100 = 2,802,000
            // 실현손익 = (3,200,000 - 3,000) - 2,802,000 = 395,000
            assertThat(cash).isEqualTo(cashBefore + 3_197_000L);
            assertThat(samsung().getQuantity()).isEqualByComparingTo("60");
            assertThat(samsung().getTotalCost()).isEqualTo(4_203_000L);
            assertThat(samsung().avgPrice()).isEqualByComparingTo("70050"); // 평단가는 안 변한다

            Expense pl = savedRealizedExpense();
            assertThat(pl.getExpenseType()).isEqualTo(ExpenseType.INCOME);
            assertThat(pl.getAmount()).isEqualTo(395_000L);
        }

        @Test
        @DisplayName("전량 매도 — 보유가 사라지고 대금이 예수금으로 남는다")
        void sellAll() {
            deposit(10_000_000L);
            sut.createTrade(trade(TradeType.BUY, "100", 7_000_000L, 5_000L, 3));
            long cashBefore = cash;

            sut.createTrade(trade(TradeType.SELL, "100", 8_000_000L, 6_000L, 20));

            assertThat(cash).isEqualTo(cashBefore + 7_994_000L);
            assertThat(samsung()).isNull(); // 수량 0 이면 보유가 사라진다
            // 실현손익 = (8,000,000 - 6,000) - 7,005,000 = 989,000
            assertThat(savedRealizedExpense().getAmount()).isEqualTo(989_000L);
        }

        @Test
        @DisplayName("손실 매도 — 지출로 기록된다")
        void sellAtLoss() {
            deposit(10_000_000L);
            sut.createTrade(trade(TradeType.BUY, "100", 7_000_000L, 5_000L, 3));

            sut.createTrade(trade(TradeType.SELL, "100", 6_000_000L, 4_000L, 20));

            // (6,000,000 - 4,000) - 7,005,000 = -1,009,000
            Expense pl = savedRealizedExpense();
            assertThat(pl.getExpenseType()).isEqualTo(ExpenseType.EXPENSE);
            assertThat(pl.getAmount()).isEqualTo(1_009_000L);
        }

        @Test
        @DisplayName("본전 매도 — 손익이 0 이면 거래를 만들지 않는다")
        void sellAtBreakEven() {
            deposit(10_000_000L);
            sut.createTrade(trade(TradeType.BUY, "100", 7_000_000L, 0L, 3)); // 원가 7,000,000

            sut.createTrade(trade(TradeType.SELL, "100", 7_000_000L, 0L, 20));

            verify(expenseRepository, never()).save(any(Expense.class));
        }
    }

    // === 기초 보유 =============================================================

    @Nested
    @DisplayName("앱을 쓰기 전부터 갖고 있던 보유")
    class Opening {

        @Test
        @DisplayName("기초 보유는 예수금을 건드리지 않는다 — 돈이 오간 적이 없다")
        void openingDoesNotMoveCash() {
            sut.createTrade(trade(TradeType.OPENING, "100", 7_000_000L, 0L, 1));

            assertThat(cash).isZero();
            assertThat(samsung().getQuantity()).isEqualByComparingTo("100");
            assertThat(samsung().getTotalCost()).isEqualTo(7_000_000L);
            verify(balanceHistoryService, never())
                .recordTrade(any(), any(), any(Long.class), any());
        }

        @Test
        @DisplayName("기초 보유 뒤 매도해도 원가 기준으로 손익이 잡힌다")
        void sellAfterOpening() {
            sut.createTrade(trade(TradeType.OPENING, "100", 7_000_000L, 0L, 1));

            sut.createTrade(trade(TradeType.SELL, "100", 7_500_000L, 0L, 10));

            assertThat(cash).isEqualTo(7_500_000L);
            assertThat(savedRealizedExpense().getAmount()).isEqualTo(500_000L);
        }
    }

    // === 취소 ==================================================================

    @Nested
    @DisplayName("거래 취소")
    class Cancel {

        @Test
        @DisplayName("매수 취소 — 예수금·수량·원가가 매수 직전으로 돌아온다")
        void cancelBuy() {
            deposit(10_000_000L);
            var bought = sut.createTrade(trade(TradeType.BUY, "100", 7_000_000L, 5_000L, 3));

            sut.deleteTrade(bought.rowId(), USER_ID);

            assertThat(cash).isEqualTo(10_000_000L);
            assertThat(samsung()).isNull();
        }

        @Test
        @DisplayName("매도 취소 — 판 수량과 원가가 복원되고 손익 거래도 지워진다")
        void cancelSell() {
            deposit(10_000_000L);
            sut.createTrade(trade(TradeType.BUY, "100", 7_000_000L, 5_000L, 3));
            var sold = sut.createTrade(trade(TradeType.SELL, "40", 3_200_000L, 3_000L, 10));
            long cashAfterSell = cash;

            sut.deleteTrade(sold.rowId(), USER_ID);

            assertThat(cash).isEqualTo(cashAfterSell - 3_197_000L);
            assertThat(samsung().getQuantity()).isEqualByComparingTo("100");
            assertThat(samsung().getTotalCost()).isEqualTo(7_005_000L); // 판 만큼의 원가가 되돌아온다
            verify(expenseRepository).delete(any(Expense.class));
        }
    }

    // === 막아야 할 입력 =========================================================

    @Nested
    @DisplayName("현실에서 불가능한 입력은 막는다")
    class Validation {

        @Test
        @DisplayName("예수금보다 많이 사도 막지 않는다 — 예수금이 마이너스로 쌓인다")
        void buyingOverCashIsAllowed() {
            deposit(1_000_000L);

            // 이건 기록용 앱이다. 입금을 안 적고 매수만 적는 사용자가 있고, 마이너스 통장처럼
            // 음수로 쌓이는 게 정상이다. 막으면 "실제로는 샀는데 앱에는 못 적는" 상태가 된다.
            sut.createTrade(trade(TradeType.BUY, "100", 7_000_000L, 0L, 3));

            assertThat(cash).isEqualTo(-6_000_000L);
        }

        @Test
        @DisplayName("보유 수량보다 많이 팔 수 없다")
        void cannotSellOverQuantity() {
            deposit(10_000_000L);
            sut.createTrade(trade(TradeType.BUY, "100", 7_000_000L, 0L, 3));

            assertThatThrownBy(() -> sut.createTrade(trade(TradeType.SELL, "200", 1_000_000L, 0L, 5)))
                .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("보유하지 않은 종목은 팔 수 없다")
        void cannotSellUnheld() {
            deposit(10_000_000L);

            assertThatThrownBy(() -> sut.createTrade(trade(TradeType.SELL, "10", 100_000L, 0L, 5)))
                .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("수량은 0보다 커야 한다")
        void quantityMustBePositive() {
            deposit(10_000_000L);

            assertThatThrownBy(() -> sut.createTrade(trade(TradeType.BUY, "0", 100_000L, 0L, 3)))
                .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("투자 자산이 아니면 거래를 기록할 수 없다")
        void onlyInvestmentAssets() {
            Asset bank = Asset.createAsset(user, "급여통장", AssetType.BANK_ACCOUNT, 0L, "KRW",
                null, null, null, null, 0, YNType.Y, null, null, null, null);
            ReflectionTestUtils.setField(bank, "rowId", 99L);
            given(assetRepository.findById(99L)).willReturn(Optional.of(bank));

            var cmd = new CreateTradeCommand(USER_ID, 99L, TradeType.BUY, HoldingType.STOCK,
            null,
                SAMSUNG, true, BigDecimal.TEN, 100_000L, 0L,
                LocalDateTime.of(2026, 8, 3, 10, 0), null, null);

            assertThatThrownBy(() -> sut.createTrade(cmd))
                .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("남의 자산에는 거래를 기록할 수 없다")
        void othersAssetRejected() {
            assertThatThrownBy(() -> sut.createTrade(
                new CreateTradeCommand(999L, ASSET_ID, TradeType.BUY, HoldingType.STOCK,
            null,
                    SAMSUNG, true, BigDecimal.TEN, 100_000L, 0L,
                    LocalDateTime.of(2026, 8, 3, 10, 0), null, null)))
                .isInstanceOf(RuntimeException.class);
        }
    }

    // === 결제 계좌 =============================================================

    @Nested
    @DisplayName("결제 계좌 매수 — 통장에서 예수금으로 채우고 예수금에서 산다")
    class SettlementAccount {

        private CreateTradeCommand viaBank(TradeType type, String qty, long amount, int day) {
            return new CreateTradeCommand(USER_ID, ASSET_ID, type, HoldingType.STOCK,
            null, SAMSUNG, true,
                new BigDecimal(qty), amount, 0L, LocalDateTime.of(2026, 8, day, 10, 0), null,
                BANK_ID);
        }

        @Test
        @DisplayName("예수금 0 에서 700만 매수 — 통장에서 700만 이체 후 예수금에서 결제")
        void fundsShortfallThenBuys() {
            sut.createTrade(viaBank(TradeType.BUY, "100", 7_000_000L, 3));

            // ① 이체: 통장 -700만 → 예수금 +700만  ② 매수: 예수금 -700만
            assertThat(transfers).hasSize(1);
            assertThat(transfers.get(0).amount()).isEqualTo(7_000_000L);
            assertThat(bankFlow).isEqualTo(-7_000_000L);
            assertThat(cash).isZero();
            assertThat(samsung().getTotalCost()).isEqualTo(7_000_000L);
        }

        @Test
        @DisplayName("예수금이 일부 있으면 모자란 만큼만 이체한다 — 400만 있고 1,000만 매수 → 600만")
        void fundsOnlyTheShortfall() {
            deposit(4_000_000L);

            sut.createTrade(viaBank(TradeType.BUY, "100", 10_000_000L, 3));

            assertThat(transfers).hasSize(1);
            assertThat(transfers.get(0).amount()).isEqualTo(6_000_000L);
            assertThat(bankFlow).isEqualTo(-6_000_000L);
            assertThat(cash).isZero();  // 400만 + 600만 - 1,000만
        }

        @Test
        @DisplayName("예수금이 충분하면 이체가 아예 생기지 않는다")
        void noTransferWhenCashIsEnough() {
            deposit(10_000_000L);

            sut.createTrade(viaBank(TradeType.BUY, "100", 7_000_000L, 3));

            assertThat(transfers).isEmpty();
            assertThat(bankFlow).isZero();
            assertThat(cash).isEqualTo(3_000_000L);
        }

        @Test
        @DisplayName("통장이 마이너스가 돼도 막지 않는다 — 기록용 앱이다")
        void bankMayGoNegative() {
            sut.createTrade(viaBank(TradeType.BUY, "100", 99_000_000L, 3));

            assertThat(bankFlow).isEqualTo(-99_000_000L);
        }

        @Test
        @DisplayName("매도 대금은 예수금에 남는다 — 팔았다고 통장으로 자동 이체되지 않는다")
        void sellKeepsProceedsInCash() {
            sut.createTrade(viaBank(TradeType.BUY, "100", 7_000_000L, 3));
            int transfersAfterBuy = transfers.size();

            sut.createTrade(viaBank(TradeType.SELL, "100", 8_000_000L, 20));

            assertThat(transfers).hasSize(transfersAfterBuy); // 매도는 이체를 만들지 않는다
            assertThat(bankFlow).isEqualTo(-7_000_000L);      // 매수 때 나간 그대로
            assertThat(cash).isEqualTo(8_000_000L);           // 대금은 예수금에
            assertThat(savedRealizedExpense().getAmount()).isEqualTo(1_000_000L);
        }

        @Test
        @DisplayName("취소 — 이체와 매수가 함께 되돌아 매수 직전으로 복귀한다")
        void cancelRestoresBothLegs() {
            deposit(4_000_000L);
            var bought = sut.createTrade(viaBank(TradeType.BUY, "100", 10_000_000L, 3));

            sut.deleteTrade(bought.rowId(), USER_ID);

            // 이체를 안 지우면 "통장에서 600만 빼서 예수금에 넣어 둔" 상태로 남는다.
            assertThat(deletedTransferIds).containsExactly(transfers.get(0).rowId());
            assertThat(bankFlow).isZero();
            assertThat(cash).isEqualTo(4_000_000L);
            assertThat(samsung()).isNull();
        }

        @Test
        @DisplayName("이체가 안 생긴 매수를 취소해도 지울 이체가 없다")
        void cancelWithoutTransfer() {
            deposit(10_000_000L);
            var bought = sut.createTrade(viaBank(TradeType.BUY, "100", 7_000_000L, 3));

            sut.deleteTrade(bought.rowId(), USER_ID);

            assertThat(deletedTransferIds).isEmpty();
            assertThat(cash).isEqualTo(10_000_000L);
        }
    }

    // === 금·코인 ===============================================================

    @Nested
    @DisplayName("금·코인도 같은 규칙으로 굴러간다")
    class NonStock {

        private CreateTradeCommand goldTrade(TradeType type, String qty, long amount, int day) {
            return new CreateTradeCommand(USER_ID, ASSET_ID, type, HoldingType.GOLD,
            null, "금 현물", false,
                new BigDecimal(qty), amount, 0L, LocalDateTime.of(2026, 8, day, 10, 0), null, null);
        }

        @Test
        @DisplayName("소수 수량 — 금 3.75g 을 사고 1.25g 을 판다")
        void fractionalQuantity() {
            deposit(10_000_000L);
            sut.createTrade(goldTrade(TradeType.BUY, "3.75", 3_000_000L, 3));

            sut.createTrade(goldTrade(TradeType.SELL, "1.25", 1_100_000L, 10));

            AssetHolding gold = holdings.stream()
                .filter(h -> "금 현물".equals(h.holdingKey()) && h.getIsDeleted() == YNType.N)
                .findFirst().orElseThrow();
            assertThat(gold.getQuantity()).isEqualByComparingTo("2.50");
            // 판 만큼의 원가 = 3,000,000 * 1.25/3.75 = 1,000,000 → 실현손익 100,000
            assertThat(gold.getTotalCost()).isEqualTo(2_000_000L);
            assertThat(savedRealizedExpense().getAmount()).isEqualTo(100_000L);
        }
    }

    @Nested
    @DisplayName("재계산 — 순서가 바뀌면 그 뒤 손익을 다시 쌓는다")
    class Replay {

        /**
         * 이동평균은 순서에 의존한다. 각 거래에 박아 둔 변동분은 "그때의" 값이라,
         * 앞선 거래가 사라지거나 과거 날짜 거래가 끼어들면 그대로는 어긋난다.
         */
        @Test
        @DisplayName("중간 매수를 지우면 그 뒤 매도의 실현손익이 다시 계산된다")
        void deletingMiddleBuyRecalculatesLaterSell() {
            deposit(50_000_000L);
            var first = sut.createTrade(trade(TradeType.BUY, "10", 700_000L, 0L, 2));   // 평단 70,000
            sut.createTrade(trade(TradeType.BUY, "10", 900_000L, 0L, 10));              // 평단 80,000
            var sold = sut.createTrade(trade(TradeType.SELL, "10", 1_000_000L, 0L, 20));

            // 20주 원가 1,600,000 중 절반을 팔았으니 800,000 → 손익 +200,000
            assertThat(tradeOf(sold.rowId()).getRealizedPl()).isEqualTo(200_000L);

            // "3월 매수는 사실 다른 증권사 거래였다" — 지운다.
            sut.deleteTrade(first.rowId(), USER_ID);

            // 남은 건 10주 900,000 뿐이고 그걸 1,000,000 에 팔았으니 +100,000 이어야 한다.
            assertThat(tradeOf(sold.rowId()).getRealizedPl()).isEqualTo(100_000L);
        }

        @Test
        @DisplayName("과거 날짜 매수를 뒤늦게 넣어도 그 뒤 매도가 다시 계산된다")
        void backdatedBuyRecalculatesLaterSell() {
            deposit(50_000_000L);
            sut.createTrade(trade(TradeType.BUY, "10", 900_000L, 0L, 10));
            var sold = sut.createTrade(trade(TradeType.SELL, "10", 1_000_000L, 0L, 20));

            assertThat(tradeOf(sold.rowId()).getRealizedPl()).isEqualTo(100_000L);

            // 2일자 매수를 8월 말에 뒤늦게 입력 — 평단이 섞여 손익이 달라져야 한다.
            sut.createTrade(trade(TradeType.BUY, "10", 700_000L, 0L, 2));

            assertThat(tradeOf(sold.rowId()).getRealizedPl()).isEqualTo(200_000L);
        }

        @Test
        @DisplayName("맨 뒤에 붙는 거래는 다시 쌓지 않는다 — 이미 맞는 값이다")
        void appendingDoesNotChangeEarlier() {
            deposit(50_000_000L);
            sut.createTrade(trade(TradeType.BUY, "10", 700_000L, 0L, 2));
            var sold = sut.createTrade(trade(TradeType.SELL, "5", 500_000L, 0L, 10));
            long before = tradeOf(sold.rowId()).getRealizedPl();

            sut.createTrade(trade(TradeType.BUY, "10", 900_000L, 0L, 20));

            assertThat(tradeOf(sold.rowId()).getRealizedPl()).isEqualTo(before);
        }

        @Test
        @DisplayName("재계산 후 보유 수량·원가도 다시 쌓은 값으로 맞는다")
        void holdingFollowsReplay() {
            deposit(50_000_000L);
            var first = sut.createTrade(trade(TradeType.BUY, "10", 700_000L, 0L, 2));
            sut.createTrade(trade(TradeType.BUY, "10", 900_000L, 0L, 10));
            sut.createTrade(trade(TradeType.SELL, "10", 1_000_000L, 0L, 20));

            sut.deleteTrade(first.rowId(), USER_ID);

            // 10주 사서 10주 팔았으니 보유가 없어야 한다.
            assertThat(samsung()).isNull();
        }

        @Test
        @DisplayName("다른 종목은 건드리지 않는다 — 재계산 단위는 (자산, 종목) 하나다")
        void doesNotTouchOtherHoldings() {
            deposit(50_000_000L);
            var kakaoBuy = new CreateTradeCommand(USER_ID, ASSET_ID, TradeType.BUY,
                HoldingType.STOCK, null, "035720", true, new BigDecimal("5"), 500_000L, 0L,
                LocalDateTime.of(2026, 8, 5, 10, 0), null, null);
            sut.createTrade(kakaoBuy);
            var first = sut.createTrade(trade(TradeType.BUY, "10", 700_000L, 0L, 2));

            sut.deleteTrade(first.rowId(), USER_ID);

            AssetHolding kakao = holdings.stream()
                .filter(h -> "035720".equals(h.holdingKey()) && h.getIsDeleted() == YNType.N)
                .findFirst().orElse(null);
            assertThat(kakao).isNotNull();
            assertThat(kakao.getTotalCost()).isEqualTo(500_000L);
        }
    }
}
