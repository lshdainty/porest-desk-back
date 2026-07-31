package com.porest.desk.stock.service;

import com.porest.desk.stock.domain.StockMaster;
import com.porest.desk.stock.repository.StockMasterRepository;
import com.porest.desk.stock.repository.StockMasterSearchCondition;
import com.porest.desk.stock.service.dto.StockServiceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class StockMasterServiceImpl implements StockMasterService {

    private final StockMasterRepository stockMasterRepository;

    @Override
    public Page<StockServiceDto.StockInfo> search(StockMasterSearchCondition condition, Pageable pageable) {
        log.debug("종목 검색: condition={}, pageable={}", condition, pageable);
        Page<StockMaster> page = stockMasterRepository.search(condition, pageable);
        return page.map(StockServiceDto.StockInfo::from);
    }
}
