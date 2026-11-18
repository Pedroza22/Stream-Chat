package com.streamchat.chat.application;

import com.streamchat.chat.application.dto.response.MessageResponse;
import com.streamchat.chat.application.dto.response.PageResponse;
import com.streamchat.chat.application.port.out.GroupRepository;
import com.streamchat.chat.application.port.out.MessageRepository;
import com.streamchat.chat.application.usecase.GetMessageHistoryUseCaseImpl;
import com.streamchat.chat.domain.exception.GroupNotFoundException;
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
import static org.mockito.Mockito.*;

class GetMessageHistoryUseCaseTest {

    private GroupRepository groupRepository;
    private MessageRepository messageRepository;
    private Clock clock;
    private GetMessageHistoryUseCaseImpl useCase;

    private UUID creatorId;
    private UUID groupId;
    private ChatGroup group;

    @BeforeEach
    void setUp() {
        groupRepository  = mock(GroupRepository.class);
        messageRepository = mock(MessageRepository.class);
        clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);
        useCase = new GetMessageHistoryUseCaseImpl(groupRepository, messageRepository);

        creatorId = UUID.randomUUID();
        groupId   = UUID.randomUUID();

        group = ChatGroup.create(creatorId, "Test Group", clock);
        group.addMember(UserId.of(creatorId), clock);

        when(groupRepository.findById(GroupId.of(groupId))).thenReturn(Optional.of(group));
    }

    @Test
    void returns_messages_paged_when_requester_is_member() {
        ChatMessage msg1 = ChatMessage.create(groupId, creatorId, "Hola", clock);
        ChatMessage msg2 = ChatMessage.create(groupId, creatorId, "Mundo", clock);

        when(messageRepository.findByGroupId(GroupId.of(groupId), 0, 10))
                .thenReturn(List.of(msg1, msg2));
        when(messageRepository.countByGroupId(GroupId.of(groupId))).thenReturn(2L);

        PageResponse<MessageResponse> result = useCase.execute(groupId, creatorId, 0, 10);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).content()).isEqualTo("Hola");
        assertThat(result.content().get(1).content()).isEqualTo("Mundo");
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.totalPages()).isEqualTo(1);
    }

    @Test
    void returns_empty_page_when_no_messages() {
        when(messageRepository.findByGroupId(GroupId.of(groupId), 0, 10))
                .thenReturn(List.of());
        when(messageRepository.countByGroupId(GroupId.of(groupId))).thenReturn(0L);

        PageResponse<MessageResponse> result = useCase.execute(groupId, creatorId, 0, 10);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
    }

    @Test
    void calculates_total_pages_correctly() {
        when(messageRepository.findByGroupId(GroupId.of(groupId), 0, 5))
                .thenReturn(List.of());
        when(messageRepository.countByGroupId(GroupId.of(groupId))).thenReturn(11L);

        PageResponse<MessageResponse> result = useCase.execute(groupId, creatorId, 0, 5);

        // 11 mensajes, página de 5 → 3 páginas (ceil(11/5) = 3)
        assertThat(result.totalPages()).isEqualTo(3);
    }

    @Test
    void throws_when_group_not_found() {
        UUID unknownGroupId = UUID.randomUUID();
        when(groupRepository.findById(GroupId.of(unknownGroupId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(unknownGroupId, creatorId, 0, 10))
                .isInstanceOf(GroupNotFoundException.class);

        verifyNoInteractions(messageRepository);
    }

    @Test
    void throws_when_requester_is_not_member() {
        UUID outsiderId = UUID.randomUUID();

        assertThatThrownBy(() -> useCase.execute(groupId, outsiderId, 0, 10))
                .isInstanceOf(NotGroupMemberException.class);

        verifyNoInteractions(messageRepository);
    }
}
