package com.streamchat.presence.infrastructure.metrics;

import com.streamchat.contracts.UserActivityEvent;
import com.streamchat.presence.application.port.in.ProcessActivityUseCase;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Clock;
import java.time.Duration;

/**
 * Decorador (patrón Decorator) que instrumenta el procesador de actividad.
 *
 * Métricas expuestas (visibles en /actuator/metrics):
 * - presence.typing.update.latency — latencia entre {@code occurredAt} del evento y su procesamiento.
 *   Es la métrica que mide el experimento E1 (carga de CPU frente a tiempo de entrega de TYPING_UPDATE).
 * - presence.activity.processed    — contador de eventos de actividad procesados.
 *
 * Envuelve al caso de uso real sin modificarlo (SRP + OCP), igual que MetricsPublisher
 * hace con el publicador de mensajes.
 */
public class MetricsProcessActivityUseCase implements ProcessActivityUseCase {

    private final ProcessActivityUseCase delegate;
    private final Clock clock;
    private final Timer latencyTimer;
    private final Counter processedCounter;

    public MetricsProcessActivityUseCase(ProcessActivityUseCase delegate, Clock clock, MeterRegistry registry) {
        this.delegate = delegate;
        this.clock = clock;
        this.latencyTimer = Timer.builder("presence.typing.update.latency")
                .description("Latencia de procesamiento de eventos de actividad (occurredAt -> procesado)")
                .publishPercentiles(0.5, 0.95, 0.99)   // p50/p95/p99 requeridos por el experimento E1
                .register(registry);
        this.processedCounter = Counter.builder("presence.activity.processed")
                .description("Total de eventos de actividad (typing) procesados")
                .register(registry);
    }

    @Override
    public void handle(UserActivityEvent event) {
        if (event != null && event.occurredAt() != null) {
            Duration latency = Duration.between(event.occurredAt(), clock.instant());
            if (!latency.isNegative()) {
                latencyTimer.record(latency);
            }
        }
        processedCounter.increment();
        delegate.handle(event);
    }
}
