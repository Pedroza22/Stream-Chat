package com.streamchat.messaging.kafka.producer;

import com.streamchat.contracts.UserActivityEvent;
import com.streamchat.presence.application.port.out.ActivityEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Adaptador Kafka que implementa el puerto ActivityEventPublisher.
 *
 * Clave de partición: "groupId:userId"
 *   - Garantiza que los eventos del mismo usuario en el mismo grupo
 *     lleguen siempre a la misma partición y en orden.
 *   - No asume orden global entre usuarios ni entre grupos distintos.
 *
 * La publicación es asíncrona (fire-and-forget con callback de log).
 * Los eventos de typing son efímeros: perder uno ocasionalmente es
 * preferible a bloquear el hilo de la petición esperando confirmación.
 * Si se requiere mayor confiabilidad, cambiar a sendAndWait().
 */
@Component
public class KafkaActivityEventPublisher implements ActivityEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaActivityEventPublisher.class);

    private final KafkaTemplate<String, UserActivityEvent> kafkaTemplate;
    private final String activityTopic;

    public KafkaActivityEventPublisher(
            KafkaTemplate<String, UserActivityEvent> kafkaTemplate,
            @Value("${app.messaging.activity-topic:user-activity}") String activityTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.activityTopic = activityTopic;
    }

    @Override
    public void publish(UserActivityEvent event) {
        // Clave de partición: orden garantizado por usuario dentro de un grupo
        String partitionKey = event.groupId() + ":" + event.userId();

        CompletableFuture<SendResult<String, UserActivityEvent>> future =
                kafkaTemplate.send(activityTopic, partitionKey, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Error publicando evento de actividad eventId={} eventType={} — {}",
                        event.eventId(), event.eventType(), ex.getMessage());
            } else {
                log.debug("Evento de actividad publicado eventId={} eventType={} partition={} offset={}",
                        event.eventId(),
                        event.eventType(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
