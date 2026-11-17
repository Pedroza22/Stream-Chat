package com.streamchat.chat.application.usecase;

import com.streamchat.chat.application.dto.SendMessageCommand;
import com.streamchat.chat.application.port.in.SendMessageUseCase;
import com.streamchat.chat.application.port.out.GroupRepository;
import com.streamchat.chat.application.port.out.MessageEventPublisher;
import com.streamchat.chat.application.port.out.MessageRepository;
import com.streamchat.chat.domain.exception.GroupNotFoundException;
import com.streamchat.chat.domain.model.ChatMessage;
import com.streamchat.chat.domain.valueobject.GroupId;
import com.streamchat.chat.domain.valueobject.UserId;
import com.streamchat.contracts.MessageCreatedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
@Transactional
public class SendMessageUseCaseImpl implements SendMessageUseCase {

    private final GroupRepository groupRepository;
    private final MessageRepository messageRepository;
    private final MessageEventPublisher messageEventPublisher;
    private final Clock clock;

    public SendMessageUseCaseImpl(GroupRepository groupRepository,
                                  MessageRepository messageRepository,
                                  MessageEventPublisher messageEventPublisher,
                                  Clock clock) {
        this.groupRepository = groupRepository;
        this.messageRepository = messageRepository;
        this.messageEventPublisher = messageEventPublisher;
        this.clock = clock;
    }

    @Override
    public ChatMessage execute(SendMessageCommand cmd) {
        var group = groupRepository.findById(GroupId.of(cmd.groupId()))
                .orElseThrow(() -> new GroupNotFoundException(cmd.groupId()));
        ChatMessage msg = group.sendMessage(UserId.of(cmd.senderId()), cmd.content(), clock);
        messageRepository.save(msg);
        MessageCreatedEvent event = new MessageCreatedEvent(
                UUID.randomUUID(),
                "MESSAGE_CREATED",
                msg.getId(),
                group.getId(),
                msg.getSenderId(),
                msg.getContent(),
                msg.getCreatedAt(),
                UUID.randomUUID(),
                1);
        messageEventPublisher.publish(event);
        return msg;
    }
}
