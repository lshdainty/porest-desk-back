package com.porest.desk.asset.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.asset.controller.dto.AssetApiDto;
import com.porest.desk.asset.service.AssetService;
import com.porest.desk.asset.service.dto.AssetServiceDto;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.UserPrincipal;
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
            @RequestBody AssetApiDto.CreateAssetRequest request) {
        AssetServiceDto.AssetInfo info = assetService.createAsset(new AssetServiceDto.CreateAssetCommand(
            loginUser.getRowId(),
            request.assetName(), request.assetType(), request.balance(),
            request.currency(), request.icon(), request.color(),
            request.institution(), request.memo(), request.sortOrder()
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
        AssetServiceDto.AssetInfo info = assetService.getAsset(id);
        return ApiResponse.success(AssetApiDto.AssetResponse.from(info));
    }

    @PutMapping("/asset/{id}")
    public ApiResponse<AssetApiDto.AssetResponse> updateAsset(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id,
            @RequestBody AssetApiDto.UpdateAssetRequest request) {
        AssetServiceDto.AssetInfo info = assetService.updateAsset(id, new AssetServiceDto.UpdateAssetCommand(
            request.assetName(), request.assetType(), request.balance(),
            request.currency(), request.icon(), request.color(),
            request.institution(), request.memo(), request.isIncludedInTotal()
        ));
        return ApiResponse.success(AssetApiDto.AssetResponse.from(info));
    }

    @DeleteMapping("/asset/{id}")
    public ApiResponse<Void> deleteAsset(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        assetService.deleteAsset(id);
        return ApiResponse.success();
    }

    @GetMapping("/assets/summary")
    public ApiResponse<AssetApiDto.AssetSummaryResponse> getAssetSummary(@LoginUser UserPrincipal loginUser) {
        AssetServiceDto.AssetSummary summary = assetService.getAssetSummary(loginUser.getRowId());
        return ApiResponse.success(AssetApiDto.AssetSummaryResponse.from(summary));
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
            request.amount(), request.fee(), request.description(), request.transferDate()
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

    @DeleteMapping("/asset-transfer/{id}")
    public ApiResponse<Void> deleteTransfer(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        assetService.deleteTransfer(id);
        return ApiResponse.success();
    }
}
