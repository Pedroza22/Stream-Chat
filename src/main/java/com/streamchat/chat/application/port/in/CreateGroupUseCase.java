package com.streamchat.chat.application.port.in;

import com.streamchat.chat.application.dto.CreateGroupCommand;
import com.streamchat.chat.domain.model.ChatGroup;

public interface CreateGroupUseCase {
    ChatGroup execute(CreateGroupCommand command);
}
