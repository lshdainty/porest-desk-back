package com.porest.desk.dataimport.sms.repository;

import com.porest.desk.dataimport.sms.domain.SmsCardMapping;

import java.util.List;
import java.util.Optional;

public interface SmsCardMappingRepository {

    /**
     * (user, cardHint) 행 — <b>삭제분 포함</b>.
     *
     * <p>유니크 제약이 삭제 여부를 보지 않으므로, 삭제 행을 빼고 조회한 뒤 새로 만들면
     * 재지정이 통째로 실패한다. 되살려 쓰기 위해 삭제분까지 본다.
     */
    Optional<SmsCardMapping> findByCardHintIncludingDeleted(Long userRowId, String cardHint);

    /** 사용자의 활성 매핑 전체 — 설정 화면에서 목록으로 보여준다. */
    List<SmsCardMapping> findAllActiveByUser(Long userRowId);

    Optional<SmsCardMapping> findActiveById(Long rowId);

    SmsCardMapping save(SmsCardMapping mapping);
}
