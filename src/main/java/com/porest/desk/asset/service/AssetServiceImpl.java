package com.porest.desk.asset.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.domain.AssetHolding;
import com.porest.desk.asset.domain.AssetTransfer;
import com.porest.desk.asset.repository.AssetHoldingRepository;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.asset.repository.AssetTransferRepository;
import com.porest.desk.asset.service.AssetBalanceHistoryService.BalanceResolver;
import com.porest.desk.asset.service.dto.AssetServiceDto;
import com.porest.desk.asset.type.AssetType;
import com.porest.desk.asset.type.HoldingType;
import com.porest.desk.card.domain.CardCatalog;
import com.porest.desk.card.domain.CardBilling;
import com.porest.desk.card.repository.CardBillingRepository;
import com.porest.desk.card.repository.CardCatalogRepository;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.subscription.service.SubscriptionEntitlementService;
import com.porest.desk.toss.credential.service.TossCredentialService;
import com.porest.desk.toss.dto.TossMarketDto;
import com.porest.desk.toss.service.TossQueryService;
import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import com.porest.core.time.UserClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AssetServiceImpl implements AssetService {
    private static final Set<AssetType> DEBT_TYPES = Set.of(AssetType.CREDIT_CARD, AssetType.LOAN);
    private static final String FEATURE_SECURITIES = "SECURITIES";

    private final AssetRepository assetRepository;
    private final AssetHoldingRepository assetHoldingRepository;
    private final AssetTransferRepository assetTransferRepository;
    private final UserRepository userRepository;
    private final CardCatalogRepository cardCatalogRepository;
    // 청구 회차 정합용 — 카드 서비스가 아니라 리포지토리만 참조한다(서비스끼리는 순환).
    private final CardBillingRepository cardBillingRepository;
    private final ExpenseRepository expenseRepository;
    private final AssetBalanceHistoryService balanceHistoryService;
    private final UserClock userClock;
    private final SubscriptionEntitlementService entitlementService;
    private final TossCredentialService tossCredentialService;
    private final TossQueryService tossQueryService;

    @Override
    @Transactional
    public AssetServiceDto.AssetInfo createAsset(AssetServiceDto.CreateAssetCommand command) {
        log.debug("자산 등록 시작: userRowId={}, assetName={}", command.userRowId(), command.assetName());

        User user = userRepository.findById(command.userRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

        validateHoldings(command.assetType(), command.holdings());

        CardCatalog cardCatalog = resolveCardCatalog(command.cardCatalogRowId());
        Asset paymentAsset = resolvePaymentAsset(command.paymentAssetRowId(), command.userRowId());

        // 보유가 있는 투자 자산은 평가액을 서버가 산정한다 — 클라이언트가 보낸 balance 를 쓰지 않는다.
        // 연동 시세를 못 구하면 미연동 합만 잡고, 나머지는 일 1회 스냅샷 배치가 채운다.
        //
        // 평가액은 예수금(balance)이 아니라 HOLDING 채널 앵커로 따로 찍는다. 한 칸에 담으면
        // 다음 평가 갱신이 그 사이 들어온 이체를 덮어써 돈이 사라진다.
        Long balance = command.balance();
        Long holdingValuation = null;
        if (command.assetType() == AssetType.INVESTMENT
            && command.holdings() != null && !command.holdings().isEmpty()) {
            Long computed = computeInvestmentBalance(command.userRowId(), command.holdings());
            holdingValuation = computed != null ? computed : manualHoldingsSum(command.holdings());
            balance = 0L; // 예수금은 비어서 시작 — 증권계좌에 넣은 돈은 이체로 들어온다
        }

        Asset asset = Asset.createAsset(
            user,
            command.assetName(),
            command.assetType(),
            balance,
            command.currency() != null ? command.currency() : "KRW",
            command.exchangeRate(),
            command.color(),
            command.institution(),
            command.memo(),
            command.sortOrder() != null ? command.sortOrder() : 0,
            command.isIncludedInTotal(),
            cardCatalog,
            command.creditLimit(),
            command.paymentDay(),
            paymentAsset
        );

        assetRepository.save(asset);
        List<AssetServiceDto.HoldingInfo> holdings = saveHoldings(asset, command.holdings());
        // 잔액 이력: 초기 예수금 절대 앵커 + (보유가 있으면) 평가금액 앵커
        LocalDateTime createdAt = userClock.now(command.userRowId());
        balanceHistoryService.recordInit(asset, createdAt);
        if (holdingValuation != null) {
            balanceHistoryService.recordValuation(asset, holdingValuation, createdAt);
        }
        log.info("자산 등록 완료: assetId={}, userRowId={}", asset.getRowId(), command.userRowId());

        return AssetServiceDto.AssetInfo.from(asset, holdings);
    }

    @Override
    public List<AssetServiceDto.AssetInfo> getAssets(Long userRowId) {
        log.debug("자산 목록 조회: userRowId={}", userRowId);

        List<Asset> assets = assetRepository.findByUser(userRowId);
        if (assets.isEmpty()) {
            return List.of();
        }
        // 보유는 in-query 1회 일괄 로딩 — 자산별 N+1 금지.
        List<Long> ids = assets.stream().map(Asset::getRowId).toList();
        Map<Long, List<AssetServiceDto.HoldingInfo>> holdingsByAsset =
            assetHoldingRepository.findActiveByAssets(ids).stream()
                .collect(Collectors.groupingBy(h -> h.getAsset().getRowId(),
                    Collectors.mapping(AssetServiceDto.HoldingInfo::from, Collectors.toList())));

        // 잔액은 캐시 컬럼이 아니라 이력에서 산정한다 — 요약·순자산·추이가 쓰는 것과 같은 계산이라
        // 목록 금액과 총합이 어긋날 여지가 없다(캐시가 낡아 있어도 스스로 맞춰진다).
        LocalDateTime now = userClock.now(userRowId);
        Map<Long, AssetBalanceHistoryService.Split> balances =
            balanceHistoryService.balancesAt(assets, now);

        return assets.stream()
            .map(a -> AssetServiceDto.AssetInfo.from(
                a,
                holdingsByAsset.getOrDefault(a.getRowId(), List.of()),
                balances.getOrDefault(a.getRowId(), AssetBalanceHistoryService.Split.ZERO)))
            .toList();
    }

    @Override
    public AssetServiceDto.AssetInfo getAsset(Long assetId, Long userRowId) {
        log.debug("자산 상세 조회: assetId={}", assetId);

        Asset asset = findAssetOrThrow(assetId);
        validateAssetOwnership(asset, userRowId);
        return AssetServiceDto.AssetInfo.from(asset, activeHoldingInfos(assetId));
    }

    @Override
    @Transactional
    public AssetServiceDto.AssetInfo updateAsset(Long assetId, Long userRowId, AssetServiceDto.UpdateAssetCommand command) {
        log.debug("자산 수정 시작: assetId={}", assetId);

        Asset asset = findAssetOrThrow(assetId);
        validateAssetOwnership(asset, userRowId);

        CardCatalog cardCatalog = resolveCardCatalog(command.cardCatalogRowId());
        // paymentAssetRowId 가 들어온 경우에만 로드 — null 이면 기존 연관 유지(partial update).
        Asset paymentAsset = resolvePaymentAsset(command.paymentAssetRowId(), userRowId);

        // 필수 필드(NOT NULL)는 null 이면 기존 값 유지 — partial update 허용.
        // 선택 필드(color/institution/memo) 는 null 을 clear 로 간주.
        // 보유를 함께 보낸 투자 자산은 평가액을 서버가 산정한다(클라이언트 계산값 불신).
        // 연동 시세를 못 구하면 기존 평가금액을 유지한다 — 부분합으로 덮어쓰지 않기 위해서다.
        //
        // 갱신 대상은 HOLDING 채널이다. 예수금은 건드리지 않으므로, 이 자산으로 들어온
        // 이체는 평가액을 몇 번 다시 계산하든 그대로 남는다.
        // 빈 리스트도 받는다 — 보유를 전부 지우면 평가금액이 0 이 돼야 한다.
        boolean investHoldings = command.holdings() != null
            && (command.assetType() != null ? command.assetType() : asset.getAssetType()) == AssetType.INVESTMENT;
        // 보유가 남아 있는지 — 빈 리스트(전량 매도·전부 삭제)와 구분해야 한다.
        boolean hasHoldings = investHoldings && !command.holdings().isEmpty();
        Long holdingValuation = null;
        if (investHoldings) {
            Long computed = computeInvestmentBalance(userRowId, command.holdings());
            holdingValuation = computed != null ? computed : asset.getHoldingBalance();
        }
        Long oldPaymentAssetRowId = asset.getPaymentAsset() != null
            ? asset.getPaymentAsset().getRowId() : null;
        asset.updateAsset(
            command.assetName() != null ? command.assetName() : asset.getAssetName(),
            command.assetType() != null ? command.assetType() : asset.getAssetType(),
            command.currency()  != null ? command.currency()  : asset.getCurrency(),
            command.exchangeRate() != null ? command.exchangeRate() : asset.getExchangeRate(),
            command.color(),
            command.institution(),
            command.memo(),
            command.isIncludedInTotal(),
            cardCatalog,
            command.creditLimit(),
            command.paymentDay(),
            paymentAsset
        );

        // 평가금액(HOLDING)과 예수금(CASH)은 서로 다른 칸이라 각각 반영한다.
        // 한쪽 가지가 다른 쪽을 막으면 전량 매도처럼 두 칸이 동시에 바뀌는 상황에서 입력이 버려진다.
        if (investHoldings && !Objects.equals(asset.getHoldingBalance(), holdingValuation)) {
            balanceHistoryService.recordValuation(asset, holdingValuation, userClock.now(userRowId));
        }
        // 예수금은 보유가 없을 때만 잔액칸으로 조정한다 — 보유가 있으면 그 값은 평가금액을 포함한
        // 총액이라 예수금 앵커로 찍으면 이중 계상된다.
        //
        // 비교 대상이 총액이 아니라 예수금인 게 핵심이다. 전량 매도로 평가금액이 예수금으로
        // 옮겨오면 총액은 그대로인 채 칸만 바뀌는데, 총액끼리 비교하면 '안 바뀌었다'로 보여
        // 매도 대금이 통째로 사라진다.
        if (!hasHoldings && command.balance() != null
            && !Objects.equals(asset.getCashBalance(), command.balance())) {
            balanceHistoryService.recordManual(asset, command.balance(), userClock.now(userRowId));
        }

        // 체크카드 연결 계좌가 바뀌면 그 카드로 쓴 기존 지출 이력도 새 계좌로 옮긴다 —
        // 신규만 옮기면 과거는 카드에, 신규는 계좌에 쌓여 어느 쪽 잔액도 맞지 않는다.
        if (asset.getAssetType() == AssetType.CHECK_CARD
            && asset.getPaymentAsset() != null
            && !Objects.equals(oldPaymentAssetRowId, asset.getPaymentAsset().getRowId())) {
            balanceHistoryService.relinkCheckCardHistory(asset, asset.getPaymentAsset());
        }

        // 보유 교체 — null=무변경, 리스트=전체 교체(기존 활성 soft delete 후 신규 insert).
        List<AssetServiceDto.HoldingInfo> holdings;
        if (command.holdings() != null) {
            validateHoldings(asset.getAssetType(), command.holdings());
            for (AssetHolding old : assetHoldingRepository.findActiveByAsset(assetId)) {
                old.deleteHolding(); // dirty checking — UPDATE 는 save 안 함
            }
            holdings = saveHoldings(asset, command.holdings());
        } else {
            holdings = activeHoldingInfos(assetId);
        }

        log.info("자산 수정 완료: assetId={}", assetId);
        return AssetServiceDto.AssetInfo.from(asset, holdings);
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
    @Transactional
    public AssetServiceDto.AssetInfo linkTossSymbol(Long assetId, Long userRowId, String symbol, Long quantity) {
        log.debug("자산 토스 연결 시작: assetId={}, symbol={}, quantity={}", assetId, symbol, quantity);

        // 게이트: 프로(SECURITIES) 구독 + 토스 연결 사용자만 연결 가능.
        entitlementService.requireFeature(userRowId, FEATURE_SECURITIES);
        if (!tossCredentialService.getStatus(userRowId).connected()) {
            log.warn("자산 토스 연결 거부 - 토스 미연결: userRowId={}", userRowId);
            throw new ForbiddenException(DeskErrorCode.TOSS_CREDENTIAL_REQUIRED);
        }
        // 종목코드 + 보유수량(양수) 필수. 평가액 = 토스 시세 × 수량.
        if (symbol == null || symbol.isBlank() || quantity == null || quantity <= 0) {
            throw new InvalidValueException(DeskErrorCode.INVALID_INPUT);
        }

        Asset asset = findAssetOrThrow(assetId);
        validateAssetOwnership(asset, userRowId);
        // 종목 연결은 투자(INVESTMENT) 자산에만 허용.
        if (asset.getAssetType() != AssetType.INVESTMENT) {
            log.warn("자산 토스 연결 거부 - INVESTMENT 자산 아님: assetId={}, type={}", assetId, asset.getAssetType());
            throw new InvalidValueException(DeskErrorCode.INVALID_INPUT);
        }
        // 토스가 시세를 주는 유효 종목인지 검증 — 잘못된 코드 연결 차단(정합성의 최종 판정).
        if (!isTossPriceAvailable(userRowId, symbol)) {
            log.warn("자산 토스 연결 거부 - 토스 미인식 종목: symbol={}", symbol);
            throw new InvalidValueException(DeskErrorCode.TOSS_SYMBOL_INVALID);
        }

        asset.linkToss(symbol, quantity);
        // 연결 즉시 평가액 1회 스냅샷 — 추이 그래프에 바로 반영(환율 미확보 외화면 생략).
        Long valuation = computeTossValuationKrw(userRowId, symbol, BigDecimal.valueOf(quantity));
        if (valuation != null) {
            balanceHistoryService.recordValuation(asset, valuation, userClock.now(userRowId));
        }
        log.info("자산 토스 연결 완료: assetId={}, symbol={}, quantity={}", assetId, symbol, quantity);
        return AssetServiceDto.AssetInfo.from(asset);
    }

    @Override
    @Transactional
    public AssetServiceDto.AssetInfo unlinkTossSymbol(Long assetId, Long userRowId) {
        log.debug("자산 토스 연결 해제 시작: assetId={}", assetId);

        // 해제는 구독 만료 후에도 가능해야 하므로 소유권만 검증.
        Asset asset = findAssetOrThrow(assetId);
        validateAssetOwnership(asset, userRowId);

        // 해제 순간의 마지막 평가액(시세×수량)을 자산 금액으로 굳힌다 — 자동 연동만 끄고 마지막 본 금액 유지.
        // 토스 시세 미수신(미연결/조회실패) 시엔 굳히지 않고 기존 잔액을 유지한다.
        if (asset.isTossLinked() && asset.getTossQuantity() != null) {
            Long valuation = computeTossValuationKrw(
                userRowId, asset.getTossSymbol(), BigDecimal.valueOf(asset.getTossQuantity()));
            if (valuation != null) {
                // 굳히는 값도 '보유 평가금액' 이다 — CASH 로 찍으면 예수금이 평가액만큼 부풀고
                // 기존 HOLDING 앵커와 이중으로 더해진다.
                balanceHistoryService.recordValuation(asset, valuation, userClock.now(userRowId));
            }
        }

        asset.unlinkToss();
        log.info("자산 토스 연결 해제 완료: assetId={}", assetId);
        return AssetServiceDto.AssetInfo.from(asset);
    }

    /**
     * 보유 입력 검증 — INVESTMENT 전용.
     * linked=Y 는 주식만 가능하며 종목코드+수량 필수, linked=N 은 이름+평가액 필수(수량은 선택).
     */
    private void validateHoldings(AssetType assetType, List<AssetServiceDto.HoldingCommand> holdings) {
        if (holdings == null || holdings.isEmpty()) {
            return;
        }
        if (assetType != AssetType.INVESTMENT) {
            throw new InvalidValueException(DeskErrorCode.INVALID_INPUT);
        }
        for (AssetServiceDto.HoldingCommand hc : holdings) {
            boolean linked = Boolean.TRUE.equals(hc.linked());
            HoldingType type = hc.holdingType() != null ? hc.holdingType() : HoldingType.STOCK;

            // 수량은 유형·연동 여부와 무관하게 음수를 허용하지 않는다.
            if (hc.quantity() != null && hc.quantity().signum() < 0) {
                throw new InvalidValueException(DeskErrorCode.INVALID_INPUT);
            }

            if (linked) {
                // 토스는 국내·미국 주식 시세만 제공한다 — 금·코인은 연동 대상이 아니다.
                if (type != HoldingType.STOCK) {
                    throw new InvalidValueException(DeskErrorCode.INVALID_INPUT);
                }
                if (hc.tossSymbol() == null || hc.tossSymbol().isBlank() || hc.quantity() == null) {
                    throw new InvalidValueException(DeskErrorCode.INVALID_INPUT);
                }
            } else {
                if (hc.holdingName() == null || hc.holdingName().isBlank() || hc.holdingValue() == null) {
                    throw new InvalidValueException(DeskErrorCode.INVALID_INPUT);
                }
            }
        }
    }

    /** 미연동 보유의 입력 금액 합 — 정수라 오차가 없다. */
    private static long manualHoldingsSum(List<AssetServiceDto.HoldingCommand> holdings) {
        long sum = 0;
        for (AssetServiceDto.HoldingCommand hc : holdings) {
            if (!Boolean.TRUE.equals(hc.linked()) && hc.holdingValue() != null) {
                sum += hc.holdingValue();
            }
        }
        return sum;
    }

    /**
     * 투자 자산의 평가액을 <b>서버가</b> 산정한다.
     *
     * <p>이전에는 클라이언트가 시세×수량을 계산해 balance 로 보냈다. JS·Dart 는 십진 소수를 정확히
     * 담지 못해 저장값이 어긋날 수 있고, 무엇보다 DB 에 남는 금액을 클라이언트가 정하는 구조였다.
     *
     * <p>미연동 보유는 입력 금액 그대로(정수), 연동 보유는 토스 시세×수량을 BigDecimal 로 계산한다.
     * 시세 조회는 종목 다건 1콜 + 외화가 있을 때만 환율 1콜로 끝낸다.
     *
     * @return 연동 시세를 하나라도 못 구하면 null — 부분합으로 자산 금액을 왜곡하지 않기 위해서다.
     *         호출부가 생성이면 미연동 합, 수정이면 기존 잔액을 쓴다.
     */
    private Long computeInvestmentBalance(Long userRowId, List<AssetServiceDto.HoldingCommand> holdings) {
        long manual = manualHoldingsSum(holdings);
        List<AssetServiceDto.HoldingCommand> linked = holdings.stream()
            .filter(hc -> Boolean.TRUE.equals(hc.linked()) && hc.tossSymbol() != null && hc.quantity() != null)
            .toList();
        if (linked.isEmpty()) {
            return manual;
        }
        try {
            String symbols = linked.stream()
                .map(AssetServiceDto.HoldingCommand::tossSymbol)
                .distinct()
                .collect(Collectors.joining(","));
            Map<String, TossMarketDto.PriceResponse> priceBySymbol = new HashMap<>();
            for (TossMarketDto.PriceResponse p : tossQueryService.getPrices(userRowId, symbols)) {
                priceBySymbol.put(p.symbol(), p);
            }

            BigDecimal fx = null;
            BigDecimal sum = BigDecimal.ZERO;
            for (AssetServiceDto.HoldingCommand hc : linked) {
                TossMarketDto.PriceResponse p = priceBySymbol.get(hc.tossSymbol());
                BigDecimal price = p != null ? parsePrice(p.lastPrice()) : null;
                if (price == null) {
                    return null;
                }
                BigDecimal krw;
                if (p.currency() == null || "KRW".equals(p.currency())) {
                    krw = price;
                } else {
                    if (fx == null) {
                        fx = parsePrice(tossQueryService.getExchangeRate(userRowId, "USD", "KRW", null).rate());
                    }
                    if (fx == null || fx.signum() <= 0) {
                        return null; // 외화인데 환율 미확보
                    }
                    krw = price.multiply(fx);
                }
                sum = sum.add(krw.multiply(hc.quantity()));
            }
            return toWon(sum) + manual;
        } catch (Exception ex) {
            log.warn("투자 평가액 산정 실패 - userRowId={}: {}", userRowId, ex.getMessage());
            return null;
        }
    }

    /** 보유 신규 저장 — sortOrder = 배열 인덱스. */
    private List<AssetServiceDto.HoldingInfo> saveHoldings(Asset asset, List<AssetServiceDto.HoldingCommand> holdings) {
        if (holdings == null || holdings.isEmpty()) {
            return List.of();
        }
        // 편집은 보유 목록을 통째로 갈아끼운다. 원가는 매수·매도가 쌓은 값이고 편집 폼은
        // 그걸 입력받지 않으므로, 안 보내오면 종목 식별자로 이어 붙인다 — 안 그러면 편집을
        // 저장하는 순간 원가가 0 이 되고 다음 매도에서 대금 전액이 이익으로 잡힌다.
        Map<String, Long> costByKey = assetHoldingRepository.findActiveByAsset(asset.getRowId()).stream()
            .filter(h -> h.holdingKey() != null && h.getTotalCost() != null)
            .collect(Collectors.toMap(AssetHolding::holdingKey, AssetHolding::getTotalCost, (a, b) -> a));
        List<AssetServiceDto.HoldingInfo> result = new ArrayList<>(holdings.size());
        for (int i = 0; i < holdings.size(); i++) {
            AssetServiceDto.HoldingCommand hc = holdings.get(i);
            boolean linked = Boolean.TRUE.equals(hc.linked());
            AssetHolding holding = AssetHolding.create(
                asset,
                hc.holdingType() != null ? hc.holdingType() : HoldingType.STOCK,
                linked ? YNType.Y : YNType.N,
                linked ? hc.tossSymbol() : null,
                // 미연동도 수량을 남긴다 — 몇 주·몇 g·몇 개인지는 평가액과 별개로 기록 가치가 있다(선택 입력).
                hc.quantity(),
                linked ? null : hc.holdingName(),
                linked ? null : hc.holdingValue(),
                resolveCost(hc, costByKey),
                i
            );
            assetHoldingRepository.save(holding);
            result.add(AssetServiceDto.HoldingInfo.from(holding));
        }
        return result;
    }

    /** 원가 — 보내왔으면 그 값, 아니면 같은 종목의 기존 원가를 잇는다. */
    private Long resolveCost(AssetServiceDto.HoldingCommand hc, Map<String, Long> costByKey) {
        if (hc.totalCost() != null) {
            return hc.totalCost();
        }
        String key = Boolean.TRUE.equals(hc.linked()) ? hc.tossSymbol() : hc.holdingName();
        return key != null ? costByKey.get(key) : null;
    }

    private List<AssetServiceDto.HoldingInfo> activeHoldingInfos(Long assetId) {
        return assetHoldingRepository.findActiveByAsset(assetId).stream()
            .map(AssetServiceDto.HoldingInfo::from)
            .toList();
    }

    @Override
    @Transactional
    public void snapshotTossValuations() {
        List<Asset> investments = assetRepository.findAllByType(AssetType.INVESTMENT);
        if (investments.isEmpty()) {
            return;
        }
        // 자산별 유효 평가 소스: 활성 linked 보유(holdings)가 있으면 그 합산,
        // 없으면 레거시 단일 연동(toss_symbol×toss_quantity). 이관 전후 모두 안전.
        Map<Long, List<AssetHolding>> linkedHoldingsByAsset =
            assetHoldingRepository.findActiveByAssets(investments.stream().map(Asset::getRowId).toList())
                .stream()
                .filter(AssetHolding::isLinked)
                .collect(Collectors.groupingBy(h -> h.getAsset().getRowId()));

        List<Asset> linked = investments.stream()
            .filter(a -> linkedHoldingsByAsset.containsKey(a.getRowId()) || a.isTossLinked())
            .toList();
        if (linked.isEmpty()) {
            return;
        }
        // 사용자별로 묶어 시세를 1회 조회 → 종목별 (현재가 × 보유수량)을 VALUATION 앵커로 적재.
        Map<Long, List<Asset>> byUser = linked.stream()
            .collect(Collectors.groupingBy(a -> a.getUser().getRowId()));

        for (Map.Entry<Long, List<Asset>> entry : byUser.entrySet()) {
            Long userRowId = entry.getKey();
            List<Asset> userAssets = entry.getValue();
            try {
                // 게이트: 구독 만료/토스 해제된 사용자는 스냅샷 생략.
                if (!entitlementService.hasFeature(userRowId, FEATURE_SECURITIES)) {
                    continue;
                }
                if (!tossCredentialService.getStatus(userRowId).connected()) {
                    continue;
                }
                String symbols = userAssets.stream()
                    .flatMap(a -> valuationPairs(a, linkedHoldingsByAsset).stream())
                    .map(SymbolQty::symbol)
                    .distinct()
                    .collect(Collectors.joining(","));
                List<TossMarketDto.PriceResponse> prices = tossQueryService.getPrices(userRowId, symbols);
                Map<String, TossMarketDto.PriceResponse> priceBySymbol = new HashMap<>();
                boolean hasForeign = false;
                for (TossMarketDto.PriceResponse p : prices) {
                    priceBySymbol.put(p.symbol(), p);
                    if (p.currency() != null && !"KRW".equals(p.currency())) {
                        hasForeign = true;
                    }
                }
                // 해외 종목(외화)이 있으면 환율(USD→KRW) 1회 조회해 원화 환산.
                BigDecimal fx = null;
                if (hasForeign) {
                    try {
                        fx = parsePrice(
                            tossQueryService.getExchangeRate(userRowId, "USD", "KRW", null).rate());
                    } catch (Exception ex) {
                        log.warn("토스 환율 조회 실패 - userRowId={}: {}", userRowId, ex.getMessage());
                    }
                }
                LocalDateTime now = userClock.now(userRowId);
                for (Asset a : userAssets) {
                    // 보유(linked) 합산 평가 — 한 종목이라도 시세/환율 미확보면 자산 전체 생략(부분합 왜곡 방지).
                    // 소수 수량이 섞여도 어긋나지 않게 합산을 BigDecimal 로 한다(원 단위 반올림은 마지막에 1회).
                    BigDecimal sum = BigDecimal.ZERO;
                    boolean complete = true;
                    List<SymbolQty> pairs = valuationPairs(a, linkedHoldingsByAsset);
                    if (pairs.isEmpty()) {
                        continue;
                    }
                    for (SymbolQty pair : pairs) {
                        TossMarketDto.PriceResponse p = priceBySymbol.get(pair.symbol());
                        BigDecimal price = p != null ? parsePrice(p.lastPrice()) : null;
                        if (price == null) {
                            complete = false;
                            break;
                        }
                        BigDecimal krw;
                        if (p.currency() == null || "KRW".equals(p.currency())) {
                            krw = price;
                        } else if (fx != null && fx.signum() > 0) {
                            krw = price.multiply(fx);
                        } else {
                            complete = false; // 외화인데 환율 미확보
                            break;
                        }
                        sum = sum.add(krw.multiply(pair.qty()));
                    }
                    if (!complete) {
                        continue;
                    }
                    balanceHistoryService.recordValuation(a, toWon(sum), now);
                }
            } catch (Exception ex) {
                // 사용자 단위 격리 — 한 명의 토스 조회 실패가 전체를 막지 않게.
                log.warn("토스 평가액 스냅샷 실패 - userRowId={}: {}", userRowId, ex.getMessage());
            }
        }
    }

    /** 자산의 평가 대상 (종목코드, 수량) 목록 — 활성 linked 보유 우선, 없으면 레거시 단일 연동. */
    private List<SymbolQty> valuationPairs(Asset asset, Map<Long, List<AssetHolding>> linkedHoldingsByAsset) {
        List<AssetHolding> holdings = linkedHoldingsByAsset.get(asset.getRowId());
        if (holdings != null && !holdings.isEmpty()) {
            return holdings.stream()
                .filter(h -> h.getTossSymbol() != null && h.getQuantity() != null)
                .map(h -> new SymbolQty(h.getTossSymbol(), h.getQuantity()))
                .toList();
        }
        if (asset.isTossLinked() && asset.getTossQuantity() != null) {
            return List.of(new SymbolQty(asset.getTossSymbol(), BigDecimal.valueOf(asset.getTossQuantity())));
        }
        return List.of();
    }

    private record SymbolQty(String symbol, BigDecimal qty) {}

    /** 토스가 해당 종목코드의 시세를 제공하는지 — 유효 종목 검증(미인식/조회실패 시 false). */
    private boolean isTossPriceAvailable(Long userRowId, String symbol) {
        try {
            List<TossMarketDto.PriceResponse> prices = tossQueryService.getPrices(userRowId, symbol);
            return prices != null && prices.stream().anyMatch(p ->
                symbol.equalsIgnoreCase(p.symbol()) && p.lastPrice() != null && !p.lastPrice().isBlank());
        } catch (Exception ex) {
            log.warn("토스 종목 시세 검증 실패 - symbol={}: {}", symbol, ex.getMessage());
            return false;
        }
    }

    /**
     * 토스 시세(외화면 환율 환산) × 수량 = 원화 평가액. 시세 미수신/조회 실패 시 null.
     *
     * <p>수량이 소수(코인·소수점 주식)일 수 있어 곱셈을 전부 BigDecimal 로 한다 —
     * double 로 넘기면 0.1 같은 값이 이진수로 정확히 떨어지지 않아 평가액이 어긋난다.
     */
    private Long computeTossValuationKrw(Long userRowId, String symbol, BigDecimal quantity) {
        try {
            TossMarketDto.PriceResponse p = tossQueryService.getPrices(userRowId, symbol).stream()
                .filter(x -> symbol.equalsIgnoreCase(x.symbol()))
                .findFirst().orElse(null);
            if (p == null) {
                return null;
            }
            BigDecimal price = parsePrice(p.lastPrice());
            if (price == null) {
                return null;
            }
            BigDecimal krw;
            if (p.currency() == null || "KRW".equals(p.currency())) {
                krw = price;
            } else {
                BigDecimal r = parsePrice(tossQueryService.getExchangeRate(userRowId, "USD", "KRW", null).rate());
                if (r == null || r.signum() <= 0) {
                    return null;
                }
                krw = price.multiply(r);
            }
            return toWon(krw.multiply(quantity));
        } catch (Exception ex) {
            log.warn("토스 평가액 계산 실패 - symbol={}: {}", symbol, ex.getMessage());
            return null;
        }
    }

    /**
     * 토스가 내려주는 가격·환율 문자열을 오차 없이 파싱한다.
     *
     * <p>토스가 시세를 String 으로 주는 이유가 정밀도 보존이다. double 로 받으면 그 의도가 무너지므로
     * {@code new BigDecimal(String)} 으로 문자열의 십진 값을 그대로 가져온다
     * ({@code BigDecimal.valueOf(double)} 이 아니다 — 그건 이미 오차가 섞인 double 을 거친다).
     */
    private static BigDecimal parsePrice(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** 원화 평가액 반올림 — 화폐 단위(원)로 떨어뜨린다. */
    private static long toWon(BigDecimal krw) {
        return krw.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    @Override
    public AssetServiceDto.AssetSummary getAssetSummary(Long userRowId, Integer year, Integer month) {
        log.debug("자산 요약 조회: userRowId={}, year={}, month={}", userRowId, year, month);

        List<Asset> included = includedAssets(userRowId);
        BalanceResolver resolver = balanceHistoryService.resolverFor(included);

        LocalDate today = userClock.today(userRowId);
        boolean isPastPeriod = year != null && month != null
            && !(year == today.getYear() && month == today.getMonthValue());

        LocalDateTime asOf;
        LocalDateTime prevAsOf;
        if (!isPastPeriod) {
            // 현재 월(또는 year/month 미지정): 지금 시각 기준
            asOf = userClock.now(userRowId);
            prevAsOf = today.withDayOfMonth(1).minusDays(1).atTime(LocalTime.MAX);
        } else {
            // 과거 월: 선택 월 말 / 전월 말 시점
            LocalDate selectedMonthEnd = LocalDate.of(year, month, 1).with(TemporalAdjusters.lastDayOfMonth());
            asOf = selectedMonthEnd.atTime(LocalTime.MAX);
            prevAsOf = selectedMonthEnd.minusMonths(1)
                .with(TemporalAdjusters.lastDayOfMonth()).atTime(LocalTime.MAX);
        }
        return buildSummaryAt(included, asOf, prevAsOf);
    }

    /** 기준시각(asOf) 잔액으로 요약을 구성하고, 전월 시점(prevAsOf) 순자산과의 증감을 계산. */
    private AssetServiceDto.AssetSummary buildSummaryAt(List<Asset> included,
                                                        LocalDateTime asOf, LocalDateTime prevAsOf) {
        // 두 시점을 한 번씩 — DB 가 집계한다(이력을 앱으로 가져오지 않는다).
        Map<Long, AssetBalanceHistoryService.Split> at = balanceHistoryService.balancesAt(included, asOf);
        Map<Long, AssetBalanceHistoryService.Split> prev = balanceHistoryService.balancesAt(included, prevAsOf);
        long totalBalance = 0, totalAssets = 0, totalDebt = 0;
        Map<AssetType, long[]> byTypeAcc = new EnumMap<>(AssetType.class); // long[2] = { sumBalance, count }
        for (Asset a : included) {
            // 외화 자산은 원화로 환산해 더한다 — 환산하지 않으면 USD 1,000 잔고가
            // 순자산에 1,000원으로 들어가 합계가 무너진다(원화 자산은 환산율 1이라 그대로).
            long bal = a.balanceInKrw(totalOf(at, a));
            totalBalance += bal;
            if (DEBT_TYPES.contains(a.getAssetType())) {
                totalDebt += Math.abs(bal);
            } else {
                totalAssets += bal;
            }
            long[] acc = byTypeAcc.computeIfAbsent(a.getAssetType(), k -> new long[]{0, 0});
            acc[0] += bal;
            acc[1] += 1;
        }
        long netWorth = totalAssets - totalDebt;

        long lastMonthNetWorth = netWorthOf(included, prev);
        long changeAmount = netWorth - lastMonthNetWorth;
        double changePercent = lastMonthNetWorth == 0
            ? 0.0
            : Math.round(((double) changeAmount / Math.abs(lastMonthNetWorth)) * 1000.0) / 10.0;

        List<AssetServiceDto.AssetTypeSummary> byType = byTypeAcc.entrySet().stream()
            .map(e -> new AssetServiceDto.AssetTypeSummary(e.getKey(), e.getValue()[0], (int) e.getValue()[1]))
            .toList();

        return new AssetServiceDto.AssetSummary(
            totalBalance, totalAssets, totalDebt, netWorth,
            lastMonthNetWorth, changeAmount, changePercent, byType
        );
    }

    @Override
    public List<AssetServiceDto.NetWorthTrendPoint> getNetWorthTrend(Long userRowId, Integer months) {
        if (months != null && months < 0) {
            throw new InvalidValueException(DeskErrorCode.INVALID_INPUT);
        }
        int n = (months == null || months < 1) ? 12 : Math.min(months, 36);
        log.debug("순자산 추이 조회: userRowId={}, months={}", userRowId, n);

        List<Asset> included = includedAssets(userRowId);
        LocalDate today = userClock.today(userRowId);
        LocalDateTime now = userClock.now(userRowId);

        // 시점마다 쿼리를 날리면 12개월에 12번이다. 시점 목록을 한 번에 넘겨
        // 집계 2회(시작 시점 + 일자별 변동)로 끝낸다.
        List<LocalDate> labels = new ArrayList<>(n);
        List<LocalDateTime> asOfs = new ArrayList<>(n);
        for (int i = n - 1; i >= 0; i--) {
            LocalDate m = today.minusMonths(i);
            labels.add(m);
            // 현재 월(i=0)은 지금 시각, 과거 월은 월말 23:59:59.999999 → 현재 점 = summary netWorth 와 동일
            asOfs.add(i == 0 ? now : m.with(TemporalAdjusters.lastDayOfMonth()).atTime(LocalTime.MAX));
        }
        Map<Long, List<AssetBalanceHistoryService.Split>> series =
            balanceHistoryService.balancesAtPoints(included, asOfs);

        List<AssetServiceDto.NetWorthTrendPoint> points = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            long assets = 0, debt = 0;
            for (Asset a : included) {
                List<AssetBalanceHistoryService.Split> seq = series.get(a.getRowId());
                long bal = a.balanceInKrw(
                    seq == null ? 0L : seq.get(i).total());
                if (DEBT_TYPES.contains(a.getAssetType())) {
                    debt += Math.abs(bal);
                } else {
                    assets += bal;
                }
            }
            LocalDate m = labels.get(i);
            points.add(new AssetServiceDto.NetWorthTrendPoint(m.getYear(), m.getMonthValue(), assets - debt));
        }
        return points;
    }

    @Override
    public List<AssetServiceDto.AssetBalancePoint> getAssetBalanceTrend(Long assetId, Long userRowId, Integer weeks) {
        if (weeks != null && weeks < 0) {
            throw new InvalidValueException(DeskErrorCode.INVALID_INPUT);
        }
        int n = (weeks == null || weeks < 1) ? 12 : Math.min(weeks, 104);
        log.debug("자산 잔액 추이 조회: assetId={}, weeks={}", assetId, n);

        Asset asset = findAssetOrThrow(assetId);
        validateAssetOwnership(asset, userRowId);

        // window: 이번 주 월요일 기준 n-1주 전 ~ 이번 주
        LocalDate today = userClock.today(userRowId);
        LocalDate currentMonday = today.with(DayOfWeek.MONDAY);
        LocalDate firstMonday = currentMonday.minusWeeks(n - 1);
        LocalDateTime now = userClock.now(userRowId);

        // 주마다 쿼리를 날리면 26주에 26번이다 — 시점 목록을 한 번에 넘겨 집계 2회로 끝낸다.
        List<LocalDate> weekStarts = new ArrayList<>(n);
        List<LocalDateTime> asOfs = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            LocalDate weekStart = firstMonday.plusWeeks(i);
            weekStarts.add(weekStart);
            // 그 주 일요일 끝 시점(미래면 지금) 기준 잔액
            LocalDateTime asOf = weekStart.plusDays(6).atTime(LocalTime.MAX);
            asOfs.add(asOf.isAfter(now) ? now : asOf);
        }
        List<AssetBalanceHistoryService.Split> series = balanceHistoryService
            .balancesAtPoints(List.of(asset), asOfs)
            .getOrDefault(asset.getRowId(), List.of());

        List<AssetServiceDto.AssetBalancePoint> points = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            long balance = i < series.size() ? series.get(i).total() : 0L;
            points.add(new AssetServiceDto.AssetBalancePoint(weekStarts.get(i), balance));
        }
        return points;
    }

    /**
     * 기준시각의 순자산 = Σ(비채무 잔액) − Σ|채무 잔액|. summary/trend 가 공유.
     * 외화 자산은 원화로 환산해 더한다(환산하지 않으면 통화 단위가 섞여 합계가 무의미해진다).
     */
    private long netWorthOf(List<Asset> included, Map<Long, AssetBalanceHistoryService.Split> balances) {
        long assets = 0, debt = 0;
        for (Asset a : included) {
            long bal = a.balanceInKrw(totalOf(balances, a));
            if (DEBT_TYPES.contains(a.getAssetType())) {
                debt += Math.abs(bal);
            } else {
                assets += bal;
            }
        }
        return assets - debt;
    }

    /** 집계 결과에서 그 자산의 총잔액(예수금 + 평가금액). 이력이 없으면 0. */
    private static long totalOf(Map<Long, AssetBalanceHistoryService.Split> balances, Asset a) {
        return balances.getOrDefault(a.getRowId(), AssetBalanceHistoryService.Split.ZERO).total();
    }

    private List<Asset> includedAssets(Long userRowId) {
        return assetRepository.findByUser(userRowId).stream()
            .filter(a -> a.getIsIncludedInTotal() == YNType.Y)
            .toList();
    }

    @Override
    @Transactional
    public void reorderAssets(Long userRowId, List<AssetServiceDto.ReorderItem> items) {
        log.debug("자산 정렬 변경: userRowId={}, count={}", userRowId, items.size());

        for (AssetServiceDto.ReorderItem item : items) {
            Asset asset = findAssetOrThrow(item.assetId());
            validateAssetOwnership(asset, userRowId); // 남의 자산 순서 조작 차단
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

        // 같은 자산으로의 이체는 무의미·잘못된 잔액 이력 유발 — 차단(ASSET_TRANSFER_SAME_ASSET 코드는 있었으나 미적용이었음).
        if (command.fromAssetRowId() != null
            && command.fromAssetRowId().equals(command.toAssetRowId())) {
            throw new InvalidValueException(DeskErrorCode.ASSET_TRANSFER_SAME_ASSET);
        }
        // 이체 금액은 0보다 커야 함 — 음수는 잔액 흐름을 역전시켜 자금이 거꾸로 이동(검증 누락 시 위험).
        if (command.amount() == null || command.amount() <= 0) {
            throw new InvalidValueException(DeskErrorCode.ASSET_TRANSFER_INVALID_AMOUNT);
        }

        Asset fromAsset = findAssetOrThrow(command.fromAssetRowId());
        validateAssetOwnership(fromAsset, command.userRowId());
        Asset toAsset = findAssetOrThrow(command.toAssetRowId());
        validateAssetOwnership(toAsset, command.userRowId());

        // 체크카드는 잔액을 들지 않는다 — 긁는 즉시 연결 계좌에서 빠지므로 이체할 잔액이 없다.
        // 여기로 이체를 걸면 카드에 있을 수 없는 잔액이 생겨 연결 계좌와 어긋난다.
        // 신용카드는 반대로 결제일 자동이체(CardPaymentService)의 대상이라 그대로 허용한다.
        if (fromAsset.getAssetType() == AssetType.CHECK_CARD
            || toAsset.getAssetType() == AssetType.CHECK_CARD) {
            throw new InvalidValueException(DeskErrorCode.ASSET_TRANSFER_CHECK_CARD);
        }

        // 이자는 상환액(amount) 안에 포함된 몫이라 그보다 클 수 없다.
        // 같으면 원금이 0 이라 부채가 전혀 안 줄어드는데, 이자만 내는 거치 상환에서 실제로 있는 일이다.
        long interest = command.interestAmount() != null ? command.interestAmount() : 0L;
        if (interest < 0 || interest > command.amount()) {
            throw new InvalidValueException(DeskErrorCode.ASSET_TRANSFER_INVALID_INTEREST);
        }

        AssetTransfer transfer = AssetTransfer.createTransfer(
            user, fromAsset, toAsset,
            command.amount(), command.fee(), interest, command.description(), command.transferDate()
        );

        assetTransferRepository.save(transfer);

        // 이자는 부채를 줄이지 않고 은행으로 나가는 비용 — 지출 거래로 따로 잡아
        // 카테고리·예산·통계에 들어가게 한다. 이체를 지우면 이 거래도 함께 지운다.
        if (transfer.hasInterest()) {
            Expense interestExpense = Expense.createExpense(
                user, null, fromAsset, ExpenseType.EXPENSE, interest,
                command.description(), command.transferDate(),
                null, "TRANSFER", null, null,
                null, null, null); // 이자는 원화 — 외화 대출은 이체 자체가 환산된 뒤 들어온다
            expenseRepository.save(interestExpense);
            transfer.linkInterestExpense(interestExpense.getRowId());
            log.debug("대출 이자 지출 생성: transferId={}, expenseId={}, interest={}",
                transfer.getRowId(), interestExpense.getRowId(), interest);
        }

        // 자산 잔액 이력: 출금/입금 flow 2건 적재 → recompute 가 양쪽 잔액 반영 (단일 writer)
        // 이자 지출은 잔액에 또 반영하지 않는다 — 출금액(amount)에 이미 포함돼 있어 이중 차감이 된다.
        balanceHistoryService.recordTransfer(transfer);
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

        transfer.deleteTransfer();
        // 이자로 만들어 둔 지출 거래도 함께 무른다 — 남겨두면 이체는 사라졌는데
        // 그 달 지출에 이자만 유령처럼 남는다.
        if (transfer.getInterestExpenseRowId() != null) {
            expenseRepository.findById(transfer.getInterestExpenseRowId())
                .ifPresent(Expense::deleteExpense);
        }
        // 자산 잔액 이력: 양쪽 flow soft-delete → recompute 가 양쪽 잔액 반영 (단일 writer)
        balanceHistoryService.removeTransfer(transferId);
        // 카드 결제로 만들어진 이체였다면 그 청구 회차도 함께 무른다. COMPLETED 로 남겨두면
        // '이미 낸 회차' 로 집계돼(선결제 차감) 다음 청구액이 0 이 되고, 잔액만 되돌아온 채
        // 카드 부채가 영원히 안 갚아진다.
        cardBillingRepository.findActiveByTransfer(transferId).ifPresent(CardBilling::cancel);
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

    private Asset resolvePaymentAsset(Long paymentAssetRowId, Long userRowId) {
        if (paymentAssetRowId == null) {
            return null;
        }
        Asset paymentAsset = findAssetOrThrow(paymentAssetRowId);
        validateAssetOwnership(paymentAsset, userRowId);
        return paymentAsset;
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

    @Override
    @Transactional
    public int recomputeBalances(Long userRowId) {
        int count = balanceHistoryService.recomputeAllForUser(userRowId);
        log.info("자산 잔액 재산정 완료: userRowId={}, assets={}", userRowId, count);
        return count;
    }
}
