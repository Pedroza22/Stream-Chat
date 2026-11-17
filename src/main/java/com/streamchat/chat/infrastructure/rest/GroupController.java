package com.streamchat.chat.infrastructure.rest;

import com.streamchat.chat.application.ChatFacade;
import com.streamchat.chat.application.dto.AddMemberCommand;
import com.streamchat.chat.application.dto.CreateGroupCommand;
import com.streamchat.chat.application.dto.request.AddMemberRequest;
import com.streamchat.chat.application.dto.request.CreateGroupRequest;
import com.streamchat.chat.domain.model.ChatGroup;
import com.streamchat.chat.infrastructure.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/groups")
public class GroupController {

    private final ChatFacade facade;

    public GroupController(ChatFacade facade) {
        this.facade = facade;
    }

    @PostMapping
    public ResponseEntity<ChatGroup> createGroup(@Valid @RequestBody CreateGroupRequest req) {
        AuthenticatedUser principal = (AuthenticatedUser) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        UUID userId = principal.userId();
        ChatGroup group = facade.createGroup(new CreateGroupCommand(userId, req.name()));
        return ResponseEntity.status(HttpStatus.CREATED).body(group);
    }

    @PostMapping("/{groupId}/members")
    public ResponseEntity<Void> addMember(@PathVariable UUID groupId,
                                          @Valid @RequestBody AddMemberRequest req) {
        AuthenticatedUser principal = (AuthenticatedUser) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        UUID requesterId = principal.userId();
        facade.addMember(new AddMemberCommand(requesterId, groupId, req.targetUserId()));
        return ResponseEntity.ok().build();
    }
}
