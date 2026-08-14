package com.porest.desk.dataimport.sms.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.InvalidValueException;
import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.asset.type.AssetType;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.dataimport.sms.domain.SmsCardMapping;
import com.porest.desk.dataimport.sms.repository.SmsCardMappingRepository;
import com.porest.desk.dataimport.sms.service.dto.SmsImportServiceDto;
import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.desk.expense.repository.ExpenseCategoryRepository;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.service.ExpenseService;
import com.porest.desk.expense.service.dto.ExpenseServiceDto;
import com.porest.desk.expense.type.ExpenseType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 결제 문자 해석·저장 오케스트레이션.
 *
 * <p>파서({@link SmsParser})는 사용자 데이터를 모른다. 여기서 그 위에
 * <b>내 카드 매핑</b>과 <b>내 지난 거래</b>를 얹어 "바로 저장 가능한 초안" 으로 만든다.
 *
 * <p>저장은 반드시 {@link ExpenseService} 를 거친다 — 체크카드는 잔액 이력이 연결 계좌로
 * 리다이렉트되고 예산 알림도 여기서 돈다. 저장소에 직접 넣으면 잔액이 어긋난다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SmsImportServiceImpl implements SmsImportService {

    /**
     * 결제수단 기본값 — 앱·웹 폼이 쓰는 코드와 같은 값이어야 한다.
     *
     * <p>여기에 자산 이름 같은 자유 문자열을 넣으면, 나중에 그 지출을 편집할 때
     * 결제수단 select 가 값을 못 알아본다(코드 목록에 없는 값이라 빈칸으로 떨어진다).
     */
    private static final String DEFAULT_PAYMENT_METHOD = "CARD";

    private final ExpenseService expenseService;
    private final ExpenseRepository expenseRepository;
    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final AssetRepository assetRepository;
    private final SmsCardMappingRepository cardMappingRepository;

    @Override
    public SmsImportServiceDto.ParseResult parse(String text, Long userRowId) {
        SmsParsed parsed = SmsParser.parse(text);
        if (!parsed.matched()) {
            return SmsImportServiceDto.ParseResult.noMatch();
        }

        List<Asset> assets = assetRepository.findByUser(userRowId);
        AssetResolution asset = resolveAsset(parsed, assets, userRowId);
        CategoryResolution category = inferCategory(userRowId, parsed.merchant());

        return new SmsImportServiceDto.ParseResult(
            true,
            parsed.confidence(),
            parsed.cancel(),
            parsed.amount(),
            parsed.merchant(),
            parsed.occurredAt(),
            parsed.installmentMonths(),
            parsed.cardHint(),
            parsed.issuer() == null ? null : parsed.issuer().displayName(),
            parsed.cardLast4(),
            asset.rowId(),
            asset.remembered(),
            asset.candidates(),
            category.rowId(),
            category.name(),
            parsed.originalAmount(),
            parsed.originalCurrency()
        );
    }

    @Override
    @Transactional
    public SmsImportServiceDto.CommitResult commit(SmsImportServiceDto.CommitCommand command) {
        SmsParsed parsed = SmsParser.parse(command.text());
        if (!parsed.matched()) {
            throw new InvalidValueException(DeskErrorCode.SMS_NOT_RECOGNIZED);
        }
        // 취소 문자는 환불 상계(refundOf)에 걸어야 맞는데 원거래를 특정할 방법이 없다.
        // 그대로 지출로 넣으면 결제와 취소가 둘 다 지출로 쌓여 두 배가 된다 — 1차는 막는다.
        if (parsed.cancel()) {
            throw new InvalidValueException(DeskErrorCode.SMS_CANCEL_NOT_SUPPORTED);
        }

        // 지출 생성은 ExpenseService 가 검증까지 맡는다(금액·카테고리 소유권/타입/leaf·자산 소유권).
        // 여기서 같은 검증을 또 하면 규칙이 두 곳으로 갈라진다.
        ExpenseServiceDto.ExpenseInfo created = expenseService.createExpense(
            new ExpenseServiceDto.CreateCommand(
                command.userRowId(),
                command.categoryRowId(),
                command.assetRowId(),
                ExpenseType.EXPENSE,
                command.amount(),
                command.description(),
                command.expenseDate(),
                command.merchant(),
                paymentMethodOf(command.paymentMethod()),
                command.installmentMonths(),
                null,
                command.originalAmount(),
                command.originalCurrency(),
                command.exchangeRate(),
                null,
                null));

        boolean remembered = false;
        if (command.rememberCard() && command.assetRowId() != null) {
            // 카드 힌트는 원문에서 서버가 도출한다 — 클라이언트가 정하게 두면
            // 남의 문자에서 온 키로 내 자산을 묶는 짓이 가능해진다.
            remembered = rememberCard(command.userRowId(), parsed.cardHint(), command.assetRowId());
        }

        log.info("결제 문자 지출 등록: expenseId={}, userRowId={}, remembered={}",
            created.rowId(), command.userRowId(), remembered);
        return new SmsImportServiceDto.CommitResult(created.rowId(), remembered);
    }

    @Override
    public List<SmsImportServiceDto.CardMappingInfo> getCardMappings(Long userRowId) {
        List<SmsCardMapping> mappings = cardMappingRepository.findAllActiveByUser(userRowId);
        if (mappings.isEmpty()) return List.of();

        // 자산명을 붙이려고 건별 조회하면 N+1 이다 — 한 번 읽어 맵으로 쓴다.
        Map<Long, String> assetNames = assetRepository.findByUser(userRowId).stream()
            .collect(Collectors.toMap(Asset::getRowId, Asset::getAssetName, (a, b) -> a));

        return mappings.stream()
            .map(m -> new SmsImportServiceDto.CardMappingInfo(
                m.getRowId(), m.getCardHint(), m.getAssetRowId(),
                assetNames.get(m.getAssetRowId())))
            .toList();
    }

    @Override
    @Transactional
    public void deleteCardMapping(Long rowId, Long userRowId) {
        SmsCardMapping mapping = cardMappingRepository.findActiveById(rowId)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.SMS_CARD_MAPPING_NOT_FOUND));
        if (!mapping.getUserRowId().equals(userRowId)) {
            throw new InvalidValueException(DeskErrorCode.EXPENSE_ACCESS_DENIED);
        }
        mapping.delete();
    }

    // ── 자산 매칭 ────────────────────────────────────────────

    /** 자산 해석 결과 — 확정된 자산과, 확정 못 했을 때의 후보. */
    private record AssetResolution(
        Long rowId,
        boolean remembered,
        List<SmsImportServiceDto.AssetCandidate> candidates
    ) {}

    /**
     * 문자의 카드 → 내 자산.
     *
     * <p>1순위는 <b>기억해 둔 매핑</b>이다. 사용자가 한 번 고른 답이라 추측보다 정확하다.
     * 매핑이 없으면 카드사 이름·끝자리로 후보를 좁히고, 후보가 딱 하나일 때만 미리 채운다 —
     * 둘 이상이면 고르게 해야 한다. 잘못 채운 자산은 엉뚱한 카드의 잔액을 깎는다.
     */
    private AssetResolution resolveAsset(SmsParsed parsed, List<Asset> assets, Long userRowId) {
        String cardHint = parsed.cardHint();
        Set<Long> aliveIds = assets.stream().map(Asset::getRowId).collect(Collectors.toSet());

        if (cardHint != null) {
            Optional<SmsCardMapping> mapping =
                cardMappingRepository.findByCardHintIncludingDeleted(userRowId, cardHint);
            if (mapping.isPresent()
                && mapping.get().getIsDeleted() == YNType.N
                // 매핑해 둔 자산이 그 사이 삭제됐을 수 있다 — 죽은 자산을 채우면 저장이 통째로 실패한다.
                && aliveIds.contains(mapping.get().getAssetRowId())) {
                return new AssetResolution(mapping.get().getAssetRowId(), true, List.of());
            }
        }

        List<SmsImportServiceDto.AssetCandidate> candidates = candidatesFor(parsed, assets);
        Long only = candidates.size() == 1 ? candidates.get(0).rowId() : null;
        return new AssetResolution(only, false, candidates);
    }

    /**
     * 자산 후보 — 카드 자산 중 카드사·끝자리가 맞는 것.
     *
     * <p>끝자리가 이름에 박힌 카드가 있으면 그것만 남긴다(가장 강한 단서).
     * 카드사만 맞는 게 여럿이면 전부 후보로 두고, 아무 단서도 안 맞으면
     * 카드 자산 전체를 후보로 준다 — 빈 목록보다 고를 거리가 있는 편이 낫다.
     */
    private List<SmsImportServiceDto.AssetCandidate> candidatesFor(SmsParsed parsed, List<Asset> assets) {
        List<Asset> cards = assets.stream()
            .filter(a -> a.getAssetType() == AssetType.CREDIT_CARD || a.getAssetType() == AssetType.CHECK_CARD)
            .toList();
        if (cards.isEmpty()) return List.of();

        if (parsed.cardLast4() != null) {
            List<Asset> byLast4 = cards.stream()
                .filter(a -> containsIgnoreCase(a.getAssetName(), parsed.cardLast4()))
                .toList();
            if (!byLast4.isEmpty()) return toCandidates(byLast4);
        }

        if (parsed.issuer() != null) {
            List<Asset> byIssuer = new ArrayList<>();
            for (Asset a : cards) {
                boolean hit = parsed.issuer().aliases().stream().anyMatch(alias ->
                    containsIgnoreCase(a.getInstitution(), alias) || containsIgnoreCase(a.getAssetName(), alias));
                if (hit) byIssuer.add(a);
            }
            if (!byIssuer.isEmpty()) return toCandidates(byIssuer);
        }

        return toCandidates(cards);
    }

    private static List<SmsImportServiceDto.AssetCandidate> toCandidates(List<Asset> assets) {
        return assets.stream()
            .map(a -> new SmsImportServiceDto.AssetCandidate(
                a.getRowId(), a.getAssetName(), a.getInstitution(), a.getAssetType()))
            .toList();
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        if (haystack == null || needle == null) return false;
        return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    // ── 카테고리 추론 ────────────────────────────────────────

    private record CategoryResolution(Long rowId, String name) {
        static CategoryResolution none() {
            return new CategoryResolution(null, null);
        }
    }

    /**
     * 카테고리 추론 — 내 지난 거래가 1순위, 키워드 사전이 2순위.
     *
     * <p>여기서 카테고리를 <b>만들지 않는다</b>. parse 는 미리보기라 저장이 없어야 하고,
     * commit 은 사용자가 확정한 categoryRowId 를 받으므로 생성이 필요 없다.
     * 못 찾으면 비워서 사용자가 고르게 한다.
     */
    private CategoryResolution inferCategory(Long userRowId, String merchant) {
        if (merchant == null || merchant.isBlank()) return CategoryResolution.none();

        List<ExpenseCategory> categories = expenseCategoryRepository.findAllByUser(userRowId);

        Optional<Long> recent =
            expenseRepository.findRecentCategoryRowIdByMerchant(userRowId, merchant, ExpenseType.EXPENSE);
        if (recent.isPresent()) {
            Long rowId = recent.get();
            // 그 사이 카테고리가 지워졌을 수 있다 — 죽은 rowId 를 채우면 저장이 실패한다.
            Optional<ExpenseCategory> alive = categories.stream()
                .filter(c -> c.getRowId().equals(rowId))
                .findFirst();
            if (alive.isPresent()) {
                return new CategoryResolution(rowId, alive.get().getCategoryName());
            }
        }

        List<String> hints = SmsMerchantHints.categoryNamesFor(merchant);
        if (hints.isEmpty()) return CategoryResolution.none();

        // 거래는 leaf 에만 달 수 있다 — 자식 있는 부모를 제안하면 저장 단계에서 거절된다.
        Set<Long> parentIds = new HashSet<>();
        categories.forEach(c -> {
            if (c.getParent() != null) parentIds.add(c.getParent().getRowId());
        });

        for (String hint : hints) {
            for (ExpenseCategory c : categories) {
                if (c.getExpenseType() == ExpenseType.EXPENSE
                    && !parentIds.contains(c.getRowId())
                    && c.getCategoryName().equalsIgnoreCase(hint)) {
                    return new CategoryResolution(c.getRowId(), c.getCategoryName());
                }
            }
        }
        return CategoryResolution.none();
    }

    // ── 카드 매핑 기억 ────────────────────────────────────────

    /**
     * (카드 힌트 → 자산) 기억. 이미 있으면 자산만 갈아끼운다.
     *
     * <p>삭제 행도 되살려 쓴다 — (user, cardHint) 유니크가 삭제 여부를 보지 않아
     * 새로 만들면 제약에 걸린다.
     */
    private boolean rememberCard(Long userRowId, String cardHint, Long assetRowId) {
        if (cardHint == null || cardHint.isBlank()) return false;

        Optional<SmsCardMapping> existing =
            cardMappingRepository.findByCardHintIncludingDeleted(userRowId, cardHint);
        if (existing.isPresent()) {
            existing.get().relink(assetRowId);
            return true;
        }
        cardMappingRepository.save(SmsCardMapping.create(userRowId, cardHint, assetRowId));
        return true;
    }

    /** 결제수단 — 클라이언트가 고른 코드를 그대로 쓰고, 비면 카드로 본다. */
    private String paymentMethodOf(String paymentMethod) {
        if (paymentMethod == null || paymentMethod.isBlank()) return DEFAULT_PAYMENT_METHOD;
        return paymentMethod.trim();
    }
}
