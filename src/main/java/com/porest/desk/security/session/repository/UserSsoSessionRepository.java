package com.porest.desk.security.session.repository;

import com.porest.core.type.YNType;
import com.porest.desk.security.session.domain.UserSsoSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserSsoSessionRepository extends JpaRepository<UserSsoSession, Long> {

    /**
     * 재발급용 조회 — 행 잠금.
     *
     * <p>같은 세션에 동시 요청이 들어오면 각자 SSO 재발급을 시도하고, rotation 때문에 하나만
     * 성공한다. 나머지는 이미 폐기된 토큰으로 거부당해 멀쩡한 사용자가 로그아웃된다.
     * 여기서 줄을 세워 그 경합을 없앤다. 만료 직후 한 번만 지나가는 경로라 비용도 거의 없다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from UserSsoSession s where s.sessionId = :sessionId and s.isDeleted = :isDeleted")
    Optional<UserSsoSession> findForRefresh(@Param("sessionId") String sessionId,
                                            @Param("isDeleted") YNType isDeleted);

    /** 로그아웃용 — 잠글 필요 없다. */
    Optional<UserSsoSession> findBySessionIdAndIsDeleted(String sessionId, YNType isDeleted);

    /** 사용자의 살아 있는 세션 전체 (기기 목록·전체 로그아웃용). */
    List<UserSsoSession> findAllByUserRowIdAndIsDeleted(Long userRowId, YNType isDeleted);
}
