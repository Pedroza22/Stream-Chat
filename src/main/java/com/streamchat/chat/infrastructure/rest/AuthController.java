package com.streamchat.chat.infrastructure.rest;

import com.streamchat.chat.application.ChatFacade;
import com.streamchat.chat.application.dto.RegisterUserCommand;
import com.streamchat.chat.application.dto.request.LoginRequest;
import com.streamchat.chat.application.dto.request.RegisterRequest;
import com.streamchat.chat.application.dto.response.AuthResponse;
import com.streamchat.chat.application.dto.response.UserResponse;
import com.streamchat.chat.application.port.out.UserRepository;
import com.streamchat.chat.domain.exception.UserNotFoundException;
import com.streamchat.chat.domain.valueobject.UserId;
import com.streamchat.chat.infrastructure.security.AuthenticatedUser;
import com.streamchat.chat.infrastructure.security.JwtTokenProvider;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final ChatFacade facade;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;

    public AuthController(ChatFacade facade,
                          JwtTokenProvider jwtTokenProvider,
                          AuthenticationManager authenticationManager,
                          UserRepository userRepository) {
        this.facade = facade;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest req) {
        UserResponse response = facade.registerUser(
                new RegisterUserCommand(req.username(), req.email(), req.password()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        var user = userRepository.findByUsername(req.username())
                .orElseThrow(() -> new UserNotFoundException(req.username()));
        var authenticatedUser = new AuthenticatedUser(user.getId(), user.getUsername());
        String token = jwtTokenProvider.generateToken(authenticatedUser);
        return ResponseEntity.ok(new AuthResponse(token, user.getUsername(), user.getId()));
    }
}
