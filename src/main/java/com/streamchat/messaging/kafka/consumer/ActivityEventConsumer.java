package com.streamchat.messaging.kafka.consumer;

import com.streamchat.contracts.UserActivityEvent;
import com.streamchat.presence.application.port.in.ProcessActivityUseCase;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor de eventos de actividad (typing) desde Kafka.
 *
 * Cierra el flujo del diagrama de trabajo: el evento publicado por el controlador
 * WebSocket viaja por el topic {@code user-activity} y aquí se entrega al motor de
 * presencia, que mantiene el estado de escritura, aplica el TTL y notifica al grupo
 * por WebSocket (mismo {@code ClientNotifier} que usan los mensajes).
 *
 * Kafka garantiza el orden por {@code groupId:userId} (clave de partición), por lo que
 * un TYPING_STARTED nunca adelanta a un TYPING_STOPPED del mismo usuario en el mismo grupo.
 */
@Component
public class ActivityEventConsumer {

    private final ProcessActivityUseCase processActivityUseCase;

    public ActivityEventConsumer(ProcessActivityUseCase processActivityUseCase) {
        this.processActivityUseCase = processActivityUseCase;
    }

    @KafkaListener(
            topics = "${app.messaging.activity-topic:user-activity}",
            groupId = "${spring.kafka.consumer.group-id:streamchat-presence}"
    )
    public void onActivityEvent(UserActivityEvent event) {
        processActivityUseCase.handle(event);
    }
}
