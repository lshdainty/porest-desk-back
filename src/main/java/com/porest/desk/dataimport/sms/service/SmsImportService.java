package com.porest.desk.dataimport.sms.service;

import com.porest.desk.dataimport.sms.service.dto.SmsImportServiceDto;

import java.util.List;

/**
 * 결제 문자 → 지출. 파싱(미리보기)과 저장을 분리한다.
 *
 * <p>{@code parse} 는 아무것도 저장하지 않는다 — 파일 가져오기의 analyze/execute 와 같은 계약이다.
 * 사용자가 확인·보정한 값으로 {@code commit} 을 불러야 지출이 생긴다.
 */
public interface SmsImportService {

    /** 문자 해석 — 저장 없음. 자산 매핑·카테고리 추론까지 얹어 돌려준다. */
    SmsImportServiceDto.ParseResult parse(String text, Long userRowId);

    /** 확정 값으로 지출 생성. 취소 문자면 거부한다(1차 범위). */
    SmsImportServiceDto.CommitResult commit(SmsImportServiceDto.CommitCommand command);

    /** 기억해 둔 카드 매핑 목록. */
    List<SmsImportServiceDto.CardMappingInfo> getCardMappings(Long userRowId);

    /** 카드 매핑 해제 — 다음 문자부터 다시 물어본다. */
    void deleteCardMapping(Long rowId, Long userRowId);
}
