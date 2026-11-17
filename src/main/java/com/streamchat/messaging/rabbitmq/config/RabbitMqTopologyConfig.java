package com.streamchat.messaging.rabbitmq.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Declara la topología completa de RabbitMQ:
 *
 *  ┌─────────────────────────────────────────────────────────────────┐
 *  │  Flujo principal                                                │
 *  │                                                                 │
 *  │  Publisher → [streamchat.events] ──message.created──►          │
 *  │              (topic exchange)        [streamchat.messages.created] │
 *  │                                           │ NACK / rechazo      │
 *  │                                           ▼                     │
 *  │              [streamchat.events.retry]  (TTL 10 s)              │
 *  │               (direct exchange)  ──► [streamchat.messages.retry]│
 *  │                                           │ TTL expirado        │
 *  │                                           ▼ (dead-letter)       │
 *  │                      vuelve a [streamchat.events] ──► cola      │
 *  │                      (hasta max-retries, luego DLQ)             │
 *  │                                                                 │
 *  │  DLQ final: [streamchat.messages.created.dlq]                   │
 *  └─────────────────────────────────────────────────────────────────┘
 *
 * Patrón: las colas de reintento usan TTL + dead-letter hacia el exchange
 * principal para re-enrutar el mensaje después de una espera, evitando
 * bucles de reintento inmediatos que saturan el broker.
 */
@Configuration
public class RabbitMqTopologyConfig {

    // ── Exchanges ────────────────────────────────────────────────────

    /** Exchange principal: recibe todos los eventos del dominio. */
    @Bean
    TopicExchange streamchatEventsExchange() {
        return ExchangeBuilder
                .topicExchange(RabbitMqProperties.DEFAULT_EXCHANGE)
                .durable(true)
                .build();
    }

    /** Exchange de reintento: recibe mensajes rechazados, aplica TTL y los devuelve al principal. */
    @Bean
    DirectExchange streamchatRetryExchange() {
        return ExchangeBuilder
                .directExchange(RabbitMqProperties.DEFAULT_RETRY_EXCHANGE)
                .durable(true)
                .build();
    }

    /** Exchange destino de los mensajes que agotaron reintentos (DLX del exchange de reintento). */
    @Bean
    DirectExchange streamchatDlxExchange() {
        return ExchangeBuilder
                .directExchange(RabbitMqProperties.DEFAULT_EXCHANGE + ".dlx")
                .durable(true)
                .build();
    }

    // ── Cola principal ───────────────────────────────────────────────

    /**
     * Cola principal de mensajes creados.
     * - dead-letter-exchange → exchange de reintento si el consumidor rechaza (NACK sin requeue).
     * - x-delivery-limit → máximo de reintentos antes de enviar a la DLQ final.
     */
    @Bean
    Queue messageCreatedQueue() {
        return QueueBuilder
                .durable(RabbitMqProperties.DEFAULT_MSG_QUEUE)
                .withArguments(Map.of(
                        "x-dead-letter-exchange",    RabbitMqProperties.DEFAULT_RETRY_EXCHANGE,
                        "x-dead-letter-routing-key", RabbitMqProperties.DEFAULT_MSG_QUEUE,
                        "x-delivery-limit",          3     // máximo 3 entregas antes de la DLQ
                ))
                .build();
    }

    @Bean
    Binding messageCreatedBinding(Queue messageCreatedQueue,
                                  TopicExchange streamchatEventsExchange) {
        return BindingBuilder
                .bind(messageCreatedQueue)
                .to(streamchatEventsExchange)
                .with(RabbitMqProperties.DEFAULT_MSG_ROUTING_KEY);
    }

    // ── Cola de reintento (con TTL) ──────────────────────────────────

    /**
     * Cola de reintento con TTL de 10 segundos.
     * Al expirar el TTL, el mensaje vuelve al exchange principal con la routing key original,
     * por lo que se re-enruta a la cola principal para un nuevo intento.
     */
    @Bean
    Queue messageRetryQueue() {
        return QueueBuilder
                .durable(RabbitMqProperties.DEFAULT_MSG_QUEUE + ".retry")
                .withArguments(Map.of(
                        "x-message-ttl",             RabbitMqProperties.DEFAULT_RETRY_TTL_MS,
                        "x-dead-letter-exchange",    RabbitMqProperties.DEFAULT_EXCHANGE,
                        "x-dead-letter-routing-key", RabbitMqProperties.DEFAULT_MSG_ROUTING_KEY
                ))
                .build();
    }

    @Bean
    Binding messageRetryBinding(Queue messageRetryQueue,
                                DirectExchange streamchatRetryExchange) {
        return BindingBuilder
                .bind(messageRetryQueue)
                .to(streamchatRetryExchange)
                .with(RabbitMqProperties.DEFAULT_MSG_QUEUE);
    }

    // ── Dead Letter Queue final ──────────────────────────────────────

    /** Cola de mensajes muertos: recibe lo que agotó todos los reintentos. */
    @Bean
    Queue messageCreatedDlq() {
        return QueueBuilder
                .durable(RabbitMqProperties.DEFAULT_MSG_DLQ)
                .build();
    }

    @Bean
    Binding messageCreatedDlqBinding(Queue messageCreatedDlq,
                                     DirectExchange streamchatDlxExchange) {
        return BindingBuilder
                .bind(messageCreatedDlq)
                .to(streamchatDlxExchange)
                .with(RabbitMqProperties.DEFAULT_MSG_DLQ);
    }
}
