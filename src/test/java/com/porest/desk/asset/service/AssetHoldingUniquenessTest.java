package com.porest.desk.asset.service;

import com.porest.core.exception.InvalidValueException;
import com.porest.core.time.ServiceClock;
import com.porest.core.time.UserClock;
import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.domain.AssetHolding;
import com.porest.desk.asset.repository.AssetHoldingRepository;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.asset.repository.AssetTransferRepository;
import com.porest.desk.asset.service.dto.AssetServiceDto;
import com.porest.desk.asset.type.AssetType;
import com.porest.desk.asset.type.HoldingType;
import com.porest.desk.card.repository.CardCatalogRepository;
import com.porest.desk.securities.service.SecuritiesCredentialService;
import com.porest.desk.securities.service.SecuritiesPriceProviders;
import com.porest.desk.stock.service.StockMasterResolver;
import com.porest.desk.subscription.service.SubscriptionEntitlementService;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 한 투자 자산에 같은 종목이 두 줄로 생기지 않게 하는 자리 — 저장 전에 다듬고 접는다(QA #78).
 *
 * <p>정체성은 코드가 이미 정한 {@code AssetHolding.holdingKey()}(연동이면 종목코드, 아니면 항목명)다.
 * 여기서 고정하는 것은 셋이다.
 * <ol>
 *   <li><b>정규화</b> — 종목코드는 대문자·앞뒤공백을 뗀 채로 저장하고, 항목명은 trim 만 한다.
 *       비교는 둘 다 대소문자를 안 가린다(DB 콜레이션 {@code utf8mb4_unicode_ci} 와 같은 판정).</li>
 *   <li><b>접기</b> — 편집 배열에 같은 종목이 두 줄 담겨 와도 한 행이 된다.</li>
 *   <li><b>이어 붙이기</b> — rowId 를 안 실어 보내도 같은 종목이면 그 행을 제자리에서 고친다.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class AssetHoldingUniquenessTest {

    @Mock private AssetRepository assetRepository;
    @Mock private AssetHoldingRepository assetHoldingRepository;
    @Mock private AssetTransferRepository assetTransferRepository;
    @Mock private UserRepository userRepository;
    @Mock private CardCatalogRepository cardCatalogRepository;
    @Mock private AssetBalanceHistoryService balanceHistoryService;
    @Mock private SubscriptionEntitlementService entitlementService;
    @Mock private SecuritiesCredentialService securitiesCredentialService;
    @Mock private SecuritiesPriceProviders priceProviders;
    @Mock private StockMasterResolver stockMasterResolver;
    @Spy private UserClock userClock = new UserClock(rowId -> null, new ServiceClock("Asia/Seoul"));

    @InjectMocks private AssetServiceImpl sut;

    private static final long USER_ID = 1L;
    private static final long ASSET_ID = 11L;

    /** 저장소 대용 — 편집이 만든 행과 고친 행을 그대로 본다. */
    private final List<AssetHolding> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        lenient().when(balanceHistoryService.balanceAt(any(), any()))
            .thenReturn(AssetBalanceHistoryService.Split.ZERO);
        lenient().when(balanceHistoryService.balancesAt(anyCollection(), any()))
            .thenReturn(java.util.Map.of());
        lenient().when(assetHoldingRepository.findActiveByAsset(anyLong()))
            .thenAnswer(inv -> stored.stream().filter(h -> h.getIsDeleted() == YNType.N).toList());
        lenient().when(assetHoldingRepository.save(any(AssetHolding.class))).thenAnswer(inv -> {
            AssetHolding h = inv.getArgument(0);
            stored.add(h);
            return h;
        });
    }

    // ── 픽스처 ────────────────────────────────────────────────────────────

    private User user() {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", USER_ID);
        return u;
    }

    private Asset investment() {
        Asset a = Asset.createAsset(user(), "토스증권", AssetType.INVESTMENT, 0L, "KRW",
            null, null, null, null, 0, YNType.Y, null, null, null, null);
        ReflectionTestUtils.setField(a, "rowId", ASSET_ID);
        return a;
    }

    private AssetServiceDto.CreateAssetCommand createCmd(List<AssetServiceDto.HoldingCommand> holdings) {
        return new AssetServiceDto.CreateAssetCommand(
            USER_ID, "토스증권", AssetType.INVESTMENT, 0L, null, "KRW",
            null, null, null, null, 0, YNType.Y, null, null, null, null, holdings);
    }

    private AssetServiceDto.UpdateAssetCommand updateCmd(List<AssetServiceDto.HoldingCommand> holdings) {
        return new AssetServiceDto.UpdateAssetCommand(
            null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, holdings);
    }

    private static AssetServiceDto.HoldingCommand linked(Long rowId, String symbol, String qty) {
        return new AssetServiceDto.HoldingCommand(rowId, HoldingType.STOCK, true, null, symbol,
            qty == null ? null : new BigDecimal(qty), null, null, null);
    }

    private static AssetServiceDto.HoldingCommand manual(Long rowId, String name, Long value) {
        return new AssetServiceDto.HoldingCommand(rowId, HoldingType.STOCK, false, null, null,
            null, name, value, null);
    }

    /** 이미 저장돼 있는 활성 보유를 만들어 둔다. */
    private AssetHolding existing(long rowId, boolean isLinked, String symbol, String name,
                                  String qty, long value, long cost) {
        AssetHolding h = AssetHolding.create(investment(), HoldingType.STOCK,
            isLinked ? YNType.Y : YNType.N, null, symbol,
            qty == null ? null : new BigDecimal(qty), name, value, cost, 0);
        ReflectionTestUtils.setField(h, "rowId", rowId);
        stored.add(h);
        return h;
    }

    private void stubCreate() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
    }

    private void stubUpdate() {
        given(assetRepository.findById(ASSET_ID)).willReturn(Optional.of(investment()));
    }

    private List<AssetHolding> savedRows() {
        ArgumentCaptor<AssetHolding> captor = ArgumentCaptor.forClass(AssetHolding.class);
        verify(assetHoldingRepository, org.mockito.Mockito.atLeast(0)).save(captor.capture());
        return captor.getAllValues();
    }

    // ── 정규화 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("생성 — 종목코드는 대문자·앞뒤공백을 뗀 채로 저장된다")
    void createNormalizesSymbol() {
        stubCreate();

        sut.createAsset(createCmd(List.of(linked(null, "  aapl ", "10"))));

        assertThat(savedRows()).singleElement()
            .satisfies(h -> assertThat(h.getSymbol()).isEqualTo("AAPL"));
    }

    @Test
    @DisplayName("생성 — 항목명은 앞뒤공백만 뗀다(대소문자는 사용자가 친 그대로)")
    void createTrimsHoldingName() {
        stubCreate();

        sut.createAsset(createCmd(List.of(manual(null, "  Gold Bar ", 1_000_000L))));

        assertThat(savedRows()).singleElement()
            .satisfies(h -> assertThat(h.getHoldingName()).isEqualTo("Gold Bar"));
    }

    @Test
    @DisplayName("생성 — 연동인데 종목코드가 공백뿐이면 400")
    void createRejectsBlankSymbol() {
        stubCreate();

        assertThatThrownBy(() -> sut.createAsset(createCmd(List.of(linked(null, "   ", "10")))))
            .isInstanceOf(InvalidValueException.class);
        verify(assetHoldingRepository, never()).save(any());
    }

    // ── 접기 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("생성 — 같은 종목을 두 줄로 보내도 한 행만 만든다(뒤엣값이 이긴다)")
    void createFoldsDuplicateSymbols() {
        stubCreate();

        sut.createAsset(createCmd(List.of(
            linked(null, "AAPL", "10"),
            manual(null, "금괴", 1_000_000L),
            linked(null, "aapl", "25"))));

        List<AssetHolding> saved = savedRows();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getSymbol()).isEqualTo("AAPL");
        assertThat(saved.get(0).getQuantity()).isEqualByComparingTo("25");
        // 접힌 줄은 자리를 앞엣것에서 지킨다 — 뒤로 밀리면 목록 순서가 요청과 어긋난다.
        assertThat(saved.get(0).getSortOrder()).isEqualTo(0);
        assertThat(saved.get(1).getHoldingName()).isEqualTo("금괴");
        assertThat(saved.get(1).getSortOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("생성 — 접힌 미연동 보유의 평가액은 한 번만 더해진다")
    void createDoesNotDoubleCountFoldedValuation() {
        stubCreate();

        sut.createAsset(createCmd(List.of(
            manual(null, "금괴", 1_000_000L),
            manual(null, "금괴", 1_000_000L))));

        // 접기 전에 평가액을 세면 200만이 되어 순자산이 100만 부풀려진다.
        ArgumentCaptor<Long> valuation = ArgumentCaptor.forClass(Long.class);
        verify(balanceHistoryService).recordValuation(any(), valuation.capture(), any());
        assertThat(valuation.getValue()).isEqualTo(1_000_000L);
    }

    @Test
    @DisplayName("편집 — 같은 종목을 두 줄로 보내도 한 행만 남는다")
    void updateFoldsDuplicateSymbols() {
        stubUpdate();

        sut.updateAsset(ASSET_ID, USER_ID, updateCmd(List.of(
            linked(null, "AAPL", "10"),
            linked(null, "aapl", "25"))));

        assertThat(savedRows()).singleElement()
            .satisfies(h -> {
                assertThat(h.getSymbol()).isEqualTo("AAPL");
                assertThat(h.getQuantity()).isEqualByComparingTo("25");
            });
    }

    @Test
    @DisplayName("편집 — 접기는 앞엣줄의 rowId 를 살린다(거래 연결이 안 끊긴다)")
    void foldKeepsExistingRowId() {
        stubUpdate();
        AssetHolding held = existing(7L, true, "AAPL", null, "10", 1_500_000L, 1_000_000L);

        sut.updateAsset(ASSET_ID, USER_ID, updateCmd(List.of(
            linked(7L, "AAPL", "10"),
            linked(null, "aapl", "30"))));

        verify(assetHoldingRepository, never()).save(any());
        assertThat(held.getQuantity()).isEqualByComparingTo("30");
        assertThat(held.getIsDeleted()).isEqualTo(YNType.N);
    }

    // ── 이어 붙이기 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("편집 — rowId 를 안 보내도 같은 종목이면 그 행을 제자리에서 고친다")
    void updateReusesRowMatchedByKey() {
        stubUpdate();
        AssetHolding held = existing(7L, true, "AAPL", null, "10", 1_500_000L, 1_000_000L);

        sut.updateAsset(ASSET_ID, USER_ID, updateCmd(List.of(linked(null, "aapl", "12"))));

        // 새 행을 만들면 row_id 가 바뀌어 거래(asset_trade)가 가리키던 보유가 끊기고,
        // 옛 행이 지워지기 전에 새 행이 들어가면 DB 유일성에 걸려 저장 전체가 실패한다.
        verify(assetHoldingRepository, never()).save(any());
        assertThat(held.getQuantity()).isEqualByComparingTo("12");
        assertThat(held.getIsDeleted()).isEqualTo(YNType.N);
        assertThat(held.getTotalCost()).isEqualTo(1_000_000L);
    }

    @Test
    @DisplayName("편집 — 이름을 진짜로 바꾼 줄은 새 행이 되고 옛 행은 지워진다")
    void updateStillReplacesRenamedHolding() {
        stubUpdate();
        AssetHolding held = existing(7L, false, null, "금괴", null, 1_000_000L, 900_000L);

        sut.updateAsset(ASSET_ID, USER_ID, updateCmd(List.of(manual(null, "은괴", 500_000L))));

        assertThat(savedRows()).singleElement()
            .satisfies(h -> assertThat(h.getHoldingName()).isEqualTo("은괴"));
        assertThat(held.getIsDeleted()).isEqualTo(YNType.Y);
    }

    @Test
    @DisplayName("편집 — 목록에서 빠진 보유는 그대로 삭제된다(이어 붙이기가 삭제를 막지 않는다)")
    void updateStillDeletesRemovedHolding() {
        stubUpdate();
        AssetHolding kept = existing(7L, true, "AAPL", null, "10", 1_500_000L, 1_000_000L);
        AssetHolding gone = existing(8L, true, "TSLA", null, "5", 900_000L, 800_000L);

        sut.updateAsset(ASSET_ID, USER_ID, updateCmd(List.of(linked(null, "AAPL", "10"))));

        assertThat(kept.getIsDeleted()).isEqualTo(YNType.N);
        assertThat(gone.getIsDeleted()).isEqualTo(YNType.Y);
    }

    @Test
    @DisplayName("편집 — 골드바(수동)와 금광주(연동)는 이름이 같아도 서로 다른 보유다")
    void manualAndLinkedWithSameNameStayApart() {
        stubUpdate();

        sut.updateAsset(ASSET_ID, USER_ID, updateCmd(List.of(
            manual(null, "GOLD", 1_000_000L),
            linked(null, "GOLD", "10"))));

        // 연동 여부까지 봐야 한다 — holdingKey() 는 연동이면 종목코드, 아니면 항목명이라
        // 문자열만 비교하면 서로 다른 자산이 한 줄로 접힌다.
        List<AssetHolding> saved = savedRows();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getHoldingName()).isEqualTo("GOLD");
        assertThat(saved.get(1).getSymbol()).isEqualTo("GOLD");
    }
}
