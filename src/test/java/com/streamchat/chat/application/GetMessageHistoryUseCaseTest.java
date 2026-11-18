package com.streamchat.chat.application;

import com.streamchat.chat.application.dto.response.MessageResponse;
import com.streamchat.chat.application.dto.response.PageResponse;
import com.streamchat.chat.application.port.out.GroupRepository;
import com.streamchat.chat.application.port.out.MessageRepository;
import com.streamchat.chat.application.usecase.GetMessageHistoryUseCaseImpl;
import com.streamchat.chat.domain.exception.NotGroupMemberException;
import com.streamchat.chat.domain.model.ChatGroup;
import com.streamchat.chat.domain.model.ChatMessage;
import com.streamchat.chat.domain.valueobject.GroupId;
import com.streamchat.chat.domain.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetMessageHistoryUseCaseTest {

    private GroupRepository groupRepository;
    private MessageRepository messageRepository;
    private Clock clock;
    private GetMessageHistoryUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        groupRepository = mock(GroupRepository.class);
        messageRepository = mock(MessageRepository.class);
        clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);
        useCase = new GetMessageHistoryUseCaseImpl(groupRepository, messageRepository);
    }

    @Test
    void rejected_whenRequesterNotMember() {
        UUID groupId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        ChatGroup group = ChatGroup.create(creatorId, "Test", clock);
        when(groupRepository.findById(GroupId.of(groupId))).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> useCase.execute(groupId, requesterId, 0, 10))
                .isInstanceOf(NotGroupMemberException.class);

        verify(messageRepository, never()).findByGroupId(any(), anyInt(), anyInt());
    }

    @Test
    void returnsPaginatedHistory_whenRequesterIsMember() {
        UUID groupId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        ChatGroup group = ChatGroup.create(creatorId, "Test", clock);
        group.addMember(UserId.of(requesterId), clock);
        when(groupRepository.findById(GroupId.of(groupId))).thenReturn(Optional.of(group));

        ChatMessage msg1 = ChatMessage.create(groupId, requesterId, "Hola", clock);
        ChatMessage msg2 = ChatMessage.create(groupId, requesterId, "Mundo", clock);
        when(messageRepository.findByGroupId(GroupId.of(groupId), 0, 10))
                .thenReturn(List.of(msg1, msg2));
        when(messageRepository.countByGroupId(GroupId.of(groupId))).thenReturn(2L);

        PageResponse<MessageResponse> result = useCase.execute(groupId, requesterId, 0, 10);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).content()).isEqualTo("Hola");
        assertThat(result.content().get(1).content()).isEqualTo("Mundo");
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.totalElements()).isEqualTo(2L);
        assertThat(result.totalPages()).isEqualTo(1);
    }
}
