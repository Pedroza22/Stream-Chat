package com.streamchat.messaging.publisher;

import com.streamchat.chat.application.port.out.MessageEventPublisher;
import com.streamchat.contracts.MessageCreatedEvent;
import com.streamchat.messaging.rabbitmq.publisher.LoggingPublisher;
import com.streamchat.messaging.rabbitmq.publisher.MetricsPublisher;
import com.streamchat.messaging.rabbitmq.publisher.PublishException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Pruebas de los decoradores LoggingPublisher y MetricsPublisher.
 *
 * Verifica LSP: cada decorador cumple el mismo contrato que el publicador real
 * — delega correctamente y propaga las excepciones sin tragarlas.
 */
class PublisherDecoratorsTest {

    private MessageEventPublisher delegate;
    private SimpleMeterRegistry registry;
    private MessageCreatedEvent event;

    @BeforeEach
    void setUp() {
        delegate = mock(MessageEventPublisher.class);
        registry = new SimpleMeterRegistry();
        event = new MessageCreatedEvent(
                UUID.randomUUID(), "MESSAGE_CREATED", UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "Hola",
                Instant.now(), UUID.randomUUID(), 1
        );
    }

    // ── LoggingPublisher ──────────────────────────────────────────────

    @Test
    void loggingPublisher_delegates_to_wrapped_publisher() {
        MessageEventPublisher logging = new LoggingPublisher(delegate);
        logging.publish(event);
        verify(delegate, times(1)).publish(event);
    }

    @Test
    void loggingPublisher_propagates_exception() {
        doThrow(new PublishException("broker down")).when(delegate).publish(any());
        MessageEventPublisher logging = new LoggingPublisher(delegate);

        assertThatThrownBy(() -> logging.publish(event))
                .isInstanceOf(PublishException.class)
                .hasMessageContaining("broker down");
    }

    // ── MetricsPublisher ─────────────────────────────────────────────

    @Test
    void metricsPublisher_increments_success_counter_on_ok() {
        MessageEventPublisher metrics = new MetricsPublisher(delegate, registry);
        metrics.publish(event);

        double count = registry.counter("messaging.published.total").count();
        assertThat(count).isEqualTo(1.0);
        assertThat(registry.counter("messaging.published.failed").count()).isZero();
    }

    @Test
    void metricsPublisher_increments_failure_counter_on_error() {
        doThrow(new PublishException("error")).when(delegate).publish(any());
        MessageEventPublisher metrics = new MetricsPublisher(delegate, registry);

        assertThatThrownBy(() -> metrics.publish(event))
                .isInstanceOf(PublishException.class);

        assertThat(registry.counter("messaging.published.failed").count()).isEqualTo(1.0);
        assertThat(registry.counter("messaging.published.total").count()).isZero();
    }

    @Test
    void metricsPublisher_records_timer() {
        MessageEventPublisher metrics = new MetricsPublisher(delegate, registry);
        metrics.publish(event);

        assertThat(registry.timer("messaging.publish.duration").count()).isEqualTo(1);
    }

    // ── Cadena completa: Logging → Metrics → delegate ────────────────

    @Test
    void decorator_chain_delegates_in_order() {
        MessageEventPublisher chain = new LoggingPublisher(
                new MetricsPublisher(delegate, registry)
        );
        chain.publish(event);

        verify(delegate, times(1)).publish(event);
        assertThat(registry.counter("messaging.published.total").count()).isEqualTo(1.0);
    }

    @Test
    void decorator_chain_propagates_exception_and_records_failure() {
        doThrow(new PublishException("fail")).when(delegate).publish(any());
        MessageEventPublisher chain = new LoggingPublisher(
                new MetricsPublisher(delegate, registry)
        );

        assertThatThrownBy(() -> chain.publish(event))
                .isInstanceOf(PublishException.class);

        assertThat(registry.counter("messaging.published.failed").count()).isEqualTo(1.0);
    }
}
