package io.groo.damda.stock.service;

import io.groo.damda.stock.domain.StockMaster;
import io.groo.damda.stock.repository.StockMasterRepository;
import io.groo.damda.stock.repository.StockMasterSearchCondition;
import io.groo.damda.stock.service.dto.StockServiceDto;
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
