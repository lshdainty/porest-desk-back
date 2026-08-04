package com.porest.desk.asset.service;

import com.porest.desk.asset.repository.AssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 기동할 때 자산 잔액 캐시를 이력에 맞춰 되맞춘다.
 *
 * <p>잔액은 {@code asset_balance_history} 가 진실이고 {@code asset} 의 컬럼은 파생 캐시다.
 * 정상 운영 중엔 거래·이체마다 알아서 갱신되므로 여기서 할 일이 없다. 문제는 다른 데서 온다 —
 * 캐시 컬럼이 새로 생기거나(신규 컬럼은 {@code DEFAULT 0} 으로 만들어진다) 마이그레이션이
 * 이력을 직접 손대면, 배포 직후 캐시가 어긋난 채로 남는다.
 *
 * <p>그때마다 사람이 재산정을 기억해서 호출해야 한다면 언젠가 빠뜨린다. 배포하면 알아서
 * 맞도록 기동 훅에 건다. 이력에서 다시 계산하는 것뿐이라 몇 번 돌아도 결과가 같다.
 *
 * <p>사용자 단위로 끊어 처리한다 — 자산 전체를 한 번에 올리면 이력까지 통째로 메모리에 든다.
 * 한 사용자가 실패해도 나머지는 계속 간다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AssetBalanceStartupReconciler {

    private final AssetRepository assetRepository;
    private final AssetBalanceHistoryService balanceHistoryService;

    /** 데이터가 아주 많아져 기동이 늦어지면 끌 수 있게 둔다. */
    @Value("${app.asset.recompute-on-startup:true}")
    private boolean enabled;

    @EventListener(ApplicationReadyEvent.class)
    public void reconcile() {
        if (!enabled) {
            log.info("자산 잔액 기동 재산정 건너뜀 (app.asset.recompute-on-startup=false)");
            return;
        }
        List<Long> userRowIds = assetRepository.findUserRowIdsWithAssets();
        int done = 0, failed = 0, assets = 0;
        for (Long userRowId : userRowIds) {
            // 한 명의 실패가 전체를 멈추지 않게 — 기동을 막을 이유가 없다.
            try {
                assets += balanceHistoryService.recomputeAllForUser(userRowId);
                done++;
            } catch (Exception e) {
                failed++;
                log.warn("자산 잔액 재산정 실패 - userRowId={}: {}", userRowId, e.getMessage());
            }
        }
        log.info("자산 잔액 기동 재산정 완료: 사용자={}명(실패 {}), 자산={}개", done, failed, assets);
    }
}
