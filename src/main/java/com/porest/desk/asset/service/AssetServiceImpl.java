package com.porest.desk.asset.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.domain.AssetTransfer;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.asset.repository.AssetTransferRepository;
import com.porest.desk.asset.service.dto.AssetServiceDto;
import com.porest.desk.card.domain.CardCatalog;
import com.porest.desk.card.repository.CardCatalogRepository;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AssetServiceImpl implements AssetService {
    private final AssetRepository assetRepository;
    private final AssetTransferRepository assetTransferRepository;
    private final UserRepository userRepository;
    private final CardCatalogRepository cardCatalogRepository;

    @Override
    @Transactional
    public AssetServiceDto.AssetInfo createAsset(AssetServiceDto.CreateAssetCommand command) {
        log.debug("자산 등록 시작: userRowId={}, assetName={}", command.userRowId(), command.assetName());

        User user = userRepository.findById(command.userRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

        CardCatalog cardCatalog = resolveCardCatalog(command.cardCatalogRowId());

        Asset asset = Asset.createAsset(
            user,
            command.assetName(),
            command.assetType(),
            command.balance(),
            command.currency() != null ? command.currency() : "KRW",
            command.icon(),
            command.color(),
            command.institution(),
            command.memo(),
            command.sortOrder() != null ? command.sortOrder() : 0,
            cardCatalog
        );

        assetRepository.save(asset);
        log.info("자산 등록 완료: assetId={}, userRowId={}", asset.getRowId(), command.userRowId());

        return AssetServiceDto.AssetInfo.from(asset);
    }

    @Override
    public List<AssetServiceDto.AssetInfo> getAssets(Long userRowId) {
        log.debug("자산 목록 조회: userRowId={}", userRowId);

        return assetRepository.findByUser(userRowId).stream()
            .map(AssetServiceDto.AssetInfo::from)
            .toList();
    }

    @Override
    public AssetServiceDto.AssetInfo getAsset(Long assetId, Long userRowId) {
        log.debug("자산 상세 조회: assetId={}", assetId);

        Asset asset = findAssetOrThrow(assetId);
        validateAssetOwnership(asset, userRowId);
        return AssetServiceDto.AssetInfo.from(asset);
    }

    @Override
    @Transactional
    public AssetServiceDto.AssetInfo updateAsset(Long assetId, Long userRowId, AssetServiceDto.UpdateAssetCommand command) {
        log.debug("자산 수정 시작: assetId={}", assetId);

        Asset asset = findAssetOrThrow(assetId);
        validateAssetOwnership(asset, userRowId);

        CardCatalog cardCatalog = resolveCardCatalog(command.cardCatalogRowId());

        asset.updateAsset(
            command.assetName(),
            command.assetType(),
            command.balance(),
            command.currency(),
            command.icon(),
            command.color(),
            command.institution(),
            command.memo(),
            command.isIncludedInTotal(),
            cardCatalog
        );

        log.info("자산 수정 완료: assetId={}", assetId);
        return AssetServiceDto.AssetInfo.from(asset);
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
    public AssetServiceDto.AssetSummary getAssetSummary(Long userRowId) {
        log.debug("자산 요약 조회: userRowId={}", userRowId);

        List<Asset> assets = assetRepository.findByUser(userRowId);

        Long totalBalance = assets.stream()
            .filter(a -> a.getIsIncludedInTotal() == com.porest.core.type.YNType.Y)
            .mapToLong(Asset::getBalance)
            .sum();

        List<AssetServiceDto.AssetTypeSummary> byType = assets.stream()
            .collect(Collectors.groupingBy(Asset::getAssetType))
            .entrySet().stream()
            .map(entry -> new AssetServiceDto.AssetTypeSummary(
                entry.getKey(),
                entry.getValue().stream().mapToLong(Asset::getBalance).sum(),
                entry.getValue().size()
            ))
            .toList();

        return new AssetServiceDto.AssetSummary(totalBalance, byType);
    }

    @Override
    @Transactional
    public void reorderAssets(Long userRowId, List<AssetServiceDto.ReorderItem> items) {
        log.debug("자산 정렬 변경: userRowId={}, count={}", userRowId, items.size());

        for (AssetServiceDto.ReorderItem item : items) {
            Asset asset = findAssetOrThrow(item.assetId());
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

        Asset fromAsset = findAssetOrThrow(command.fromAssetRowId());
        validateAssetOwnership(fromAsset, command.userRowId());
        Asset toAsset = findAssetOrThrow(command.toAssetRowId());
        validateAssetOwnership(toAsset, command.userRowId());

        AssetTransfer transfer = AssetTransfer.createTransfer(
            user, fromAsset, toAsset,
            command.amount(), command.fee(), command.description(), command.transferDate()
        );

        // 잔액 업데이트
        Long fee = command.fee() != null ? command.fee() : 0L;
        fromAsset.updateBalance(fromAsset.getBalance() - command.amount() - fee);
        toAsset.updateBalance(toAsset.getBalance() + command.amount());

        assetTransferRepository.save(transfer);
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

        // 잔액 복원
        Asset fromAsset = transfer.getFromAsset();
        Asset toAsset = transfer.getToAsset();
        fromAsset.updateBalance(fromAsset.getBalance() + transfer.getAmount() + transfer.getFee());
        toAsset.updateBalance(toAsset.getBalance() - transfer.getAmount());

        transfer.deleteTransfer();
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
}
