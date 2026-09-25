package com.porest.desk.asset.repository;

import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.type.AssetType;

import java.util.List;
import java.util.Optional;

public interface AssetRepository {
    Optional<Asset> findById(Long rowId);
    List<Asset> findByUser(Long userRowId);
    Asset save(Asset asset);
    void delete(Asset asset);

    /** 삭제되지 않은 특정 타입의 전체 자산 조회 (스케줄러용 — 사용자 무관). */
    List<Asset> findAllByType(AssetType assetType);

    /** 자산을 가진 사용자 아이디 — 기동 시 잔액 재산정 대상. 자산이 없으면 계산할 것도 없다. */
    List<Long> findUserRowIdsWithAssets();

    /**
     * 이 사용자가 지운 자산인가 — 지우기 전에 연 폼이 그 계좌를 결제계좌로 되실어 온 요청을 가른다.
     * {@link #findById} 는 지운 자산을 못 찾으므로(404) 따로 묻는다.
     */
    boolean existsDeletedOwnedBy(Long rowId, Long userRowId);
}
