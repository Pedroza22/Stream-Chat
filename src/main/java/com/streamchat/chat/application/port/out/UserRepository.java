package com.streamchat.chat.application.port.out;

import com.streamchat.chat.domain.model.User;
import com.streamchat.chat.domain.valueobject.UserId;

import java.util.Optional;

public interface UserRepository {
    Optional<User> findById(UserId id);
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    User save(User user);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
