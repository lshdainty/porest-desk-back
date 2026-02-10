package com.porest.desk.dutchpay.service;

import com.porest.desk.dutchpay.service.dto.DutchPayServiceDto;

import java.util.List;

public interface DutchPayService {
    DutchPayServiceDto.DutchPayInfo createDutchPay(DutchPayServiceDto.CreateCommand command);
    List<DutchPayServiceDto.DutchPayInfo> getDutchPays(Long userRowId);
    DutchPayServiceDto.DutchPayInfo getDutchPay(Long dutchPayId);
    DutchPayServiceDto.DutchPayInfo updateDutchPay(Long dutchPayId, DutchPayServiceDto.UpdateCommand command);
    void deleteDutchPay(Long dutchPayId);
    DutchPayServiceDto.DutchPayInfo markParticipantPaid(Long dutchPayId, Long participantId);
    DutchPayServiceDto.DutchPayInfo settleAll(Long dutchPayId);
}
