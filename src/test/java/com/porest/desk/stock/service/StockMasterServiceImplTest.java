package com.porest.desk.stock.service;

import com.porest.desk.stock.client.dto.KisStockRecord;
import com.porest.desk.stock.domain.StockMaster;
import com.porest.desk.stock.repository.StockMasterRepository;
import com.porest.desk.stock.repository.StockMasterSearchCondition;
import com.porest.desk.stock.service.dto.StockServiceDto;
import com.porest.desk.stock.type.StockMarket;
import com.porest.desk.stock.type.StockSecurityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/** 종목 검색 서비스 테스트 — 리포 위임과 엔티티→DTO 매핑을 검증한다. */
@ExtendWith(MockitoExtension.class)
class StockMasterServiceImplTest {

    @Mock private StockMasterRepository stockMasterRepository;
    @InjectMocks private StockMasterServiceImpl stockMasterService;

    @Test
    @DisplayName("검색 결과를 StockInfo 로 매핑해 돌려준다")
    void searchMapsEntityToInfo() {
        StockMaster apple = StockMaster.create(StockMarket.NAS,
            new KisStockRecord("AAPL", null, "NASAAPL", "애플", "APPLE INC", StockSecurityType.STOCK, "USD"));
        StockMasterSearchCondition condition = new StockMasterSearchCondition("애플", null, null);
        Pageable pageable = PageRequest.of(0, 20);
        given(stockMasterRepository.search(eq(condition), any(Pageable.class)))
            .willReturn(new PageImpl<>(List.of(apple), pageable, 1));

        Page<StockServiceDto.StockInfo> page = stockMasterService.search(condition, pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        StockServiceDto.StockInfo info = page.getContent().get(0);
        assertThat(info.symbol()).isEqualTo("AAPL");
        assertThat(info.marketCode()).isEqualTo(StockMarket.NAS);
        assertThat(info.countryCode()).isEqualTo("US");
        assertThat(info.nameKr()).isEqualTo("애플");
        assertThat(info.nameEn()).isEqualTo("APPLE INC");
        assertThat(info.securityType()).isEqualTo(StockSecurityType.STOCK);
        assertThat(info.currency()).isEqualTo("USD");
    }
}
