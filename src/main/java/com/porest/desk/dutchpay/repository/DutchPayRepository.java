package com.porest.desk.dutchpay.repository;

import com.porest.desk.dutchpay.domain.DutchPay;
import com.porest.desk.dutchpay.domain.DutchPayParticipant;

import java.util.List;
import java.util.Optional;

public interface DutchPayRepository {
    Optional<DutchPay> findById(Long rowId);
    List<DutchPay> findAllByUser(Long userRowId);

    /**
     * 이 사용자로 연결된 참가자 행 전부 — <b>남이 만든 정산</b>에 들어간 나까지 포함한다.
     *
     * <p>해지 루틴이 그 행들의 연결을 끊고 이름을 익명화하는 데 쓴다. 정산 자체는 만든
     * 사람 것이라 지우지 않는다.
     */
    List<DutchPayParticipant> findParticipantsByUser(Long userRowId);
    DutchPay save(DutchPay dutchPay);
    void delete(DutchPay dutchPay);

    /**
     * 지금까지의 변경을 즉시 내보낸다.
     *
     * <p>참가자 동기화가 이걸 <b>순서를 만들기 위해</b> 쓴다 — 하이버네이트는 한 플러시에서
     * INSERT 를 UPDATE 보다 먼저 내므로, 명시하지 않으면 "빠진 사람을 지우고 같은 이름을 새로
     * 넣는" 저장이 INSERT 부터 나가 활성 이름 UNIQUE 에 걸린다.
     */
    void flush();
}
