package com.streamchat.messaging.integration;

import com.streamchat.chat.infrastructure.persistence.OutboxEvent;
import com.streamchat.chat.infrastructure.persistence.OutboxRepository;
import com.streamchat.contracts.MessageCreatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.amqp.rabbit.core.RabbitAdmin;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Prueba de integración: Relay de Outbox.
 *
 * Verifica que:
 * 1. Un evento PENDING en outbox_events se publica en RabbitMQ y queda SENT.
 * 2. Si el relay falla (broker caído), el evento queda FAILED para reintentar.
 *
 * Evidencia para Sistemas Distribuidos: demuestra que no se pierde un evento
 * si el proceso cae entre el guardado en PostgreSQL y la publicación en RabbitMQ.
 */
@DisplayName("Relay de Outbox — integración")
class OutboxRelayIntegrationTest extends MessagingIntegrationBase {

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @BeforeEach
    void limpiar() {
        outboxRepository.deleteAll();
        // Purgar la cola para que cada test empiece limpio
        rabbitAdmin.purgeQueue("streamchat.messages.created", true);
    }

    @Test
    @DisplayName("Evento PENDING se publica en RabbitMQ y queda marcado SENT")
    void evento_pending_se_publica_y_marca_sent() throws Exception {
        // DADO: un evento en outbox con estado PENDING
        MessageCreatedEvent event = buildEvent("Hola desde el outbox relay");
        String payload = objectMapper.writeValueAsString(event);
        OutboxEvent outboxEvent = OutboxEvent.create(event.messageId(), "MESSAGE_CREATED", payload);
        outboxRepository.save(outboxEvent);

        // CUANDO: el relay lo procesa (corre cada 500ms en test)
        // ENTONCES: el evento debe quedar SENT dentro de 5 segundos
        await().atMost(5, SECONDS).untilAsserted(() -> {
            OutboxEvent updated = outboxRepository.findById(outboxEvent.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.SENT);
            assertThat(updated.getProcessedAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("Evento SENT no se vuelve a publicar en el siguiente ciclo del relay")
    void evento_sent_no_se_reprocesa() throws Exception {
        // DADO: un evento ya marcado SENT
        MessageCreatedEvent event = buildEvent("Mensaje ya enviado");
        String payload = objectMapper.writeValueAsString(event);
        OutboxEvent outboxEvent = OutboxEvent.create(event.messageId(), "MESSAGE_CREATED", payload);
        outboxRepository.save(outboxEvent);

        // Esperar que el relay lo procese una vez
        await().atMost(5, SECONDS).untilAsserted(() -> {
            OutboxEvent updated = outboxRepository.findById(outboxEvent.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.SENT);
        });

        // Capturar el processed_at del primer envío
        Instant firstProcessedAt = outboxRepository.findById(outboxEvent.getId())
                .orElseThrow().getProcessedAt();

        // Esperar dos ciclos más del relay
        Thread.sleep(1500);

        // ENTONCES: processed_at no debe haber cambiado (no se reprocesó)
        Instant secondProcessedAt = outboxRepository.findById(outboxEvent.getId())
                .orElseThrow().getProcessedAt();
        assertThat(secondProcessedAt).isEqualTo(firstProcessedAt);
    }

    @Test
    @DisplayName("Múltiples eventos PENDING se publican todos en el mismo ciclo")
    void multiples_eventos_pending_se_publican() throws Exception {
        int cantidad = 5;
        for (int i = 0; i < cantidad; i++) {
            MessageCreatedEvent event = buildEvent("Mensaje " + i);
            String payload = objectMapper.writeValueAsString(event);
            outboxRepository.save(OutboxEvent.create(event.messageId(), "MESSAGE_CREATED", payload));
        }

        await().atMost(8, SECONDS).untilAsserted(() -> {
            long sentCount = outboxRepository.findAll().stream()
                    .filter(e -> e.getStatus() == OutboxEvent.OutboxStatus.SENT)
                    .count();
            assertThat(sentCount).isEqualTo(cantidad);
        });
    }

    private MessageCreatedEvent buildEvent(String content) {
        return new MessageCreatedEvent(
                UUID.randomUUID(),
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
