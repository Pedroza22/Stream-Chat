package com.streamchat.chat.infrastructure.websocket;

import com.streamchat.chat.infrastructure.security.AuthenticatedUser;
import com.streamchat.chat.infrastructure.security.JwtTokenProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    public WebSocketAuthInterceptor(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = accessor.getFirstNativeHeader("passcode");
            if (token == null) {
                String auth = accessor.getFirstNativeHeader("Authorization");
                if (auth != null && auth.startsWith("Bearer ")) {
                    token = auth.substring(7);
                }
            }
            if (token == null || !jwtTokenProvider.validateToken(token)) {
                throw new MessagingException("Invalid token");
            }
            var userId = jwtTokenProvider.extractUserId(token);
            var username = jwtTokenProvider.extractUsername(token);
            var user = new AuthenticatedUser(userId, username);
            accessor.setUser(new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList()));
        }
        return message;
    }
}
