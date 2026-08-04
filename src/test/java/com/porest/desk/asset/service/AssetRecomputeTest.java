package com.porest.desk.asset.service;

import com.porest.core.time.UserClock;
import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.domain.AssetBalanceHistory;
import com.porest.desk.asset.repository.AssetBalanceHistoryRepository;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.asset.type.AssetType;
import com.porest.desk.asset.type.BalanceChannel;
import com.porest.desk.asset.type.BalanceSourceType;
import com.porest.desk.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * 잔액 재산정이 채널 분리(예수금/평가금액)를 지키는지.
 *
 * <p>투자 자산의 잔액은 두 칸으로 나뉜다 — 예수금(매수 대기 자금)과 보유 종목 평가금액.
 * 재산정이 이 구분을 뭉개면 총액이 맞아도 화면 숫자가 틀린다. 웹 자산 목록은 연동 종목을
 * 실시간 시세로 치환하면서 {@code balance = cashBalance + 라이브 평가액} 으로 다시 조립하는데,
 * 평가금액이 예수금 칸에 들어가 있으면 그만큼 한 번 더 더해진다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssetRecomputeTest {

    @Mock private AssetBalanceHistoryRepository repository;
    @Mock private UserClock userClock;
    @Mock private AssetRepository assetRepository;
    @InjectMocks private AssetBalanceHistoryService sut;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 31, 23, 59);

    private final List<AssetBalanceHistory> store = new ArrayList<>();
    private User user;

    @BeforeEach
    void setUp() {
        user = User.createUser(1L, "tester", "테스터", "tester@porest.cloud");
        ReflectionTestUtils.setField(user, "rowId", 1L);
        given(userClock.nowIn(any())).willReturn(NOW);
        given(repository.findActiveByAssetIds(any(), any())).willAnswer(inv -> {
            List<Long> ids = inv.getArgument(0);
            return store.stream()
                .filter(h -> h.getIsDeleted() == YNType.N)
                .filter(h -> h.getAsset() != null && ids.contains(h.getAsset().getRowId()))
                .sorted(Comparator.comparing(AssetBalanceHistory::getEffectiveAt)
                    .thenComparing(store::indexOf))
                .toList();
        });
    }

    private Asset asset(long rowId, AssetType type) {
        Asset a = Asset.createAsset(user, "자산" + rowId, type, 0L, "KRW",
            null, null, null, null, 0, YNType.Y, null, null, null, null);
        ReflectionTestUtils.setField(a, "rowId", rowId);
        return a;
    }

    private void history(Asset a, BalanceSourceType type, BalanceChannel channel, long amount, int day) {
        store.add(AssetBalanceHistory.of(user, a, type, channel, 1L, amount,
            LocalDateTime.of(2026, 8, day, 12, 0)));
    }

    /** 예수금 300,000 + 보유 평가 700,000 인 증권계좌. */
    private Asset brokerageAccount() {
        Asset invest = asset(30L, AssetType.INVESTMENT);
        history(invest, BalanceSourceType.INIT, BalanceChannel.CASH, 300_000L, 1);
        history(invest, BalanceSourceType.VALUATION, BalanceChannel.HOLDING, 700_000L, 2);
        return invest;
    }

    @Test
    @DisplayName("벌크 재산정 — 예수금과 평가금액이 각자 칸에 남는다 (합쳐서 예수금으로 몰면 안 됨)")
    void bulkRecomputeKeepsChannelSplit() {
        Asset invest = brokerageAccount();
        given(assetRepository.findById(30L)).willReturn(Optional.of(invest));

        sut.recomputeAssets(List.of(30L));

        assertThat(invest.getBalance()).isEqualTo(1_000_000L);
        assertThat(invest.getCashBalance()).isEqualTo(300_000L);
        assertThat(invest.getHoldingBalance()).isEqualTo(700_000L);
    }

    @Test
    @DisplayName("사용자 전체 재산정 — 자산마다 이력에서 다시 계산해 캐시를 채운다")
    void recomputeAllForUserFillsCaches() {
        Asset invest = brokerageAccount();
        Asset account = asset(10L, AssetType.BANK_ACCOUNT);
        history(account, BalanceSourceType.INIT, BalanceChannel.CASH, 1_000_000L, 1);
        history(account, BalanceSourceType.EXPENSE, BalanceChannel.CASH, -12_000L, 3);
        // 마이그레이션 직후 상태 — 컬럼이 DEFAULT 0 으로 생겨 캐시가 비어 있다.
        given(assetRepository.findByUser(1L)).willReturn(List.of(invest, account));

        int changed = sut.recomputeAllForUser(1L);

        assertThat(changed).isEqualTo(2);
        assertThat(invest.getCashBalance()).isEqualTo(300_000L);
        assertThat(invest.getHoldingBalance()).isEqualTo(700_000L);
        assertThat(account.getBalance()).isEqualTo(988_000L);
        assertThat(account.getCashBalance()).isEqualTo(988_000L);
        assertThat(account.getHoldingBalance()).isZero();
    }

    @Test
    @DisplayName("사용자 전체 재산정 — 자산이 없으면 아무것도 안 한다")
    void recomputeAllForUserWithNoAssets() {
        given(assetRepository.findByUser(1L)).willReturn(List.of());

        assertThat(sut.recomputeAllForUser(1L)).isZero();
    }
}
