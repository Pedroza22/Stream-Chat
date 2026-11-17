package com.streamchat.chat.application.dto;

import java.util.UUID;

public record SendMessageCommand(UUID senderId, UUID groupId, String content) {
}
