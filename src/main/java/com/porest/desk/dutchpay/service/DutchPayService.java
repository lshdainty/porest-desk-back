package com.porest.desk.dutchpay.service;

import com.porest.desk.dutchpay.service.dto.DutchPayServiceDto;

import java.util.List;

public interface DutchPayService {
    DutchPayServiceDto.DutchPayInfo createDutchPay(DutchPayServiceDto.CreateCommand command);
    List<DutchPayServiceDto.DutchPayInfo> getDutchPays(Long userRowId);
    DutchPayServiceDto.DutchPayInfo getDutchPay(Long dutchPayId, Long userRowId);
    DutchPayServiceDto.DutchPayInfo updateDutchPay(Long dutchPayId, Long userRowId, DutchPayServiceDto.UpdateCommand command);
    void deleteDutchPay(Long dutchPayId, Long userRowId);
    DutchPayServiceDto.DutchPayInfo markParticipantPaid(Long dutchPayId, Long userRowId, Long participantId);
    DutchPayServiceDto.DutchPayInfo settleAll(Long dutchPayId, Long userRowId);
}
