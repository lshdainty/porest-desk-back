package com.porest.desk.asset.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.asset.controller.dto.AssetApiDto;
import com.porest.desk.asset.service.AssetService;
import com.porest.desk.asset.service.dto.AssetServiceDto;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AssetApiController {
    private final AssetService assetService;

    // === Asset ===

    @PostMapping("/asset")
    public ApiResponse<AssetApiDto.AssetResponse> createAsset(
            @LoginUser UserPrincipal loginUser,
            @Valid @RequestBody AssetApiDto.CreateAssetRequest request) {
        AssetServiceDto.AssetInfo info = assetService.createAsset(new AssetServiceDto.CreateAssetCommand(
            loginUser.getRowId(),
            request.assetName(), request.assetType(), request.balance(), request.isOverdraft(),
            request.currency(), request.exchangeRate(), request.color(),
            request.institution(), request.memo(), request.sortOrder(),
            request.isIncludedInTotal(),
            request.cardCatalogRowId(),
            request.creditLimit(), request.paymentDay(), request.paymentAssetRowId(),
            AssetApiDto.HoldingRequest.toCommands(request.holdings())
        ));
        return ApiResponse.success(AssetApiDto.AssetResponse.from(info));
    }

    @GetMapping("/assets")
    public ApiResponse<AssetApiDto.AssetListResponse> getAssets(@LoginUser UserPrincipal loginUser) {
        List<AssetServiceDto.AssetInfo> infos = assetService.getAssets(loginUser.getRowId());
        return ApiResponse.success(AssetApiDto.AssetListResponse.from(infos));
    }

    @GetMapping("/asset/{id}")
    public ApiResponse<AssetApiDto.AssetResponse> getAsset(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        AssetServiceDto.AssetInfo info = assetService.getAsset(id, loginUser.getRowId());
        return ApiResponse.success(AssetApiDto.AssetResponse.from(info));
    }

    @PutMapping("/asset/{id}")
    public ApiResponse<AssetApiDto.AssetResponse> updateAsset(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id,
            @Valid @RequestBody AssetApiDto.UpdateAssetRequest request) {
        AssetServiceDto.AssetInfo info = assetService.updateAsset(id, loginUser.getRowId(), new AssetServiceDto.UpdateAssetCommand(
            request.assetName(), request.assetType(), request.balance(), request.isOverdraft(),
            request.currency(), request.exchangeRate(), request.color(),
            request.institution(), request.memo(), request.isIncludedInTotal(),
            request.cardCatalogRowId(),
            request.creditLimit(), request.paymentDay(), request.paymentAssetRowId(),
            AssetApiDto.HoldingRequest.toCommands(request.holdings())
        ));
        return ApiResponse.success(AssetApiDto.AssetResponse.from(info));
    }

    @DeleteMapping("/asset/{id}")
    public ApiResponse<Void> deleteAsset(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        assetService.deleteAsset(id, loginUser.getRowId());
        return ApiResponse.success();
    }

    // 투자 자산 ↔ 토스 보유종목 연결/해제 (프로+토스 연결 사용자 전용).
    @PutMapping("/asset/{id}/toss-link")
    public ApiResponse<AssetApiDto.AssetResponse> linkTossSymbol(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id,
            @RequestBody AssetApiDto.TossLinkRequest request) {
        AssetServiceDto.AssetInfo info = assetService.linkSymbol(
            id, loginUser.getRowId(), request.marketCode(), request.symbol(), request.quantity());
        return ApiResponse.success(AssetApiDto.AssetResponse.from(info));
    }

    @DeleteMapping("/asset/{id}/toss-link")
    public ApiResponse<AssetApiDto.AssetResponse> unlinkTossSymbol(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        AssetServiceDto.AssetInfo info = assetService.unlinkSymbol(id, loginUser.getRowId());
        return ApiResponse.success(AssetApiDto.AssetResponse.from(info));
    }

    @GetMapping("/assets/summary")
    public ApiResponse<AssetApiDto.AssetSummaryResponse> getAssetSummary(
            @LoginUser UserPrincipal loginUser,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        AssetServiceDto.AssetSummary summary = assetService.getAssetSummary(loginUser.getRowId(), year, month);
        return ApiResponse.success(AssetApiDto.AssetSummaryResponse.from(summary));
    }

    @GetMapping("/assets/net-worth-trend")
    public ApiResponse<AssetApiDto.NetWorthTrendResponse> getNetWorthTrend(
            @LoginUser UserPrincipal loginUser,
            @RequestParam(required = false, defaultValue = "12") Integer months) {
        List<AssetServiceDto.NetWorthTrendPoint> trend = assetService.getNetWorthTrend(loginUser.getRowId(), months);
        return ApiResponse.success(AssetApiDto.NetWorthTrendResponse.from(trend));
    }

    @GetMapping("/asset/{id}/balance-trend")
    public ApiResponse<AssetApiDto.AssetBalanceTrendResponse> getAssetBalanceTrend(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "12") Integer weeks) {
        List<AssetServiceDto.AssetBalancePoint> trend = assetService.getAssetBalanceTrend(id, loginUser.getRowId(), weeks);
        return ApiResponse.success(AssetApiDto.AssetBalanceTrendResponse.from(trend));
    }

    @PatchMapping("/assets/reorder")
    public ApiResponse<Void> reorderAssets(
            @LoginUser UserPrincipal loginUser,
            @RequestBody AssetApiDto.ReorderRequest request) {
        assetService.reorderAssets(
            loginUser.getRowId(),
            request.items().stream()
                .map(i -> new AssetServiceDto.ReorderItem(i.assetId(), i.sortOrder()))
                .toList()
        );
        return ApiResponse.success();
    }

    // === Asset Transfer ===

    @PostMapping("/asset-transfer")
    public ApiResponse<AssetApiDto.TransferResponse> createTransfer(
            @LoginUser UserPrincipal loginUser,
            @RequestBody AssetApiDto.CreateTransferRequest request) {
        AssetServiceDto.TransferInfo info = assetService.createTransfer(new AssetServiceDto.CreateTransferCommand(
            loginUser.getRowId(),
            request.fromAssetRowId(), request.toAssetRowId(),
            request.amount(), request.fee(), request.interestAmount(),
            request.description(), request.transferDate(),
            null  // 사용자가 만든 이체
        ));
        return ApiResponse.success(AssetApiDto.TransferResponse.from(info));
    }

    @GetMapping("/asset-transfers")
    public ApiResponse<AssetApiDto.TransferListResponse> getTransfers(
            @LoginUser UserPrincipal loginUser,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<AssetServiceDto.TransferInfo> infos = assetService.getTransfers(loginUser.getRowId(), startDate, endDate);
        return ApiResponse.success(AssetApiDto.TransferListResponse.from(infos));
    }

    @PutMapping("/asset-transfer/{id}")
    public ApiResponse<AssetApiDto.TransferResponse> updateTransfer(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id,
            @RequestBody AssetApiDto.CreateTransferRequest request) {
        AssetServiceDto.TransferInfo info = assetService.updateTransfer(id,
            new AssetServiceDto.CreateTransferCommand(
                loginUser.getRowId(), request.fromAssetRowId(), request.toAssetRowId(),
                request.amount(), request.fee(), request.interestAmount(),
                request.description(), request.transferDate(), null));
        return ApiResponse.success(AssetApiDto.TransferResponse.from(info));
    }

    @DeleteMapping("/asset-transfer/{id}")
    public ApiResponse<Void> deleteTransfer(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        assetService.deleteTransferByUser(id, loginUser.getRowId());
        return ApiResponse.success();
    }
}
