package com.porest.desk.stock.service.dto;

import com.porest.desk.stock.domain.StockMaster;
import com.porest.desk.stock.type.StockMarket;
import com.porest.desk.stock.type.StockSecurityType;

public class StockServiceDto {

    /** 종목 검색 결과 1건 */
    public record StockInfo(
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
        public static StockInfo from(StockMaster stock) {
            return new StockInfo(
                stock.getRowId(),
                stock.getCountryCode(),
                stock.getMarketCode(),
                stock.getSymbol(),
                stock.getStandardCode(),
                stock.getNameKr(),
                stock.getNameEn(),
                stock.getSecurityType(),
                stock.getCurrency()
            );
        }
    }
}
