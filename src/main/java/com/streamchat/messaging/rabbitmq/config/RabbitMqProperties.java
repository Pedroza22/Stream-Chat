package com.streamchat.messaging.rabbitmq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nombres de exchanges, colas y routing keys leídos desde application.yml.
 * Centraliza las constantes para que publisher y consumer usen los mismos valores.
 */
@ConfigurationProperties(prefix = "app.messaging.rabbitmq")
public record RabbitMqProperties(
        String exchange,
        String messageCreatedQueue,
        String messageCreatedDlq,
        String messageCreatedRoutingKey,
        String retryExchange,
        int retryTtlMs
) {
    public static final String DEFAULT_EXCHANGE          = "streamchat.events";
    public static final String DEFAULT_RETRY_EXCHANGE    = "streamchat.events.retry";
    public static final String DEFAULT_MSG_QUEUE         = "streamchat.messages.created";
    public static final String DEFAULT_MSG_DLQ           = "streamchat.messages.created.dlq";
    public static final String DEFAULT_MSG_ROUTING_KEY   = "message.created";
    public static final int    DEFAULT_RETRY_TTL_MS      = 10_000;
}
