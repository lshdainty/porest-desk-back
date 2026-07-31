package com.porest.desk.stock.controller.dto;

import com.porest.desk.stock.service.dto.StockWatchServiceDto;
import com.porest.desk.stock.type.StockMarket;
import com.porest.desk.stock.type.StockSecurityType;

import java.util.List;

public class StockWatchApiDto {

    /** 그룹 생성/이름 변경 요청. 이름 검증(공백·50자)은 서비스가 한다 */
    public record GroupRequest(
        String groupName
    ) {
    }

    /** 종목 추가 요청. marketCode 미지정 시 심볼 정확 일치 중 KR/US 시장을 우선 해석 */
    public record ItemRequest(
        String symbol,
        StockMarket marketCode
    ) {
    }

    /** 관심목록 그룹 응답 (소속 종목 포함) */
    public record GroupResponse(
        Long rowId,
        String groupName,
        Integer sortOrder,
        List<ItemResponse> items
    ) {
        public static GroupResponse from(StockWatchServiceDto.GroupInfo info) {
            return new GroupResponse(
                info.rowId(),
                info.groupName(),
                info.sortOrder(),
                info.items().stream().map(ItemResponse::from).toList()
            );
        }
    }

    /** 관심 종목 응답 */
    public record ItemResponse(
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
        public static ItemResponse from(StockWatchServiceDto.ItemInfo info) {
            return new ItemResponse(
                info.rowId(),
                info.stockMasterRowId(),
                info.countryCode(),
                info.marketCode(),
                info.symbol(),
                info.nameKr(),
                info.nameEn(),
                info.securityType(),
                info.currency()
            );
        }
    }
}
