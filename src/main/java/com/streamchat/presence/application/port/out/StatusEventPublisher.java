package com.streamchat.presence.application.port.out;

import com.streamchat.contracts.ServerEvent;

/**
 * Puerto de salida: publica eventos de estado de typing hacia el
 * Fanout Exchange de RabbitMQ ("streamchat.status.updates").
 *
 * El adaptador de infraestructura (RabbitMqStatusEventPublisher)
 * implementa este puerto, desacoplando el dominio de presencia
 * del broker concreto.
 */
public interface StatusEventPublisher {
    void publish(ServerEvent event);
}
