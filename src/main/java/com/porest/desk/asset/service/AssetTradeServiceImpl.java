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
import com.porest.desk.asset.service.dto.AssetTradeServiceDto;
import com.porest.desk.asset.type.AssetType;
import com.porest.desk.asset.type.HoldingType;
import com.porest.desk.asset.type.TradeType;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * 투자 자산의 매수·매도.
 *
 * <p>예수금이 줄고 느는 진짜 사건을 기록한다. 이게 없으면 평가액 갱신을 보고 예수금을 추측해야
 * 하는데, 시세 변동·추가 매수·재등록이 전부 같은 갱신으로 들어와 구분되지 않는다.
 *
 * <p>원가는 이동평균이다. 매수 수수료는 취득원가에 넣고 매도 수수료는 대금에서 뺀다 —
 * 어느 쪽이든 예수금에서 실제로 나간다.
 *
 * <p>거래마다 수량·원가 변동분을 함께 남긴다. 이동평균은 순서에 의존해서, 취소할 때 "그때
 * 원가가 얼마나 빠졌는지" 를 현재 기준으로 다시 계산하면 어긋난다. 변동분을 박아 두면
 * 역적용으로 정확히 되돌아간다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssetTradeServiceImpl implements AssetTradeService {

    private final AssetTradeRepository tradeRepository;
    private final AssetHoldingRepository holdingRepository;
    private final AssetRepository assetRepository;
    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final AssetBalanceHistoryService balanceHistoryService;
    private final UserClock userClock;
    /** 예수금 충당 이체를 만들고 지운다 — 매수 한 건이 이체+매수 두 기록이 된다. */
    private final AssetService assetService;

    /** 자동 생성 이체의 메모 — 사용자가 직접 만든 이체와 구분된다. */
    private static final String SETTLEMENT_TRANSFER_DESC = "예수금 입금 (매수)";

    @Override
    @Transactional
    public AssetTradeServiceDto.TradeInfo createTrade(AssetTradeServiceDto.CreateTradeCommand command) {
        User user = userRepository.findById(command.userRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));
        Asset asset = findInvestmentAsset(command.assetRowId(), command.userRowId());

        TradeType type = command.tradeType();
        BigDecimal quantity = command.quantity();
        if (quantity == null || quantity.signum() <= 0) {
            throw new InvalidValueException(DeskErrorCode.ASSET_TRADE_INVALID_QUANTITY);
        }
        long amount = command.amount() != null ? command.amount() : 0L;
        long fee = command.fee() != null ? command.fee() : 0L;
        if (amount <= 0 || fee < 0) {
            throw new InvalidValueException(DeskErrorCode.ASSET_TRADE_INVALID_AMOUNT);
        }

        AssetHolding holding = findHolding(asset.getRowId(), command.holdingRowId(), command.holdingKey());
        BigDecimal quantityDelta;
        long costDelta;
        Long realized = null;

        if (type == TradeType.SELL) {
            if (holding == null) {
                throw new InvalidValueException(DeskErrorCode.ASSET_TRADE_HOLDING_NOT_FOUND);
            }
            BigDecimal held = holding.getQuantity() != null ? holding.getQuantity() : BigDecimal.ZERO;
            if (held.compareTo(quantity) < 0) {
                throw new InvalidValueException(DeskErrorCode.ASSET_TRADE_INSUFFICIENT_QUANTITY);
            }
            // 판 만큼의 원가 = 총원가 × (판 수량 / 보유 수량). 평단가를 따로 들면 반올림이 쌓인다.
            long soldCost = proportionalCost(holding.getTotalCost(), quantity, held);
            quantityDelta = quantity.negate();
            costDelta = -soldCost;
            realized = (amount - fee) - soldCost;
        } else {
            // 매수·기초보유 — 취득원가에 수수료를 포함한다.
            quantityDelta = quantity;
            costDelta = amount + fee;
        }

        long cashDelta = switch (type) {
            case BUY -> -(amount + fee);
            case SELL -> amount - fee;
            case OPENING -> 0L;
        };
        // 결제 계좌는 매수에만 쓴다 — 매도 대금은 예수금에 남기고 사용자가 이체로 관리한다
        // (팔았다고 통장으로 자동 이체되지는 않는다).
        Asset settlement = type == TradeType.BUY
            ? resolveSettlementAsset(command.settlementAssetRowId(), command.userRowId())
            : null;
        // 예수금이 모자라도 막지 않는다 — 이건 기록용 앱이다. 입금을 안 적고 매수만 적는
        // 사용자가 있고, 마이너스 통장처럼 음수로 쌓이는 게 정상이다.

        AssetTrade trade = AssetTrade.create(user, asset, settlement, type,
            command.holdingType() != null ? command.holdingType() : HoldingType.STOCK,
            command.holdingKey(),
            Boolean.TRUE.equals(command.linked()) ? YNType.Y : YNType.N,
            quantity, amount, fee, quantityDelta, costDelta,
            command.tradeDate() != null ? command.tradeDate() : userClock.now(command.userRowId()),
            command.description());
        tradeRepository.save(trade);

        AssetHolding applied = applyToHolding(asset, command, holding, quantityDelta, costDelta);
        // 이 거래가 어느 보유를 건드렸는지 id 로 남긴다 — 이름이 바뀌어도 안 끊긴다.
        if (applied != null && applied.getRowId() != null) {
            trade.linkHolding(applied.getRowId());
        }

        // 증권계좌는 예수금이 있어야 주식을 산다 — 결제 계좌를 골랐으면 통장에서 주식이
        // 바로 사지는 게 아니라 두 단계로 나뉜다.
        //   ① 이체  통장 → 예수금 (모자란 만큼만)
        //   ② 매수  예수금 → 보유
        // 예수금이 충분하면 ①이 생기지 않는다. 이 이체는 앱이 매수 때문에 만든 것이라
        // 거래를 취소하면 함께 지운다(카드 결제의 card_billing ↔ 이체와 같은 구조).
        if (settlement != null) {
            long cashNow = balanceHistoryService.balanceAt(asset, trade.getTradeDate()).cash();
            long shortfall = -cashDelta - cashNow;
            if (shortfall > 0) {
                AssetServiceDto.TransferInfo funding = assetService.createTransfer(
                    new AssetServiceDto.CreateTransferCommand(
                        command.userRowId(), settlement.getRowId(), asset.getRowId(),
                        shortfall, 0L, 0L, SETTLEMENT_TRANSFER_DESC, trade.getTradeDate(),
                        "TRADE_SETTLEMENT"));
                trade.linkSettlementTransfer(funding.rowId());
            }
        }
        // 예수금 flow — 평가금액은 시세×수량으로 따로 산정되므로 건드리지 않는다.
        // 기초 보유는 돈이 오간 적이 없어 이력을 남기지 않는다.
        if (cashDelta != 0L) {
            balanceHistoryService.recordTrade(
                trade.cashAsset(), trade.getRowId(), cashDelta, trade.getTradeDate());
        }

        if (realized != null && realized != 0L) {
            Expense pl = createRealizedExpense(user, asset, trade, realized);
            trade.recordRealized(realized, pl.getRowId());
        } else if (realized != null) {
            trade.recordRealized(0L, null);
        }

        // 과거 날짜 거래를 뒤늦게 넣으면 그 뒤 매도들의 원가·손익이 달라진다 — 맨 뒤에
        // 붙은 게 아니면 그 종목을 다시 쌓는다(맨 뒤면 방금 계산한 값이 이미 맞다).
        if (isBackdated(asset, trade)) {
            replayHolding(asset, trade.getHoldingRowId(), command.holdingKey());
        }
        // 보유가 확정된 뒤에 평가금액을 맞춘다 — 안 하면 예수금만 빠지고 산 물건 값이
        // 어디에도 안 잡혀 순자산이 매수금액만큼 증발한다.
        syncHoldingValuation(asset, trade.getTradeDate());

        log.info("투자 거래 등록: assetId={}, type={}, key={}, qty={}, amount={}, realized={}",
            asset.getRowId(), type, command.holdingKey(), quantity, amount, realized);
        return AssetTradeServiceDto.TradeInfo.from(trade);
    }

    @Override
    @Transactional
    public void deleteTrade(Long tradeRowId, Long userRowId) {
        AssetTrade trade = tradeRepository.findById(tradeRowId)
            .filter(t -> t.getIsDeleted() == YNType.N)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.ASSET_TRADE_NOT_FOUND));
        if (!Objects.equals(trade.getUser().getRowId(), userRowId)) {
            throw new InvalidValueException(DeskErrorCode.ASSET_ACCESS_DENIED);
        }

        Asset asset = trade.getAsset();
        AssetHolding holding = findHolding(asset.getRowId(), trade.getHoldingRowId(), trade.getHoldingKey());
        if (holding != null) {
            // 남겨 둔 변동분을 부호만 뒤집어 되돌린다 — 현재 원가로 다시 계산하면 어긋난다.
            holding.applyTrade(trade.getQuantityDelta().negate(), -trade.getCostDelta());
            if (holding.getQuantity() == null || holding.getQuantity().signum() <= 0) {
                holding.deleteHolding();
            }
        } else if (trade.getQuantityDelta().signum() < 0) {
            // 전량 매도로 사라졌던 보유를 되살린다.
            AssetHolding revived = AssetHolding.create(asset, trade.getHoldingType(),
                trade.getLinked(),
                trade.getLinked() == YNType.Y ? trade.getHoldingKey() : null,
                trade.getQuantityDelta().negate(),
                trade.getLinked() == YNType.Y ? null : trade.getHoldingKey(),
                null, -trade.getCostDelta(), 0);
            holdingRepository.save(revived);
        }

        balanceHistoryService.removeTrade(trade.getRowId());
        // 예수금 충당 이체도 함께 지운다 — 사용자가 만든 게 아니라 이 매수 때문에 앱이
        // 만든 것이라, 매수가 사라지면 존재 이유가 없다. 안 지우면 "통장에서 빼서 예수금에
        // 넣어 둔" 상태로 남아 매수 전으로 안 돌아간다.
        if (trade.getSettlementTransferRowId() != null) {
            assetService.deleteTransfer(trade.getSettlementTransferRowId(), userRowId);
        }
        if (trade.getRealizedExpenseRowId() != null) {
            // soft delete 로 지운다 — 다른 모든 거래가 그렇고, 물리 삭제하면 그 행에
            // 달아 둔 카테고리·분할까지 흔적 없이 사라진다.
            expenseRepository.findById(trade.getRealizedExpenseRowId())
                .ifPresent(Expense::deleteExpense);
        }
        trade.deleteTrade();
        // 이동평균은 순서에 의존한다 — 중간 거래를 지우면 그 뒤 매도들의 원가·손익이 달라진다.
        // 그 종목만 처음부터 다시 쌓아 맞춘다.
        replayHolding(asset, trade.getHoldingRowId(), trade.getHoldingKey());
        syncHoldingValuation(asset, userClock.now(userRowId));
        log.info("투자 거래 취소: tradeId={}, assetId={}", tradeRowId, asset.getRowId());
    }

    @Override
    public List<AssetTradeServiceDto.TradeInfo> getTrades(Long assetRowId, Long userRowId) {
        findInvestmentAsset(assetRowId, userRowId);
        return tradeRepository.findActiveByAsset(assetRowId, YNType.N).stream()
            .map(AssetTradeServiceDto.TradeInfo::from)
            .toList();
    }


    /**
     * 한 종목의 거래를 처음부터 다시 쌓아 수량·원가·실현손익을 맞춘다.
     *
     * <p>이동평균은 순서에 의존한다. 앞선 매수를 지우거나 과거 날짜 거래를 뒤늦게 넣으면
     * 그 뒤 매도들의 "판 만큼의 원가" 가 달라지는데, 각 거래에 박아 둔 변동분은 그때의
     * 값이라 그대로 두면 어긋난다. 그래서 그 종목만 시간순으로 다시 굴린다.
     *
     * <p>재계산 단위는 <b>(자산, 종목)</b> 하나다 — 다른 계좌·다른 종목은 건드리지 않는다.
     * 매도에 딸린 손익 거래(expense)도 새 값으로 갱신한다.
     *
     * <p>예수금은 건드리지 않는다. flow 의 단순 합이라 순서와 무관하게 이미 맞다.
     */
    private void replayHolding(Asset asset, Long holdingRowId, String holdingKey) {
        List<AssetTrade> trades = tradeRepository.findForReplay(asset.getRowId(), holdingRowId, holdingKey);
        if (trades.isEmpty()) {
            return;
        }
        BigDecimal quantity = BigDecimal.ZERO;
        long totalCost = 0L;

        for (AssetTrade t : trades) {
            if (t.getTradeType() == TradeType.SELL) {
                BigDecimal sellQty = t.getQuantity();
                long soldCost = proportionalCost(totalCost, sellQty, quantity);
                long realized = (t.getAmount() - t.getFee()) - soldCost;
                t.replaceDeltas(sellQty.negate(), -soldCost, realized);
                quantity = quantity.subtract(sellQty);
                totalCost = Math.max(0L, totalCost - soldCost);
                syncRealizedExpense(t, realized);
            } else {
                long cost = t.getAmount() + t.getFee();
                t.replaceDeltas(t.getQuantity(), cost, null);
                quantity = quantity.add(t.getQuantity());
                totalCost += cost;
            }
        }

        // 보유도 다시 쌓은 값으로 맞춘다 — 거래가 진실이고 보유는 그 결과다.
        AssetHolding holding = findHolding(asset.getRowId(), holdingRowId, holdingKey);
        if (holding != null) {
            if (quantity.signum() <= 0) {
                holding.deleteHolding();
            } else {
                // 손으로 넣은 평가액도 수량 변화만큼 따라가야 한다 — 다시 쌓아 수량이
                // 달라졌는데 평가액만 옛 값이면 순자산이 그만큼 어긋난다.
                holding.adjust(quantity,
                    scaleValuation(holding.getHoldingValue(), holding.getQuantity(), quantity),
                    totalCost);
            }
        }
    }

    /**
     * 보유 평가금액(HOLDING 채널)을 지금 보유 상태에 맞춘다.
     *
     * <p>이게 없으면 매수할 때 예수금만 빠지고 산 물건의 값이 어디에도 안 잡혀 <b>순자산이
     * 매수금액만큼 증발</b>한다. 매도는 반대로 부풀어 오른다. 돈이 자산 안에서 자리만 옮긴
     * 것이므로 순자산은 그대로여야 한다.
     *
     * <p>값은 <b>평가액이 있으면 그것, 없으면 취득원가</b>다. 미연동 종목은 시세를 모르니
     * 산 값이 곧 현재 값이고, 사용자가 시세를 알게 되면 손으로 고친다. 연동 종목은 토스
     * 스냅샷이 다음 갱신 때 이 값을 덮어쓴다.
     */
    private void syncHoldingValuation(Asset asset, LocalDateTime at) {
        long total = holdingRepository.findActiveByAsset(asset.getRowId()).stream()
            .mapToLong(h -> {
                if (h.getHoldingValue() != null) {
                    return h.getHoldingValue();
                }
                return h.getTotalCost() != null ? h.getTotalCost() : 0L;
            })
            .sum();
        balanceHistoryService.recordValuation(asset, total, at);
    }

    /** 수량이 바뀐 비율만큼 평가액을 옮긴다. 평가액이 없으면(연동·미입력) 그대로 둔다. */
    private static Long scaleValuation(Long valuation, BigDecimal before, BigDecimal after) {
        if (valuation == null) {
            return null;
        }
        if (before == null || before.signum() <= 0 || after.signum() <= 0) {
            return 0L;
        }
        return BigDecimal.valueOf(valuation)
            .multiply(after)
            .divide(before, 0, RoundingMode.HALF_UP)
            .longValue();
    }

    /** 재계산으로 손익이 바뀌면 딸린 거래도 따라간다 — 0 이 되면 지우고, 없다가 생기면 만든다. */
    private void syncRealizedExpense(AssetTrade trade, long realized) {
        Long expenseRowId = trade.getRealizedExpenseRowId();
        if (realized == 0L) {
            if (expenseRowId != null) {
                expenseRepository.findById(expenseRowId).ifPresent(Expense::deleteExpense);
                trade.recordRealized(0L, null);
            }
            return;
        }
        if (expenseRowId != null) {
            Expense pl = expenseRepository.findById(expenseRowId).orElse(null);
            if (pl != null) {
                pl.updateExpense(null, trade.getAsset(),
                    realized > 0 ? ExpenseType.INCOME : ExpenseType.EXPENSE,
                    Math.abs(realized), trade.getHoldingKey(), trade.getTradeDate(),
                    null, "TRADE", null, null, null, null, null);
                trade.recordRealized(realized, expenseRowId);
                return;
            }
        }
        Expense created = createRealizedExpense(trade.getUser(), trade.getAsset(), trade, realized);
        trade.recordRealized(realized, created.getRowId());
    }

    /** 방금 넣은 거래가 그 종목의 마지막이 아니면 소급 입력이다. */
    private boolean isBackdated(Asset asset, AssetTrade trade) {
        List<AssetTrade> all = tradeRepository.findForReplay(
            asset.getRowId(), trade.getHoldingRowId(), trade.getHoldingKey());
        return !all.isEmpty() && !all.get(all.size() - 1).getRowId().equals(trade.getRowId());
    }

    @Override
    public AssetTradeServiceDto.TradePreview previewTrade(AssetTradeServiceDto.CreateTradeCommand command) {
        Asset asset = findInvestmentAsset(command.assetRowId(), command.userRowId());
        long amount = command.amount() != null ? command.amount() : 0L;
        long fee = command.fee() != null ? command.fee() : 0L;
        BigDecimal quantity = command.quantity() != null ? command.quantity() : BigDecimal.ZERO;

        long cashDelta = switch (command.tradeType()) {
            case BUY -> -(amount + fee);
            case SELL -> amount - fee;
            case OPENING -> 0L;
        };
        long cashNow = balanceHistoryService
            .balanceAt(asset, command.tradeDate() != null
                ? command.tradeDate() : userClock.now(command.userRowId())).cash();

        long soldCost = 0L;
        Long realized = null;
        if (command.tradeType() == TradeType.SELL) {
            AssetHolding holding = findHolding(asset.getRowId(), command.holdingRowId(), command.holdingKey());
            BigDecimal held = holding != null && holding.getQuantity() != null
                ? holding.getQuantity() : BigDecimal.ZERO;
            soldCost = proportionalCost(holding != null ? holding.getTotalCost() : 0L, quantity, held);
            realized = (amount - fee) - soldCost;
        }

        // 결제 계좌를 골랐으면 부족분만 끌어온다 — 저장 때와 같은 계산이다.
        long funding = 0L;
        if (command.tradeType() == TradeType.BUY && command.settlementAssetRowId() != null) {
            funding = Math.max(0L, -cashDelta - cashNow);
        }

        return new AssetTradeServiceDto.TradePreview(
            soldCost, realized, cashDelta, cashNow + funding + cashDelta, funding);
    }

    // === 내부 ===================================================================

    private Asset findInvestmentAsset(Long assetRowId, Long userRowId) {
        Asset asset = assetRepository.findById(assetRowId)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.ASSET_NOT_FOUND));
        if (!Objects.equals(asset.getUser().getRowId(), userRowId)) {
            throw new InvalidValueException(DeskErrorCode.ASSET_ACCESS_DENIED);
        }
        if (asset.getAssetType() != AssetType.INVESTMENT) {
            throw new InvalidValueException(DeskErrorCode.ASSET_TRADE_NOT_INVESTMENT);
        }
        return asset;
    }

    /** 결제 계좌 — 본인 자산이어야 한다. 유형은 가리지 않는다(통장·현금 어디서든 낼 수 있다). */
    private Asset resolveSettlementAsset(Long settlementAssetRowId, Long userRowId) {
        if (settlementAssetRowId == null) {
            return null;
        }
        Asset settlement = assetRepository.findById(settlementAssetRowId)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.ASSET_NOT_FOUND));
        if (!Objects.equals(settlement.getUser().getRowId(), userRowId)) {
            throw new InvalidValueException(DeskErrorCode.ASSET_ACCESS_DENIED);
        }
        return settlement;
    }

    /**
     * 보유를 찾는다 — <b>row_id 가 우선</b>이고 없으면 이름으로 떨어진다.
     *
     * <p>미연동 종목의 holdingKey 는 항목명이라, 이름을 고치면 거래와 보유가 끊긴다.
     * 보유가 제자리 수정되도록 바뀌면서 row_id 가 안정됐으므로 id 로 묶는다.
     * 이름 경로는 id 가 없는 기존 거래와, 아직 보유가 없는 첫 매수를 위해 남긴다.
     */
    private AssetHolding findHolding(Long assetRowId, Long holdingRowId, String holdingKey) {
        List<AssetHolding> active = holdingRepository.findActiveByAsset(assetRowId).stream()
            .filter(h -> h.getIsDeleted() == YNType.N)
            .toList();
        if (holdingRowId != null) {
            AssetHolding byId = active.stream()
                .filter(h -> holdingRowId.equals(h.getRowId()))
                .findFirst().orElse(null);
            if (byId != null) {
                return byId;
            }
        }
        if (holdingKey == null) {
            return null;
        }
        return active.stream()
            .filter(h -> holdingKey.equals(h.holdingKey()))
            .findFirst()
            .orElse(null);
    }

    /** 판 만큼의 원가 — 총원가 × (판 수량 / 보유 수량). 마지막 한 주까지 팔면 잔여 원가가 0 이 된다. */
    private long proportionalCost(Long totalCost, BigDecimal sellQty, BigDecimal heldQty) {
        long cost = totalCost != null ? totalCost : 0L;
        if (cost == 0L || heldQty.signum() == 0) {
            return 0L;
        }
        if (sellQty.compareTo(heldQty) == 0) {
            return cost; // 전량 매도는 비율 계산의 반올림을 타지 않게 통째로
        }
        return BigDecimal.valueOf(cost)
            .multiply(sellQty)
            .divide(heldQty, 0, RoundingMode.HALF_UP)
            .longValueExact();
    }

    private AssetHolding applyToHolding(Asset asset, AssetTradeServiceDto.CreateTradeCommand command,
                                        AssetHolding holding, BigDecimal quantityDelta, long costDelta) {
        if (holding == null) {
            boolean linked = Boolean.TRUE.equals(command.linked());
            AssetHolding created = AssetHolding.create(asset,
                command.holdingType() != null ? command.holdingType() : HoldingType.STOCK,
                linked ? YNType.Y : YNType.N,
                linked ? command.holdingKey() : null,
                quantityDelta,
                linked ? null : command.holdingKey(),
                null, costDelta, nextSortOrder(asset.getRowId()));
            holdingRepository.save(created);
            return created;
        }
        holding.applyTrade(quantityDelta, costDelta);
        // 다 팔면 보유가 사라진다 — 0 주를 들고 있는 행은 목록만 어지럽힌다.
        if (holding.getQuantity() == null || holding.getQuantity().signum() <= 0) {
            holding.deleteHolding();
        }
        return holding;
    }

    private int nextSortOrder(Long assetRowId) {
        return holdingRepository.findActiveByAsset(assetRowId).stream()
            .filter(h -> h.getIsDeleted() == YNType.N)
            .mapToInt(h -> h.getSortOrder() != null ? h.getSortOrder() : 0)
            .max().orElse(-1) + 1;
    }

    /**
     * 실현손익을 가계부 거래로 남긴다 — 이익은 수입, 손실은 지출.
     *
     * <p>잔액 이력은 남기지 않는다. 매도 대금이 이미 예수금 flow 로 들어왔으므로 여기서 또
     * 반영하면 이중 계상이 된다(대출 이자 지출과 같은 취급).
     */
    private Expense createRealizedExpense(User user, Asset asset, AssetTrade trade, long realized) {
        Expense pl = Expense.createExpense(
            user, null, asset,
            realized > 0 ? ExpenseType.INCOME : ExpenseType.EXPENSE,
            Math.abs(realized),
            trade.getHoldingKey(), trade.getTradeDate(),
            null, "TRADE", null, null, null, null, null);
        pl.markAutoGenerated("TRADE_REALIZED");
        expenseRepository.save(pl);
        return pl;
    }
}
