package com.porest.desk.apitoken.repository;

import com.porest.core.type.YNType;
import com.porest.desk.apitoken.domain.UserApiToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserApiTokenRepository extends JpaRepository<UserApiToken, Long> {

    /** 요청에 실려 온 토큰의 해시로 찾는다 — 폐기된 것은 제외. */
    Optional<UserApiToken> findByTokenHashAndIsDeleted(String tokenHash, YNType isDeleted);

    /** 내 토큰 목록 — 최근 발급한 것부터. */
    List<UserApiToken> findAllByUserRowIdAndIsDeletedOrderByRowIdDesc(Long userRowId, YNType isDeleted);

    long countByUserRowIdAndIsDeleted(Long userRowId, YNType isDeleted);

    /** 폐기 대상 — 남의 토큰 번호를 넣어도 안 걸리게 주인을 같이 본다. */
    Optional<UserApiToken> findByRowIdAndUserRowIdAndIsDeleted(Long rowId, Long userRowId, YNType isDeleted);
}
