package com.streamchat.chat.infrastructure.websocket;

import com.streamchat.chat.application.ChatFacade;
import com.streamchat.chat.application.dto.SendMessageCommand;
import com.streamchat.chat.application.dto.request.SendMessageRequest;
import com.streamchat.chat.infrastructure.security.AuthenticatedUser;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

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
        AuthenticatedUser user = extractAuthenticatedUser(principal);
        facade.sendMessage(new SendMessageCommand(user.userId(), groupId, payload.content()));
    }

    private AuthenticatedUser extractAuthenticatedUser(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new IllegalStateException("Usuario no autenticado en el canal WebSocket");
    }
}
