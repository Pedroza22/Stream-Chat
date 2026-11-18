package com.streamchat.presence.application.port.out;

import com.streamchat.contracts.UserActivityEvent;

/**
 * Puerto de salida: publica eventos de actividad (TYPING_STARTED / TYPING_STOPPED).
 * Lo define P3 (quien lo usa); P2 implementa el adaptador Kafka.
 */
public interface ActivityEventPublisher {
    void publish(UserActivityEvent event);
}
