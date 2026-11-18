package com.streamchat.chat.application.port.in;

import java.util.UUID;

public interface RemoveMemberUseCase {
    void execute(UUID requesterId, UUID groupId, UUID targetUserId);
}
