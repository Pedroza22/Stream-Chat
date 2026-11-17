package com.streamchat.chat.application.dto;

import java.util.UUID;

public record CreateGroupCommand(UUID creatorId, String name) {
}
