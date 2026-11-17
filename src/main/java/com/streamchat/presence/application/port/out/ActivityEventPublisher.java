package com.streamchat.presence.application.port.out;

import com.streamchat.contracts.UserActivityEvent;

/**
 * Puerto de salida para publicar eventos de actividad de usuario (typing).
 *
 * Lo define el módulo presence (quien lo usa), no el módulo messaging (quien lo implementa).
 * Esto respeta el principio de inversión de dependencias: la abstracción pertenece
 * al cliente, no al proveedor.
 *
 * Implementación real: KafkaActivityEventPublisher en messaging/kafka/producer/.
 * Implementación de prueba: FakeActivityEventPublisher en src/test/.
 */
public interface ActivityEventPublisher {
    void publish(UserActivityEvent event);
}
