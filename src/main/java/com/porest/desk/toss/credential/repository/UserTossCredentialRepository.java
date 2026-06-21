package com.porest.desk.toss.credential.repository;

import com.porest.core.type.YNType;
import com.porest.desk.toss.credential.domain.UserTossCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserTossCredentialRepository extends JpaRepository<UserTossCredential, Long> {

    /** 사용자 크리덴셜 1건(삭제 포함 — 재등록 UPSERT 용). */
    Optional<UserTossCredential> findByUserRowId(Long userRowId);

    /** 사용 가능한(미삭제·검증완료) 크리덴셜. */
    Optional<UserTossCredential> findByUserRowIdAndIsDeletedAndIsVerified(Long userRowId, YNType isDeleted, YNType isVerified);
}
