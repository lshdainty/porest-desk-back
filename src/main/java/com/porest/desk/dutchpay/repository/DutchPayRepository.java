package com.porest.desk.dutchpay.repository;

import com.porest.desk.dutchpay.domain.DutchPay;

import java.util.List;
import java.util.Optional;

public interface DutchPayRepository {
    Optional<DutchPay> findById(Long rowId);
    List<DutchPay> findAllByUser(Long userRowId);
    DutchPay save(DutchPay dutchPay);
    void delete(DutchPay dutchPay);
}
