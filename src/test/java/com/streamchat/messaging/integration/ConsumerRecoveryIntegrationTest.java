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
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.streamchat.messaging.rabbitmq.consumer.ProcessedEventRepository;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Prueba de integración: Recuperación del consumidor.
 *
 * Verifica que:
 * 1. Mensajes publicados mientras el consumidor está detenido quedan en cola.
 * 2. Al reiniciar el consumidor, procesa todos los mensajes pendientes.
 *
 * Evidencia para Sistemas Distribuidos: demuestra tolerancia a desconexión temporal.
 * Evidencia para SO (E5 — Spooling): RabbitMQ actúa como spooler; el productor
 * encola y sigue; el consumidor procesa a su ritmo cuando vuelve.
 */
@DisplayName("Recuperación del consumidor — integración")
class ConsumerRecoveryIntegrationTest extends MessagingIntegrationBase {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private RabbitListenerEndpointRegistry listenerRegistry;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private com.streamchat.chat.application.port.out.ClientNotifier clientNotifier;

    @BeforeEach
    void limpiar() {
        processedEventRepository.deleteAll();
        rabbitAdmin.purgeQueue(RabbitMqProperties.DEFAULT_MSG_QUEUE, true);
        // Asegurar que el consumidor esté activo al inicio de cada test
        listenerRegistry.getListenerContainers().forEach(c -> {
            if (!c.isRunning()) c.start();
        });
    }

    @Test
    @DisplayName("Mensajes encolados mientras el consumidor está parado se procesan al reiniciar")
    void mensajes_pendientes_se_procesan_al_reiniciar_consumidor() throws Exception {
        // PASO 1: Detener el consumidor
        listenerRegistry.getListenerContainers().forEach(c -> c.stop());

        // Verificar que está detenido
        boolean allStopped = listenerRegistry.getListenerContainers()
                .stream().noneMatch(c -> c.isRunning());
        assertThat(allStopped).isTrue();

        // PASO 2: Publicar 3 mensajes mientras el consumidor está parado
        int cantidad = 3;
        for (int i = 0; i < cantidad; i++) {
            rabbitTemplate.convertAndSend(
                    RabbitMqProperties.DEFAULT_EXCHANGE,
                    RabbitMqProperties.DEFAULT_MSG_ROUTING_KEY,
                    buildEvent("Mensaje pendiente " + i)
            );
        }

        // Verificar que los mensajes quedaron en cola (spooling)
        await().atMost(3, SECONDS).untilAsserted(() -> {
            var queueInfo = rabbitAdmin.getQueueInfo(RabbitMqProperties.DEFAULT_MSG_QUEUE);
            assertThat(queueInfo.getMessageCount()).isEqualTo(cantidad);
        });

        // PASO 3: Reiniciar el consumidor
        listenerRegistry.getListenerContainers().forEach(c -> c.start());

        // PASO 4: El consumidor debe procesar todos los mensajes pendientes
        await().atMost(10, SECONDS).untilAsserted(() -> {
            var queueInfo = rabbitAdmin.getQueueInfo(RabbitMqProperties.DEFAULT_MSG_QUEUE);
            assertThat(queueInfo.getMessageCount()).isZero();
            verify(clientNotifier, atLeastOnce()).notifyGroup(any(GroupId.class), any(ServerEvent.class));
        });
    }

    @Test
    @DisplayName("Cola vacía al detener el consumidor — no se pierden mensajes ya procesados")
    void no_se_pierden_mensajes_ya_procesados() throws Exception {
        // Publicar un mensaje y esperar que se procese
        rabbitTemplate.convertAndSend(
                RabbitMqProperties.DEFAULT_EXCHANGE,
                RabbitMqProperties.DEFAULT_MSG_ROUTING_KEY,
                buildEvent("Mensaje procesado")
        );

        await().atMost(5, SECONDS).untilAsserted(() ->
                verify(clientNotifier, atLeastOnce()).notifyGroup(any(GroupId.class), any(ServerEvent.class))
        );

        // Detener el consumidor
        listenerRegistry.getListenerContainers().forEach(c -> c.stop());

        // La cola debe estar vacía (el mensaje fue ACKed antes de detener)
        var queueInfo = rabbitAdmin.getQueueInfo(RabbitMqProperties.DEFAULT_MSG_QUEUE);
        assertThat(queueInfo.getMessageCount()).isZero();
        assertThat(queueInfo.getConsumerCount()).isZero();
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
