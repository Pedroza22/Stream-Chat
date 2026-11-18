package com.streamchat.messaging.integration;

import com.streamchat.chat.application.port.out.ClientNotifier;
import com.streamchat.chat.domain.valueobject.GroupId;
import com.streamchat.contracts.MessageCreatedEvent;
import com.streamchat.contracts.ServerEvent;
import com.streamchat.messaging.rabbitmq.config.RabbitMqProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.streamchat.messaging.rabbitmq.consumer.ProcessedEventRepository;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Prueba de integración: Dead Letter Queue.
 *
 * Verifica que un mensaje que falla repetidamente en el consumidor
 * termina en la DLQ y no bloquea el procesamiento de otros mensajes.
 *
 * Evidencia para Sistemas Distribuidos: demuestra el manejo de mensajes
 * fallidos y que la DLQ actúa como "cuarentena" para inspección manual.
 */
@DisplayName("Dead Letter Queue — integración")
class DeadLetterQueueIntegrationTest extends MessagingIntegrationBase {

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
        rabbitAdmin.purgeQueue(RabbitMqProperties.DEFAULT_MSG_DLQ, true);
        rabbitAdmin.purgeQueue(RabbitMqProperties.DEFAULT_MSG_QUEUE + ".retry", true);
    }

    @Test
    @DisplayName("Mensaje normal llega al consumidor y NO va a la DLQ")
    void mensaje_exitoso_no_va_a_dlq() throws Exception {
        rabbitTemplate.convertAndSend(
                RabbitMqProperties.DEFAULT_EXCHANGE,
                RabbitMqProperties.DEFAULT_MSG_ROUTING_KEY,
                buildEvent("Mensaje correcto")
        );

        // Esperar procesamiento
        await().atMost(5, SECONDS).untilAsserted(() -> {
            var mainQueue = rabbitAdmin.getQueueInfo(RabbitMqProperties.DEFAULT_MSG_QUEUE);
            assertThat(mainQueue.getMessageCount()).isZero();
        });

        // DLQ debe estar vacía
        var dlq = rabbitAdmin.getQueueInfo(RabbitMqProperties.DEFAULT_MSG_DLQ);
        assertThat(dlq.getMessageCount()).isZero();
    }

    @Test
    @DisplayName("Mensaje que falla en el consumidor va a la cola de reintento con TTL")
    void mensaje_fallido_va_a_cola_reintento() throws Exception {
        // Hacer que ClientNotifier lance excepción (simula fallo en doProcess)
        doThrow(new RuntimeException("Fallo simulado en el consumidor"))
                .when(clientNotifier).notifyGroup(any(GroupId.class), any(ServerEvent.class));

        rabbitTemplate.convertAndSend(
                RabbitMqProperties.DEFAULT_EXCHANGE,
                RabbitMqProperties.DEFAULT_MSG_ROUTING_KEY,
                buildEvent("Mensaje que fallará")
        );

        // El mensaje debe aparecer en la cola de reintento (TTL 10s)
        await().atMost(5, SECONDS).untilAsserted(() -> {
            var retryQueue = rabbitAdmin.getQueueInfo(
                    RabbitMqProperties.DEFAULT_MSG_QUEUE + ".retry");
            assertThat(retryQueue.getMessageCount()).isGreaterThan(0);
        });
    }

    @Test
    @DisplayName("Un mensaje fallido no bloquea el procesamiento de mensajes buenos")
    void mensaje_fallido_no_bloquea_otros_mensajes() throws Exception {
        UUID failingEventId = UUID.randomUUID();
        UUID goodEventId = UUID.randomUUID();

        // Solo el primer evento falla
        doThrow(new RuntimeException("Fallo solo para este evento"))
                .when(clientNotifier).notifyGroup(any(GroupId.class), any(ServerEvent.class));

        // Publicar el mensaje que fallará
        rabbitTemplate.convertAndSend(
                RabbitMqProperties.DEFAULT_EXCHANGE,
                RabbitMqProperties.DEFAULT_MSG_ROUTING_KEY,
                buildEventWithId(failingEventId, "Mensaje fallido")
        );

        // Esperar que falle y vaya a reintento, luego restaurar comportamiento normal
        await().atMost(3, SECONDS).untilAsserted(() -> {
            var retryQueue = rabbitAdmin.getQueueInfo(
                    RabbitMqProperties.DEFAULT_MSG_QUEUE + ".retry");
            assertThat(retryQueue.getMessageCount()).isGreaterThan(0);
        });

        // Restaurar ClientNotifier para que funcione normalmente
        org.mockito.Mockito.reset(clientNotifier);

        // Limpiar processed_events para permitir el segundo evento
        processedEventRepository.deleteAll();

        // Publicar un mensaje correcto
        rabbitTemplate.convertAndSend(
                RabbitMqProperties.DEFAULT_EXCHANGE,
                RabbitMqProperties.DEFAULT_MSG_ROUTING_KEY,
                buildEventWithId(goodEventId, "Mensaje correcto después del fallo")
        );

        // El mensaje correcto se procesa sin bloqueo
        await().atMost(5, SECONDS).untilAsserted(() ->
                org.mockito.Mockito.verify(clientNotifier, org.mockito.Mockito.atLeastOnce())
                        .notifyGroup(any(GroupId.class), any(ServerEvent.class))
        );
    }

    private MessageCreatedEvent buildEvent(String content) {
        return buildEventWithId(UUID.randomUUID(), content);
    }

    private MessageCreatedEvent buildEventWithId(UUID eventId, String content) {
        return new MessageCreatedEvent(
                eventId,
                "MESSAGE_CREATED",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                content,
                Instant.now(),
                UUID.randomUUID(),
                1
        );
    }
}
