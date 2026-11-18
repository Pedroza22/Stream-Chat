package com.streamchat.messaging.rabbitmq.publisher;

import com.streamchat.chat.application.port.out.MessageEventPublisher;
import com.streamchat.contracts.MessageCreatedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Decorador que contabiliza publicaciones exitosas y fallidas usando Micrometer.
 */
public class MetricsMessageEventPublisher implements MessageEventPublisher {

    private final MessageEventPublisher delegate;
    private final Counter successCounter;
    private final Counter failureCounter;

    public MetricsMessageEventPublisher(MessageEventPublisher delegate, MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.successCounter = meterRegistry.counter("messages.published.success");
        this.failureCounter = meterRegistry.counter("messages.published.failure");
    }

    @Override
    public void publish(MessageCreatedEvent event) {
        try {
            delegate.publish(event);
            successCounter.increment();
        } catch (Exception e) {
            failureCounter.increment();
            throw e;
        }
    }
}
