package com.streamchat;

import com.streamchat.chat.application.port.out.ClientNotifier;
import com.streamchat.chat.domain.valueobject.GroupId;
import com.streamchat.contracts.ServerEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class FakeClientNotifier implements ClientNotifier {

    private final List<Map.Entry<GroupId, ServerEvent>> notifications = new ArrayList<>();

    @Override
    public void notifyGroup(GroupId groupId, ServerEvent event) {
        notifications.add(Map.entry(groupId, event));
    }

    public List<Map.Entry<GroupId, ServerEvent>> getNotifications() {
        return Collections.unmodifiableList(notifications);
    }
}
