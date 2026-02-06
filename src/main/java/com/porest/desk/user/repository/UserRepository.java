package com.porest.desk.user.repository;

import com.porest.desk.user.domain.User;

import java.util.Optional;

public interface UserRepository {
    Optional<User> findById(Long rowId);
    Optional<User> findByUserId(String userId);
    User save(User user);
}
