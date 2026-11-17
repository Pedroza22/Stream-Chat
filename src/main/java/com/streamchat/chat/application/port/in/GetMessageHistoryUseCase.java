package com.streamchat.chat.application.port.in;

import com.streamchat.chat.application.dto.response.MessageResponse;
import com.streamchat.chat.application.dto.response.PageResponse;

import java.util.UUID;

public interface GetMessageHistoryUseCase {
    PageResponse<MessageResponse> execute(UUID groupId, UUID requesterId, int page, int size);
}
