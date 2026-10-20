package io.groo.damda.asset.repository;

import io.groo.damda.asset.domain.AssetTransfer;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AssetTransferRepository {
    Optional<AssetTransfer> findById(Long rowId);
    List<AssetTransfer> findByUser(Long userRowId, LocalDate startDate, LocalDate endDate);
    AssetTransfer save(AssetTransfer transfer);
    void delete(AssetTransfer transfer);
}
