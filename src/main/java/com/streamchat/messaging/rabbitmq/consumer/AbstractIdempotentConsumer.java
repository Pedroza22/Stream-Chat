package com.streamchat.messaging.rabbitmq.consumer;

import com.rabbitmq.client.Channel;
import com.streamchat.messaging.rabbitmq.consumer.ProcessedEventRepository.ProcessedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.UUID;

/**
 * Clase base para consumidores idempotentes (patrón Template Method).
 *
 * Flujo por mensaje:
 *  1. Intentar insertar (eventId, consumerName) en processed_events.
 *  2. Si ya existe → duplicado → ACK sin procesar.
 *  3. Si no existe → llamar a doProcess() (método gancho, implementado por la subclase).
 *  4. Si doProcess() lanza excepción → NACK sin requeue (va a la cola de reintento con TTL).
 *  5. Si todo va bien → ACK.
 *
 * Garantía: exactly-once semántico para los efectos de negocio, con entrega at-least-once del broker.
 */
public abstract class AbstractIdempotentConsumer<T> {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final ProcessedEventRepository processedEventRepository;

    protected AbstractIdempotentConsumer(ProcessedEventRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    /** Nombre único de este consumidor, usado como discriminador en processed_events. */
    protected abstract String consumerName();

    /** UUID del evento extraído del mensaje. */
    protected abstract UUID extractEventId(T message);

    /** Lógica de negocio real. Lanzar excepción provoca NACK. */
    protected abstract void doProcess(T message);

    @Transactional
    public void handle(T message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        UUID eventId = extractEventId(message);

        try {
            // Paso 1: registrar como procesado (idempotencia)
            processedEventRepository.saveAndFlush(ProcessedEvent.of(eventId, consumerName()));

        } catch (DataIntegrityViolationException duplicate) {
            // Paso 2: ya procesado — ACK sin efectos
            log.info("Evento duplicado ignorado eventId={} consumer={}", eventId, consumerName());
            ack(channel, deliveryTag);
            return;
        }

        try {
            // Paso 3: procesar
            doProcess(message);
            ack(channel, deliveryTag);
            log.debug("Evento procesado eventId={} consumer={}", eventId, consumerName());

        } catch (Exception e) {
            // Paso 4: fallo en el procesamiento — NACK, va a la cola de reintento
            log.error("Error procesando eventId={} consumer={} — NACK sin requeue",
                    eventId, consumerName(), e);
            nack(channel, deliveryTag);
        }
    }

    private void ack(Channel channel, long deliveryTag) {
        try {
            channel.basicAck(deliveryTag, false);
        } catch (IOException e) {
            log.error("Error enviando ACK deliveryTag={}", deliveryTag, e);
        }
    }

    private void nack(Channel channel, long deliveryTag) {
        try {
            channel.basicNack(deliveryTag, false, false); // requeue=false → va al DLX
        } catch (IOException e) {
            log.error("Error enviando NACK deliveryTag={}", deliveryTag, e);
        }
    }
}
