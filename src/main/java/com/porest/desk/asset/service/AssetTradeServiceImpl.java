package com.porest.desk.asset.service;

import com.porest.core.time.UserClock;
import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.domain.AssetHolding;
import com.porest.desk.asset.domain.AssetTrade;
import com.porest.desk.asset.repository.AssetHoldingRepository;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.asset.repository.AssetTradeRepository;
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

        AssetHolding holding = findHolding(asset.getRowId(), command.holdingKey());
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
        // 현실에서 불가능한 매수를 막는다 — 예수금이 없으면 살 수 없다.
        if (type == TradeType.BUY) {
            long cash = asset.getCashBalance() != null ? asset.getCashBalance() : 0L;
            if (cash + cashDelta < 0) {
                throw new InvalidValueException(DeskErrorCode.ASSET_TRADE_INSUFFICIENT_CASH);
            }
        }

        AssetTrade trade = AssetTrade.create(user, asset, type,
            command.holdingType() != null ? command.holdingType() : HoldingType.STOCK,
            command.holdingKey(),
            Boolean.TRUE.equals(command.linked()) ? YNType.Y : YNType.N,
            quantity, amount, fee, quantityDelta, costDelta,
            command.tradeDate() != null ? command.tradeDate() : userClock.now(command.userRowId()),
            command.description());
        tradeRepository.save(trade);

        applyToHolding(asset, command, holding, quantityDelta, costDelta);
        // 예수금 flow — 평가금액은 시세×수량으로 따로 산정되므로 건드리지 않는다.
        // 기초 보유는 돈이 오간 적이 없어 이력을 남기지 않는다.
        if (cashDelta != 0L) {
            balanceHistoryService.recordTrade(asset, trade.getRowId(), cashDelta, trade.getTradeDate());
        }

        if (realized != null && realized != 0L) {
            Expense pl = createRealizedExpense(user, asset, trade, realized);
            trade.recordRealized(realized, pl.getRowId());
        } else if (realized != null) {
            trade.recordRealized(0L, null);
        }

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
        AssetHolding holding = findHolding(asset.getRowId(), trade.getHoldingKey());
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
        if (trade.getRealizedExpenseRowId() != null) {
            expenseRepository.findById(trade.getRealizedExpenseRowId())
                .ifPresent(expenseRepository::delete);
        }
        trade.deleteTrade();
        log.info("투자 거래 취소: tradeId={}, assetId={}", tradeRowId, asset.getRowId());
    }

    @Override
    public List<AssetTradeServiceDto.TradeInfo> getTrades(Long assetRowId, Long userRowId) {
        findInvestmentAsset(assetRowId, userRowId);
        return tradeRepository.findActiveByAsset(assetRowId, YNType.N).stream()
            .map(AssetTradeServiceDto.TradeInfo::from)
            .toList();
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

    private AssetHolding findHolding(Long assetRowId, String holdingKey) {
        if (holdingKey == null) {
            return null;
        }
        return holdingRepository.findActiveByAsset(assetRowId).stream()
            .filter(h -> h.getIsDeleted() == YNType.N)
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

    private void applyToHolding(Asset asset, AssetTradeServiceDto.CreateTradeCommand command,
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
            return;
        }
        holding.applyTrade(quantityDelta, costDelta);
        // 다 팔면 보유가 사라진다 — 0 주를 들고 있는 행은 목록만 어지럽힌다.
        if (holding.getQuantity() == null || holding.getQuantity().signum() <= 0) {
            holding.deleteHolding();
        }
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
        expenseRepository.save(pl);
        return pl;
    }
}
