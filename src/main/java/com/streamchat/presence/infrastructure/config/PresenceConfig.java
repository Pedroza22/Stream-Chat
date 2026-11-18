package com.streamchat.presence.infrastructure.config;

import com.streamchat.presence.domain.policy.ExpirationPolicy;
import com.streamchat.presence.domain.policy.TtlExpirationPolicy;
import com.streamchat.presence.domain.service.TypingStateRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

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
}
