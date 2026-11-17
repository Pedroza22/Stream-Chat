package com.streamchat.chat.application;

import com.streamchat.chat.application.dto.RegisterUserCommand;
import com.streamchat.chat.application.port.out.UserRepository;
import com.streamchat.chat.application.usecase.RegisterUserUseCaseImpl;
import com.streamchat.chat.domain.exception.DuplicateEmailException;
import com.streamchat.chat.domain.exception.DuplicateUsernameException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegisterUserUseCaseTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private RegisterUserUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        useCase = new RegisterUserUseCaseImpl(userRepository, passwordEncoder, Clock.systemUTC());
    }

    @Test
    void throwsDuplicateUsernameException_whenUsernameAlreadyExists() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() ->
                useCase.execute(new RegisterUserCommand("alice", "alice@example.com", "password123")))
                .isInstanceOf(DuplicateUsernameException.class);
    }

    @Test
    void throwsDuplicateEmailException_whenEmailAlreadyExists() {
        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(userRepository.existsByEmail("bob@example.com")).thenReturn(true);

        assertThatThrownBy(() ->
                useCase.execute(new RegisterUserCommand("bob", "bob@example.com", "password123")))
                .isInstanceOf(DuplicateEmailException.class);
    }
}
