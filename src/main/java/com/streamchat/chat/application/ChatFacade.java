package com.streamchat.chat.application;

import com.streamchat.chat.application.dto.AddMemberCommand;
import com.streamchat.chat.application.dto.CreateGroupCommand;
import com.streamchat.chat.application.dto.RegisterUserCommand;
import com.streamchat.chat.application.dto.SendMessageCommand;
import com.streamchat.chat.application.dto.response.MessageResponse;
import com.streamchat.chat.application.dto.response.PageResponse;
import com.streamchat.chat.application.dto.response.UserResponse;
import com.streamchat.chat.application.port.in.AddMemberUseCase;
import com.streamchat.chat.application.port.in.CreateGroupUseCase;
import com.streamchat.chat.application.port.in.GetMessageHistoryUseCase;
import com.streamchat.chat.application.port.in.RegisterUserUseCase;
import com.streamchat.chat.application.port.in.RemoveMemberUseCase;
import com.streamchat.chat.application.port.in.SendMessageUseCase;
import com.streamchat.chat.domain.model.ChatGroup;
import com.streamchat.chat.domain.model.ChatMessage;
import org.springframework.stereotype.Service;

import java.util.UUID;

import java.util.UUID;

@Service
public class ChatFacade {

    private final SendMessageUseCase sendMessageUseCase;
    private final CreateGroupUseCase createGroupUseCase;
    private final AddMemberUseCase addMemberUseCase;
    private final RemoveMemberUseCase removeMemberUseCase;
    private final RegisterUserUseCase registerUserUseCase;
    private final GetMessageHistoryUseCase getMessageHistoryUseCase;

    public ChatFacade(SendMessageUseCase sendMessageUseCase,
                      CreateGroupUseCase createGroupUseCase,
                      AddMemberUseCase addMemberUseCase,
                      RemoveMemberUseCase removeMemberUseCase,
                      RegisterUserUseCase registerUserUseCase,
                      GetMessageHistoryUseCase getMessageHistoryUseCase) {
        this.sendMessageUseCase = sendMessageUseCase;
        this.createGroupUseCase = createGroupUseCase;
        this.addMemberUseCase = addMemberUseCase;
        this.removeMemberUseCase = removeMemberUseCase;
        this.registerUserUseCase = registerUserUseCase;
        this.getMessageHistoryUseCase = getMessageHistoryUseCase;
    }

    public ChatMessage sendMessage(SendMessageCommand command) {
        return sendMessageUseCase.execute(command);
    }

    public ChatGroup createGroup(CreateGroupCommand command) {
        return createGroupUseCase.execute(command);
    }

    public void addMember(AddMemberCommand command) {
        addMemberUseCase.execute(command);
    }

    public void removeMember(UUID requesterId, UUID groupId, UUID targetUserId) {
        removeMemberUseCase.execute(requesterId, groupId, targetUserId);
    }

    public UserResponse registerUser(RegisterUserCommand command) {
        return registerUserUseCase.execute(command);
    }

    public PageResponse<MessageResponse> getMessageHistory(UUID groupId, UUID requesterId, int page, int size) {
        return getMessageHistoryUseCase.execute(groupId, requesterId, page, size);
    }
}
