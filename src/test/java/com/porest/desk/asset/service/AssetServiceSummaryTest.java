package com.porest.desk.asset.service;

import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.asset.repository.AssetTransferRepository;
import com.porest.desk.asset.service.AssetBalanceHistoryService.BalanceResolver;
import com.porest.desk.asset.service.dto.AssetServiceDto;
import com.porest.desk.asset.type.AssetType;
import com.porest.desk.card.repository.CardCatalogRepository;
import com.porest.desk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import com.porest.core.time.ServiceClock;
import com.porest.core.time.UserClock;

/**
 * 자산 순자산 집계 로직 회귀 방지 테스트 — 자산/부채(CREDIT_CARD·LOAN) 분류 합산과 netWorth 계산.
 */
@ExtendWith(MockitoExtension.class)
class AssetServiceSummaryTest {

    @Mock private AssetRepository assetRepository;
    @Mock private AssetTransferRepository assetTransferRepository;
    @Mock private UserRepository userRepository;
    @Mock private CardCatalogRepository cardCatalogRepository;
    @Mock private AssetBalanceHistoryService balanceHistoryService;
    // 날짜 판정용 — mock 이면 null 이 흘러 NPE. 실물을 주입하되 사용자 조회는 비어
    // 서비스 기준(Asia/Seoul)으로 폴백한다.
    @Spy private UserClock userClock = new UserClock(rowId -> null, new ServiceClock("Asia/Seoul"));

    @InjectMocks private AssetServiceImpl sut;

    private static final long USER_ID = 1L;

    private Asset asset(long rowId, AssetType type) {
        Asset a = Asset.createAsset(null, "자산" + rowId, type, 0L, "KRW",
                null, null, null, 0, YNType.Y, null, null, null, null);
        ReflectionTestUtils.setField(a, "rowId", rowId);
        return a;
    }

    @Test
    @DisplayName("getAssetSummary — 자산/부채를 분류 합산하고 netWorth = 자산 - 부채")
    void netWorthClassifiesAssetsAndDebt() {
        Asset bank = asset(1L, AssetType.BANK_ACCOUNT);
        Asset creditCard = asset(2L, AssetType.CREDIT_CARD);
        Asset loan = asset(3L, AssetType.LOAN);
        given(assetRepository.findByUser(USER_ID)).willReturn(List.of(bank, creditCard, loan));

        BalanceResolver resolver = mock(BalanceResolver.class);
        given(balanceHistoryService.resolverFor(anyCollection())).willReturn(resolver);
        given(resolver.balanceAt(eq(1L), any(LocalDateTime.class))).willReturn(1_000_000L);
        given(resolver.balanceAt(eq(2L), any(LocalDateTime.class))).willReturn(-300_000L);
        given(resolver.balanceAt(eq(3L), any(LocalDateTime.class))).willReturn(-500_000L);

        AssetServiceDto.AssetSummary summary = sut.getAssetSummary(USER_ID, null, null);

        assertThat(summary.totalAssets()).isEqualTo(1_000_000L);     // 은행만 자산
        assertThat(summary.totalDebt()).isEqualTo(800_000L);         // |카드| + |대출|
        assertThat(summary.netWorth()).isEqualTo(200_000L);          // 1,000,000 - 800,000
        assertThat(summary.totalBalance()).isEqualTo(200_000L);      // 모든 잔액 합(부채 음수 포함)
    }
}
