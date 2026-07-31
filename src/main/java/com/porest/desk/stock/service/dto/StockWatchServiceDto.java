package com.porest.desk.stock.service.dto;

import com.porest.desk.stock.domain.StockMaster;
import com.porest.desk.stock.domain.StockWatchGroup;
import com.porest.desk.stock.domain.StockWatchItem;
import com.porest.desk.stock.type.StockMarket;
import com.porest.desk.stock.type.StockSecurityType;

import java.util.List;

public class StockWatchServiceDto {

    /** 관심목록 그룹 (소속 종목 포함) */
    public record GroupInfo(
        Long rowId,
        String groupName,
        Integer sortOrder,
        List<ItemInfo> items
    ) {
        public static GroupInfo of(StockWatchGroup group, List<ItemInfo> items) {
            return new GroupInfo(group.getRowId(), group.getGroupName(), group.getSortOrder(), items);
        }
    }

    /** 관심 종목 1건 (종목 마스터 조인) */
    public record ItemInfo(
        Long rowId,
        Long stockMasterRowId,
        String countryCode,
        StockMarket marketCode,
        String symbol,
        String nameKr,
        String nameEn,
        StockSecurityType securityType,
        String currency
    ) {
        public static ItemInfo of(StockWatchItem item, StockMaster stock) {
            return new ItemInfo(
                item.getRowId(),
                stock.getRowId(),
                stock.getCountryCode(),
                stock.getMarketCode(),
                stock.getSymbol(),
                stock.getNameKr(),
                stock.getNameEn(),
                stock.getSecurityType(),
                stock.getCurrency()
            );
        }
    }
}
