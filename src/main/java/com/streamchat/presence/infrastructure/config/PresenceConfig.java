package com.streamchat.presence.infrastructure.config;

import com.streamchat.presence.application.port.in.ProcessActivityUseCase;
import com.streamchat.presence.application.usecase.ProcessActivityUseCaseImpl;
import com.streamchat.presence.domain.policy.ExpirationPolicy;
import com.streamchat.presence.domain.policy.TtlExpirationPolicy;
import com.streamchat.presence.domain.service.TypingStateRegistry;
import com.streamchat.presence.infrastructure.metrics.MetricsProcessActivityUseCase;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.Duration;

@Configuration
@EnableScheduling
public class PresenceConfig {

    @Bean
    public TypingStateRegistry typingStateRegistry() {
        return new TypingStateRegistry();
    }

    @Bean
    public ExpirationPolicy typingExpirationPolicy(
            @Value("${app.presence.typing-ttl-ms:5000}") long typingTtlMs) {
        return new TtlExpirationPolicy(Duration.ofMillis(typingTtlMs));
    }

    @Bean
    public MeterBinder typingStatesActiveGauge(TypingStateRegistry registry) {
        return meterRegistry -> Gauge
                .builder("typing_states_active", registry, TypingStateRegistry::activeCount)
                .description("Estados de escritura activos")
                .register(meterRegistry);
    }

    /**
     * Cadena de decoradores sobre el procesador de actividad. @Primary hace que Spring
     * inyecte este bean donde se pida ProcessActivityUseCase (ActivityEventConsumer).
     * Añade métricas de latencia (E1) sin tocar el caso de uso real (SRP + OCP).
     */
    @Bean
    @Primary
    public ProcessActivityUseCase processActivityUseCase(ProcessActivityUseCaseImpl impl,
                                                         Clock clock,
                                                         MeterRegistry meterRegistry) {
        return new MetricsProcessActivityUseCase(impl, clock, meterRegistry);
    }
}
