package com.porest.desk.asset.service;

import com.porest.desk.asset.repository.AssetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 기동할 때 잔액 캐시를 이력에 맞춰 되맞춘다.
 *
 * <p>잔액은 asset_balance_history 가 진실이고 asset 의 컬럼은 파생 캐시다. 정상 운영 중엔
 * 거래·이체마다 알아서 갱신되지만, 캐시 컬럼이 새로 생기거나(신규 컬럼은 DEFAULT 0)
 * 마이그레이션이 이력을 손대면 어긋난 채로 남는다.
 *
 * <p>그때마다 사람이 재산정 API 를 기억해서 호출해야 한다면 언젠가 빠뜨린다.
 * 배포하면 알아서 맞도록 기동 훅에 건다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssetBalanceStartupReconcilerTest {

    @Mock private AssetRepository assetRepository;
    @Mock private AssetBalanceHistoryService balanceHistoryService;

    private AssetBalanceStartupReconciler reconciler(boolean enabled) {
        var r = new AssetBalanceStartupReconciler(assetRepository, balanceHistoryService);
        ReflectionTestUtils.setField(r, "enabled", enabled);
        return r;
    }

    @Test
    @DisplayName("기동하면 사용자마다 잔액을 다시 계산한다")
    void recomputesEveryUser() {
        given(assetRepository.findUserRowIdsWithAssets()).willReturn(List.of(1L, 2L));

        reconciler(true).reconcile();

        verify(balanceHistoryService).recomputeAllForUser(1L);
        verify(balanceHistoryService).recomputeAllForUser(2L);
    }

    @Test
    @DisplayName("한 사용자가 실패해도 나머지는 계속 처리한다")
    void oneFailureDoesNotStopTheRest() {
        given(assetRepository.findUserRowIdsWithAssets()).willReturn(List.of(1L, 2L));
        willThrow(new RuntimeException("boom")).given(balanceHistoryService).recomputeAllForUser(1L);

        reconciler(true).reconcile();

        verify(balanceHistoryService).recomputeAllForUser(2L);
    }

    @Test
    @DisplayName("꺼두면 아무것도 하지 않는다 — 대량 데이터에서 기동을 늦추고 싶지 않을 때")
    void disabledDoesNothing() {
        reconciler(false).reconcile();

        verify(assetRepository, never()).findUserRowIdsWithAssets();
    }
}
