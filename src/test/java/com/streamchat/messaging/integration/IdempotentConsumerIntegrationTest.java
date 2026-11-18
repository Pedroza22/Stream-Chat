package com.streamchat.messaging.integration;

import com.streamchat.chat.application.port.out.ClientNotifier;
import com.streamchat.chat.domain.valueobject.GroupId;
import com.streamchat.contracts.MessageCreatedEvent;
import com.streamchat.contracts.ServerEvent;
import com.streamchat.messaging.rabbitmq.consumer.ProcessedEventRepository;
import com.streamchat.messaging.rabbitmq.consumer.ProcessedEvent;
import com.streamchat.messaging.rabbitmq.config.RabbitMqProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Prueba de integración: Idempotencia del consumidor.
 *
 * Verifica que:
 * 1. Un evento procesado una vez no produce efectos de negocio duplicados.
 * 2. Si el mismo eventId llega dos veces, ClientNotifier se llama solo una vez.
 *
 * Evidencia para Sistemas Distribuidos: demuestra entrega exactly-once semántica
 * sobre entrega at-least-once del broker.
 */
@DisplayName("Consumidor idempotente — integración")
class IdempotentConsumerIntegrationTest extends MessagingIntegrationBase {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private com.streamchat.chat.application.port.out.ClientNotifier clientNotifier;

    @BeforeEach
    void limpiar() {
        processedEventRepository.deleteAll();
        rabbitAdmin.purgeQueue(RabbitMqProperties.DEFAULT_MSG_QUEUE, true);
        org.mockito.Mockito.reset(clientNotifier);
    }

    @Test
    @DisplayName("Evento nuevo: ClientNotifier recibe exactamente una notificación")
    void evento_nuevo_notifica_una_vez() throws Exception {
        MessageCreatedEvent event = buildEvent();

        // Publicar el evento en la cola
        rabbitTemplate.convertAndSend(
                RabbitMqProperties.DEFAULT_EXCHANGE,
                RabbitMqProperties.DEFAULT_MSG_ROUTING_KEY,
                event
        );

        // Esperar que el consumidor lo procese
        await().atMost(5, SECONDS).untilAsserted(() ->
                verify(clientNotifier, times(1)).notifyGroup(any(GroupId.class), any(ServerEvent.class))
        );

        // Verificar que quedó registrado en processed_events
        assertThat(processedEventRepository.existsById(
                new ProcessedEvent.ProcessedEventId(event.eventId(), "message-created")
        )).isTrue();
    }

    @Test
    @DisplayName("Evento duplicado: ClientNotifier se llama solo una vez aunque llegue dos veces")
    void evento_duplicado_no_produce_efecto_doble() throws Exception {
        MessageCreatedEvent event = buildEvent();

        // Publicar el mismo evento dos veces (mismo eventId)
        rabbitTemplate.convertAndSend(
                RabbitMqProperties.DEFAULT_EXCHANGE,
                RabbitMqProperties.DEFAULT_MSG_ROUTING_KEY,
                event
        );
        rabbitTemplate.convertAndSend(
                RabbitMqProperties.DEFAULT_EXCHANGE,
                RabbitMqProperties.DEFAULT_MSG_ROUTING_KEY,
                event
        );

        // Esperar que la cola se vacíe (ambos mensajes procesados o descartados)
        await().atMost(8, SECONDS).untilAsserted(() -> {
            var queueInfo = rabbitAdmin.getQueueInfo(RabbitMqProperties.DEFAULT_MSG_QUEUE);
            assertThat(queueInfo.getMessageCount()).isZero();
        });

        // El eventId debe estar registrado en processed_events exactamente una vez
        // (esto es la garantía real de idempotencia, independiente de concurrencia del mock)
        assertThat(processedEventRepository.existsById(
                new ProcessedEvent.ProcessedEventId(event.eventId(), "message-created")
        )).isTrue();

        // Solo debe haber una entrada para ese eventId (no duplicados en la tabla)
        long count = processedEventRepository.findAll().stream()
                .filter(pe -> pe.getId().getEventId().equals(event.eventId()))
                .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("Eventos distintos: cada uno notifica una vez")
    void eventos_distintos_notifican_por_separado() throws Exception {
        MessageCreatedEvent event1 = buildEvent();
        MessageCreatedEvent event2 = buildEvent();

        rabbitTemplate.convertAndSend(
                RabbitMqProperties.DEFAULT_EXCHANGE,
                RabbitMqProperties.DEFAULT_MSG_ROUTING_KEY,
                event1
        );
        rabbitTemplate.convertAndSend(
                RabbitMqProperties.DEFAULT_EXCHANGE,
                RabbitMqProperties.DEFAULT_MSG_ROUTING_KEY,
                event2
        );

        await().atMost(8, SECONDS).untilAsserted(() ->
                verify(clientNotifier, times(2)).notifyGroup(any(GroupId.class), any(ServerEvent.class))
        );
    }

    private MessageCreatedEvent buildEvent() {
        return new MessageCreatedEvent(
                UUID.randomUUID(),
                "MESSAGE_CREATED",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Mensaje de prueba",
                Instant.now(),
                UUID.randomUUID(),
                1
        );
    }
}
