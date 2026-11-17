package com.streamchat.chat.application.dto;

import java.util.UUID;

public record AddMemberCommand(UUID requesterId, UUID groupId, UUID targetUserId) {
}
