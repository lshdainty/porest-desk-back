package com.porest.desk.stock.service;

import com.porest.desk.stock.repository.StockMasterSearchCondition;
import com.porest.desk.stock.service.dto.StockServiceDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface StockMasterService {
    Page<StockServiceDto.StockInfo> search(StockMasterSearchCondition condition, Pageable pageable);
}
