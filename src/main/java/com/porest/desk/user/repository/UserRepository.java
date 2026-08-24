package com.porest.desk.user.repository;

import com.porest.desk.user.domain.User;

import java.util.Optional;

public interface UserRepository {
    Optional<User> findById(Long rowId);
    Optional<User> findByUserId(String userId);

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
