package com.porest.desk.stock.controller.dto;

import com.porest.desk.stock.service.dto.StockServiceDto;
import com.porest.desk.stock.type.StockMarket;
import com.porest.desk.stock.type.StockSecurityType;

public class StockApiDto {

    /** 종목 검색 결과 1건 */
    public record StockResponse(
        Long rowId,
        String countryCode,
        StockMarket marketCode,
        String symbol,
        String standardCode,
        String nameKr,
        String nameEn,
        StockSecurityType securityType,
        String currency
    ) {
        public static StockResponse from(StockServiceDto.StockInfo info) {
            return new StockResponse(
                info.rowId(),
                info.countryCode(),
                info.marketCode(),
                info.symbol(),
                info.standardCode(),
                info.nameKr(),
                info.nameEn(),
                info.securityType(),
                info.currency()
            );
        }
    }
}
