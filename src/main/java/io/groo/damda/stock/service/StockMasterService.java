package io.groo.damda.stock.service;

import io.groo.damda.stock.repository.StockMasterSearchCondition;
import io.groo.damda.stock.service.dto.StockServiceDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface StockMasterService {
    Page<StockServiceDto.StockInfo> search(StockMasterSearchCondition condition, Pageable pageable);
}
