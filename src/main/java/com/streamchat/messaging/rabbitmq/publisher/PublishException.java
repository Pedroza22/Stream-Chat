package com.streamchat.messaging.rabbitmq.publisher;

/**
 * Excepción lanzada cuando RabbitMQ no confirma (NACK o timeout) la publicación de un evento.
 * El relay de outbox la captura para marcar el evento como FAILED y reintentar más tarde.
 */
public class PublishException extends RuntimeException {

    public PublishException(String message) {
        super(message);
    }

    public PublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
