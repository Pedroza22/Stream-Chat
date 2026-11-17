package com.streamchat.chat.application.port.out;

import com.streamchat.chat.domain.valueobject.GroupId;
import com.streamchat.contracts.ServerEvent;

public interface ClientNotifier {
    void notifyGroup(GroupId groupId, ServerEvent event);
}
