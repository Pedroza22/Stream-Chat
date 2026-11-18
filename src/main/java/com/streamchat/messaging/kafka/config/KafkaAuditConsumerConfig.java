package com.streamchat.messaging.kafka.config;

import com.streamchat.contracts.MessageCreatedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuración del consumer de Kafka para analytics_service.
 *
 * Usa un factory separado ("auditKafkaListenerContainerFactory") para
 * desacoplar su configuración del consumer de presencia (group-id distinto,
 * tipo de deserialización distinto: MessageCreatedEvent en lugar de UserActivityEvent).
 *
 * group-id: streamchat-analytics — offset independiente del de presencia.
 * ack-mode: MANUAL — commit explícito tras persistir en PostgreSQL.
 */
@Configuration
public class KafkaAuditConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, MessageCreatedEvent> auditConsumerFactory() {
        JsonDeserializer<MessageCreatedEvent> deserializer =
                new JsonDeserializer<>(MessageCreatedEvent.class, false);
        deserializer.addTrustedPackages("com.streamchat.contracts");

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "streamchat-analytics");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 50);

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, MessageCreatedEvent>
    auditKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, MessageCreatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(auditConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }
}
