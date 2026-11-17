package com.streamchat.chat.infrastructure.rest;

import com.streamchat.chat.application.ChatFacade;
import com.streamchat.chat.application.dto.SendMessageCommand;
import com.streamchat.chat.application.dto.request.SendMessageRequest;
import com.streamchat.chat.application.dto.response.MessageResponse;
import com.streamchat.chat.application.dto.response.PageResponse;
import com.streamchat.chat.domain.model.ChatMessage;
import com.streamchat.chat.infrastructure.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/messages")
public class MessageController {

    private final ChatFacade facade;

    public MessageController(ChatFacade facade) {
        this.facade = facade;
    }

    @PostMapping
    public ResponseEntity<MessageResponse> sendMessage(@PathVariable UUID groupId,
                                                       @Valid @RequestBody SendMessageRequest req) {
        AuthenticatedUser principal = (AuthenticatedUser) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        UUID senderId = principal.userId();
        ChatMessage msg = facade.sendMessage(new SendMessageCommand(senderId, groupId, req.content()));
        MessageResponse response = new MessageResponse(msg.getId(), msg.getGroupId(),
                msg.getSenderId(), msg.getContent(), msg.getCreatedAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<MessageResponse>> getMessages(
            @PathVariable UUID groupId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        AuthenticatedUser principal = (AuthenticatedUser) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        UUID requesterId = principal.userId();
        PageResponse<MessageResponse> result = facade.getMessageHistory(groupId, requesterId, page, size);
        return ResponseEntity.ok(result);
    }
}
