package com.streamchat.chat.application.usecase;

import com.streamchat.chat.application.dto.RegisterUserCommand;
import com.streamchat.chat.application.dto.response.UserResponse;
import com.streamchat.chat.application.port.in.RegisterUserUseCase;
import com.streamchat.chat.application.port.out.UserRepository;
import com.streamchat.chat.domain.exception.DuplicateEmailException;
import com.streamchat.chat.domain.exception.DuplicateUsernameException;
import com.streamchat.chat.domain.model.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@Transactional
public class RegisterUserUseCaseImpl implements RegisterUserUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public RegisterUserUseCaseImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    public UserResponse execute(RegisterUserCommand cmd) {
        if (userRepository.existsByUsername(cmd.username())) {
            throw new DuplicateUsernameException(cmd.username());
        }
        if (userRepository.existsByEmail(cmd.email())) {
            throw new DuplicateEmailException(cmd.email());
        }
        String hash = passwordEncoder.encode(cmd.rawPassword());
        User user = User.create(cmd.username(), cmd.email(), hash, clock);
        user = userRepository.save(user);
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getCreatedAt());
    }
}
