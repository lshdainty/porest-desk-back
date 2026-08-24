package com.porest.desk.securities.repository;

import com.porest.core.type.YNType;
import com.porest.desk.securities.domain.UserSecuritiesCredential;
import com.porest.desk.securities.type.SecuritiesBroker;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserSecuritiesCredentialRepository extends JpaRepository<UserSecuritiesCredential, Long> {

    /** 사용자 × 증권사 1건(삭제 포함 — 재등록 UPSERT 용). */
    Optional<UserSecuritiesCredential> findByUserRowIdAndBroker(Long userRowId, SecuritiesBroker broker);

    /** 사용 가능한(미삭제·검증완료) 크리덴셜 1건. */
    Optional<UserSecuritiesCredential> findByUserRowIdAndBrokerAndIsDeletedAndIsVerified(
        Long userRowId, SecuritiesBroker broker, YNType isDeleted, YNType isVerified);

    /** 사용자의 전체 크리덴셜(삭제 포함) — 상태 화면이 증권사 목록을 그리는 데 쓴다. */
    List<UserSecuritiesCredential> findAllByUserRowId(Long userRowId);

    /** 사용 가능한 크리덴셜 전체 — 기본 소스 판정·승계에 쓴다. */
    List<UserSecuritiesCredential> findAllByUserRowIdAndIsDeletedAndIsVerified(
        Long userRowId, YNType isDeleted, YNType isVerified);
}
