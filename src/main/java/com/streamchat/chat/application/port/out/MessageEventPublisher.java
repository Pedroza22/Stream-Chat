package com.streamchat.chat.application.port.out;

import com.streamchat.contracts.MessageCreatedEvent;

public interface MessageEventPublisher {
    void publish(MessageCreatedEvent event);
}
