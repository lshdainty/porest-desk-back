package io.groo.damda.stock.controller;

import io.groo.core.controller.ApiResponse;
import io.groo.core.controller.dto.PageRequest;
import io.groo.core.controller.dto.PageResponse;
import io.groo.damda.stock.controller.dto.StockApiDto;
import io.groo.damda.stock.repository.StockMasterSearchCondition;
import io.groo.damda.stock.service.StockMasterService;
import io.groo.damda.stock.service.dto.StockServiceDto;
import io.groo.damda.stock.type.StockSecurityType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 종목 마스터 검색.
 *
 * <p>종목명은 공개 데이터라 구독 게이트 없이 로그인 사용자 전체에게 연다.
 * 시세·자산 연결은 기존 /api/v1/toss/** 와 자산 서비스의 SECURITIES 게이트가 그대로 지킨다.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StockApiController {
    private final StockMasterService stockMasterService;

    @GetMapping("/stocks")
    public ApiResponse<PageResponse<StockApiDto.StockResponse>> searchStocks(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String countryCode,
        @RequestParam(required = false) StockSecurityType securityType,
        PageRequest pageRequest
    ) {
        StockMasterSearchCondition condition = new StockMasterSearchCondition(keyword, countryCode, securityType);
        Page<StockServiceDto.StockInfo> page = stockMasterService.search(condition, pageRequest.toPageable());
        return ApiResponse.success(PageResponse.of(page, StockApiDto.StockResponse::from));
    }
}
