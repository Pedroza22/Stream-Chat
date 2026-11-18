package com.streamchat.chat.infrastructure.websocket;

import com.streamchat.chat.application.ChatFacade;
import com.streamchat.chat.application.dto.SendMessageCommand;
import com.streamchat.chat.application.dto.request.SendMessageRequest;
import com.streamchat.chat.infrastructure.security.AuthenticatedUser;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.security.Principal;
import java.util.UUID;

@Controller
public class ChatWebSocketController {

    private final ChatFacade facade;

    public ChatWebSocketController(ChatFacade facade) {
        this.facade = facade;
    }

    @MessageMapping("/groups/{groupId}/messages")
    public void sendMessage(@DestinationVariable UUID groupId,
                            SendMessageRequest payload,
                            Principal principal) {
        UUID userId = extractUserId(principal);
        facade.sendMessage(new SendMessageCommand(userId, groupId, payload.content()));
    }

    private UUID extractUserId(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof AuthenticatedUser user) {
            return user.userId();
        }
        throw new IllegalStateException("Usuario no autenticado en el canal WebSocket");
    }
}
