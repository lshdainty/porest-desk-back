package com.porest.desk.asset.service;

import com.porest.core.time.ServiceClock;
import com.porest.core.time.UserClock;
import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.repository.AssetHoldingRepository;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.asset.repository.AssetTransferRepository;
import com.porest.desk.asset.service.dto.AssetServiceDto;
import com.porest.desk.asset.type.AssetType;
import com.porest.desk.card.repository.CardCatalogRepository;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * API 로 들어온 잔액의 부호를 <b>종류가</b> 정한다(QA 2026-09-03 #17 #19).
 *
 * <p>부채군은 음수, 마이너스 통장({@code isOverdraft=true})도 음수, 나머지 자산군은 양수다.
 * 이 규칙이 종전엔 잔액 이력({@code AssetBalanceHistoryService})에만 있고 그것도 부채군만
 * 봐서, 자산군에 음수를 보내면 그대로 저장됐고 {@code asset.initial_balance} 에는 사용자가
 * 보낸 원본 부호가 남았다.
 *
 * <p>{@code isOverdraft} 를 모르는 옛 클라이언트에게는 <b>보낸 부호를 그대로 존중</b>한다 —
 * 이 폴백이 없으면 옛 앱이 마이너스 통장을 열어 저장만 해도 −50,000 이 +50,000 으로 뒤집혀
 * 100,000 이 소리 없이 움직인다. 그 경우 강제 업데이트(min_build) 대상이 된다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("잔액 입력 부호 — 종류가 정한다")
class AssetBalanceInputSignTest {

    @Mock private AssetRepository assetRepository;
    @Mock private AssetHoldingRepository assetHoldingRepository;
    @Mock private AssetTransferRepository assetTransferRepository;
    @Mock private UserRepository userRepository;
    @Mock private CardCatalogRepository cardCatalogRepository;
    @Mock private com.porest.desk.card.repository.CardBillingRepository cardBillingRepository;
    @Mock private com.porest.desk.expense.repository.ExpenseRepository expenseRepository;
    @Mock private AssetBalanceHistoryService balanceHistoryService;
    @Mock private com.porest.desk.subscription.service.SubscriptionEntitlementService entitlementService;
    @Mock private com.porest.desk.securities.service.SecuritiesCredentialService securitiesCredentialService;
    @Mock private com.porest.desk.securities.service.SecuritiesPriceProviders priceProviders;
    @Mock private com.porest.desk.stock.service.StockMasterResolver stockMasterResolver;
    // 날짜 판정용 — mock 이면 null 이 흘러 NPE. 사용자 조회는 비어 Asia/Seoul 로 폴백한다.
    @Spy private UserClock userClock = new UserClock(rowId -> null, new ServiceClock("Asia/Seoul"));

    @InjectMocks private AssetServiceImpl sut;

    private static final long USER_ID = 1L;
    private static final long ASSET_ID = 9L;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(user, "rowId", USER_ID);
        lenient().when(balanceHistoryService.balancesAt(anyCollection(), any()))
            .thenReturn(java.util.Map.of());
    }

    private AssetServiceDto.CreateAssetCommand createCommand(AssetType type, Long balance,
                                                             Boolean isOverdraft) {
        return new AssetServiceDto.CreateAssetCommand(
            USER_ID, "테스트자산", type, balance, isOverdraft, "KRW",
            null, null, null, null, 0,
            YNType.Y, null, null, null, null, null);
    }

    private AssetServiceDto.UpdateAssetCommand updateCommand(Long balance, Boolean isOverdraft) {
        return new AssetServiceDto.UpdateAssetCommand(
            null, null, balance, isOverdraft, null, null,
            null, null, null, null, null, null, null, null, null);
    }

    /** 생성 경로 — 저장된 자산(initial_balance) 을 잡아 부호를 본다. */
    private Asset created(AssetType type, Long balance, Boolean isOverdraft) {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(balanceHistoryService.balanceAt(any(), any()))
            .willReturn(AssetBalanceHistoryService.Split.ZERO);

        sut.createAsset(createCommand(type, balance, isOverdraft));

        ArgumentCaptor<Asset> c = ArgumentCaptor.forClass(Asset.class);
        verify(assetRepository).save(c.capture());
        return c.getValue();
    }

    /** 수정 경로 — 현재 예수금이 {@code currentCash} 인 자산을 만든다. */
    private Asset existing(AssetType type, long currentCash) {
        Asset a = Asset.createAsset(user, "테스트자산", type, currentCash, "KRW",
            null, null, null, null, 0, YNType.Y, null, null, null, null);
        ReflectionTestUtils.setField(a, "rowId", ASSET_ID);
        given(assetRepository.findById(ASSET_ID)).willReturn(Optional.of(a));
        given(balanceHistoryService.balanceAt(any(), any()))
            .willReturn(new AssetBalanceHistoryService.Split(currentCash, 0L));
        return a;
    }

    @Nested
    @DisplayName("생성 — initial_balance 에도 정규화된 부호가 들어간다")
    class Create {

        @Test
        @DisplayName("대출을 양수 3,000,000 으로 넣어도 −3,000,000 으로 저장")
        void loanNormalized() {
            assertThat(created(AssetType.LOAN, 3_000_000L, null).getInitialBalance())
                .isEqualTo(-3_000_000L);
        }

        @Test
        @DisplayName("마이너스 통장 — 사용액 50,000 을 양수로 받아 −50,000 으로 저장")
        void overdraftNormalized() {
            Asset saved = created(AssetType.BANK_ACCOUNT, 50_000L, true);
            assertThat(saved.getAssetType()).isEqualTo(AssetType.BANK_ACCOUNT); // 새 타입을 만들지 않는다
            assertThat(saved.getInitialBalance()).isEqualTo(-50_000L);
        }

        @Test
        @DisplayName("일반 입출금 — 음수를 보내도 절대값으로 저장(부호는 사용자가 못 정한다)")
        void assetTypeForcedPositive() {
            assertThat(created(AssetType.BANK_ACCOUNT, -50_000L, false).getInitialBalance())
                .isEqualTo(50_000L);
        }

        @Test
        @DisplayName("isOverdraft 를 안 보내는 옛 클라이언트는 보낸 부호 그대로 — 종전과 같은 동작")
        void legacyClientUnchanged() {
            assertThat(created(AssetType.BANK_ACCOUNT, -50_000L, null).getInitialBalance())
                .isEqualTo(-50_000L);
        }
    }

    @Nested
    @DisplayName("수정 — 부호를 씌운 뒤에 비교한다")
    class Update {

        @Test
        @DisplayName("마이너스 통장 사용액 30,000 → −30,000 앵커")
        void overdraftNormalized() {
            Asset asset = existing(AssetType.BANK_ACCOUNT, -50_000L);

            sut.updateAsset(ASSET_ID, USER_ID, updateCommand(30_000L, true));

            verify(balanceHistoryService).recordManual(eq(asset), eq(-30_000L), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("옛 클라이언트가 −50,000 을 그대로 돌려보내면 앵커를 안 찍는다(값 무변경)")
        void legacyClientNoOpDoesNotFlipSign() {
            existing(AssetType.BANK_ACCOUNT, -50_000L);

            sut.updateAsset(ASSET_ID, USER_ID, updateCommand(-50_000L, null));

            // 폴백이 없으면 여기서 +50,000 앵커가 찍혀 잔액이 100,000 움직인다.
            verify(balanceHistoryService, never()).recordManual(any(), org.mockito.ArgumentMatchers.anyLong(), any());
        }

        @Test
        @DisplayName("마이너스 통장에 같은 사용액을 다시 저장해도 앵커가 안 쌓인다 — 정규화 후 비교")
        void normalizedComparisonAvoidsDuplicateAnchor() {
            existing(AssetType.BANK_ACCOUNT, -50_000L);

            // 화면은 '사용 중인 금액' 을 양수 50,000 으로 보낸다 → 정규화하면 −50,000 = 현재값.
            sut.updateAsset(ASSET_ID, USER_ID, updateCommand(50_000L, true));

            verify(balanceHistoryService, never()).recordManual(any(), org.mockito.ArgumentMatchers.anyLong(), any());
        }

        @Test
        @DisplayName("대출 상환액 4,500,000 을 양수로 보내도 −4,500,000 앵커")
        void loanNormalized() {
            Asset asset = existing(AssetType.LOAN, -5_000_000L);

            sut.updateAsset(ASSET_ID, USER_ID, updateCommand(4_500_000L, null));

            verify(balanceHistoryService).recordManual(eq(asset), eq(-4_500_000L), any(LocalDateTime.class));
        }
    }
}
