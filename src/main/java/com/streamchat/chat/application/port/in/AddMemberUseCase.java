package com.streamchat.chat.application.port.in;

import com.streamchat.chat.application.dto.AddMemberCommand;

public interface AddMemberUseCase {
    void execute(AddMemberCommand command);
}
