package com.porest.desk.asset.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.asset.controller.dto.AssetTradeApiDto;
import com.porest.desk.asset.service.AssetTradeService;
import com.porest.desk.asset.service.dto.AssetTradeServiceDto;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 투자 자산의 매수·매도. 예수금·보유 수량·원가·실현손익이 함께 움직인다. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AssetTradeApiController {
    private final AssetTradeService assetTradeService;

    @PostMapping("/asset-trade")
    public ApiResponse<AssetTradeApiDto.TradeResponse> createTrade(
            @LoginUser UserPrincipal loginUser,
            @RequestBody AssetTradeApiDto.CreateTradeRequest request) {
        AssetTradeServiceDto.TradeInfo info = assetTradeService.createTrade(
            new AssetTradeServiceDto.CreateTradeCommand(
                loginUser.getRowId(), request.assetRowId(), request.tradeType(),
                request.holdingType(), request.holdingKey(), request.linked(),
                request.quantity(), request.amount(), request.fee(),
                request.tradeDate(), request.description()));
        return ApiResponse.success(AssetTradeApiDto.TradeResponse.from(info));
    }

    @GetMapping("/asset-trades")
    public ApiResponse<List<AssetTradeApiDto.TradeResponse>> getTrades(
            @LoginUser UserPrincipal loginUser,
            @RequestParam Long assetRowId) {
        return ApiResponse.success(AssetTradeApiDto.TradeResponse.from(
            assetTradeService.getTrades(assetRowId, loginUser.getRowId())));
    }

    @DeleteMapping("/asset-trade/{tradeRowId}")
    public ApiResponse<Void> deleteTrade(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long tradeRowId) {
        assetTradeService.deleteTrade(tradeRowId, loginUser.getRowId());
        return ApiResponse.success(null);
    }
}
