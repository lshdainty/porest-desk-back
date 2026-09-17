package com.porest.desk.user.repository;

import com.porest.desk.user.domain.User;

import java.util.Optional;

public interface UserRepository {
    Optional<User> findById(Long rowId);
    Optional<User> findByUserId(String userId);

    /**
     * 삭제 표식까지 <b>포함해</b> 찾는다 — 해지한 계정을 알아보려면 필요하다.
     *
     * <p>{@link #findByUserId}는 {@code is_deleted='N'} 만 보므로 해지한 사용자는 "없는 사람"
     * 이 된다. 그대로 두면 토큰 교환이 새 행을 만들려다 {@code UK_users_user_id} 에 부딪힌다.
     */
    Optional<User> findByUserIdIncludingWithdrawn(String userId);

    /**
     * SSO 사용자 번호로 조회.
     *
     * <p>SSO 가 내리는 이벤트는 자기 {@code users.row_id} 만 들고 온다 — 그걸 desk 의 PK 로
     * 옮기는 유일한 다리다. 로그인 아이디({@code userId})가 아니라 번호로 대조하는 이유는
     * 아이디는 바뀔 수 있고 번호는 안 바뀌기 때문이다.
     */
    Optional<User> findBySsoUserRowId(Long ssoUserRowId);
    User save(User user);
}
