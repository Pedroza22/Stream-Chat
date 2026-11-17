package com.streamchat.chat.application.port.in;

import com.streamchat.chat.application.dto.SendMessageCommand;
import com.streamchat.chat.domain.model.ChatMessage;

public interface SendMessageUseCase {
    ChatMessage execute(SendMessageCommand command);
}
