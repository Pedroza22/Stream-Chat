package com.streamchat.chat.infrastructure.websocket;

import com.streamchat.chat.application.ChatFacade;
import com.streamchat.chat.application.dto.SendMessageCommand;
import com.streamchat.chat.application.dto.request.SendMessageRequest;
import com.streamchat.chat.infrastructure.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.security.Principal;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ChatWebSocketControllerTest {

    private ChatFacade facade;
    private ChatWebSocketController controller;

    @BeforeEach
    void setUp() {
        facade = mock(ChatFacade.class);
        controller = new ChatWebSocketController(facade);
    }

    @Test
    void sendMessage_callsFacade_whenPrincipalIsAuthenticatedUser() {
        UUID userId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, "alice");
        Principal principal = new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList());

        controller.sendMessage(groupId, new SendMessageRequest("hello"), principal);

        ArgumentCaptor<SendMessageCommand> captor = ArgumentCaptor.forClass(SendMessageCommand.class);
        verify(facade).sendMessage(captor.capture());
        SendMessageCommand cmd = captor.getValue();
        assertThat(cmd.senderId()).isEqualTo(userId);
        assertThat(cmd.groupId()).isEqualTo(groupId);
        assertThat(cmd.content()).isEqualTo("hello");
    }

    @Test
    void sendMessage_throws_whenPrincipalIsNotAnAuthenticationToken() {
        UUID groupId = UUID.randomUUID();
        Principal principal = () -> "not-a-token";

        assertThatThrownBy(() -> controller.sendMessage(groupId, new SendMessageRequest("hello"), principal))
                .isInstanceOf(IllegalStateException.class);

        verify(facade, never()).sendMessage(any());
    }

    @Test
    void sendMessage_throws_whenTokenPrincipalIsNotAuthenticatedUser() {
        UUID groupId = UUID.randomUUID();
        // El principal del token es un String, no un AuthenticatedUser
        Principal principal = new UsernamePasswordAuthenticationToken("some-principal", null);

        assertThatThrownBy(() -> controller.sendMessage(groupId, new SendMessageRequest("hello"), principal))
                .isInstanceOf(IllegalStateException.class);

        verify(facade, never()).sendMessage(any());
    }
}
