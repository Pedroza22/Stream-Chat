package com.streamchat.messaging.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declara el topic de Kafka para eventos de actividad de usuario (typing).
 *
 * Decisiones de diseño:
 * - 1 partición: versión académica de una sola instancia; documentado como limitación.
 * - retention.ms = 3 600 000 (1 hora): los eventos de typing son efímeros.
 * - cleanup.policy = delete: sin compactación, solo ventana de tiempo.
 * - Clave de partición: "groupId:userId" — garantiza orden por usuario dentro de un grupo.
 */
@Configuration
public class KafkaTopicConfig {

    @Value("${app.messaging.activity-topic:user-activity}")
    private String activityTopic;

    @Bean
    NewTopic userActivityTopic() {
        return TopicBuilder
                .name(activityTopic)
                .partitions(1)
                .replicas(1)
                .config("retention.ms",    "3600000")   // 1 hora
                .config("cleanup.policy",  "delete")
                .config("max.message.bytes", "1048576") // 1 MB
                .build();
    }

    /**
     * Topic de auditoría de mensajes de chat.
     * audit_bridge publica aquí; analytics_service consume y persiste en PostgreSQL.
     * Retención larga (7 días) — es el log inmutable de mensajes.
     */
    @Bean
    NewTopic messageAuditLogTopic() {
        return TopicBuilder
                .name("message_audit_log")
                .partitions(1)
                .replicas(1)
                .config("retention.ms",   "604800000")  // 7 días
                .config("cleanup.policy", "delete")
                .build();
    }
}
