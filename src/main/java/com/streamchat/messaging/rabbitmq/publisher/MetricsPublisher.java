package com.streamchat.messaging.rabbitmq.publisher;

import com.streamchat.chat.application.port.out.MessageEventPublisher;
import com.streamchat.contracts.MessageCreatedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Decorador (patrón Decorator) que añade métricas Micrometer al publicador.
 *
 * Métricas expuestas (accesibles desde /actuator/metrics):
 * - messaging.published.total       — contador de publicaciones exitosas
 * - messaging.published.failed      — contador de publicaciones fallidas
 * - messaging.publish.duration      — timer de latencia de publicación
 */
public class MetricsPublisher implements MessageEventPublisher {

    private final MessageEventPublisher delegate;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Timer publishTimer;

    public MetricsPublisher(MessageEventPublisher delegate, MeterRegistry registry) {
        this.delegate       = delegate;
        this.successCounter = Counter.builder("messaging.published.total")
                .description("Total de eventos de mensaje publicados exitosamente")
                .register(registry);
        this.failureCounter = Counter.builder("messaging.published.failed")
                .description("Total de eventos de mensaje que fallaron al publicarse")
                .register(registry);
        this.publishTimer   = Timer.builder("messaging.publish.duration")
                .description("Latencia de publicación de eventos de mensaje")
                .register(registry);
    }

    @Override
    public void publish(MessageCreatedEvent event) {
        publishTimer.record(() -> {
            try {
                delegate.publish(event);
                successCounter.increment();
            } catch (Exception e) {
                failureCounter.increment();
                throw e;
            }
        });
    }
}
