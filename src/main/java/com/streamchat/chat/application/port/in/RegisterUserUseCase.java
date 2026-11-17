package com.streamchat.chat.application.port.in;

import com.streamchat.chat.application.dto.RegisterUserCommand;
import com.streamchat.chat.application.dto.response.UserResponse;

public interface RegisterUserUseCase {
    UserResponse execute(RegisterUserCommand command);
}
