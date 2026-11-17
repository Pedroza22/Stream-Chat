package com.streamchat.chat.infrastructure.websocket;

import com.streamchat.chat.application.port.out.ClientNotifier;
import com.streamchat.chat.domain.valueobject.GroupId;
import com.streamchat.contracts.ServerEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class WebSocketClientNotifier implements ClientNotifier {

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketClientNotifier(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void notifyGroup(GroupId groupId, ServerEvent event) {
        messagingTemplate.convertAndSend("/topic/groups/" + groupId.value(), event);
    }
}
