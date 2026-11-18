# ADR: Uso de dos brokers, RabbitMQ y Kafka

**Estado:** Decisión tomada — dos brokers con responsabilidades separadas  
**Fecha:** 2026-11-18  
**Autores:** Equipo Stream-Chat (3 programadores)

---

## Contexto

Stream-Chat maneja dos flujos asíncronos con características muy distintas:

1. **Mensajes de chat** (`MESSAGE_CREATED`): son la funcionalidad principal, deben
   persistirse y distribuirse de forma fiable. Requieren ACK, reintentos y una cola de
   mensajes fallidos para no perder un mensaje ni duplicarlo.
2. **Actividad de usuario** (`TYPING_STARTED` / `TYPING_STOPPED`): son eventos
   **efímeros** y de alto volumen. Un `TYPING_STARTED` que llega tarde no sirve de nada; no
   hay que reintentarlo indefinidamente, sí interesa poder **reproducir** y **repasar** el
   flujo reciente.

La pregunta de arquitectura es si conviene un único broker para todo o dos brokers con
responsabilidades separadas. El plan de trabajo (sección 2.4, punto 3) exige **escribir la
justificación**: si el equipo no puede justificarla con argumentos, es señal de que uno de
los dos brokers sobra.

---

## Opciones evaluadas

### Opción A — Dos brokers: RabbitMQ para mensajes, Kafka para actividad (decisión tomada ✅)
Usar **RabbitMQ** como cola de trabajo para el flujo de mensajes (comandos/eventos que
necesitan ACK, reintentos y DLQ) y **Kafka** como log distribuido para el flujo de
actividad (alto volumen, efímero, reproducible).

**Pros:**
- Cada broker se usa para lo que está diseñado: RabbitMQ enruta y garantiza la entrega con
  ACK y DLQ; Kafka retiene y permite reproducir un log de eventos.
- El flujo crítico de mensajes no compite por recursos con el ruido de alto volumen del
  typing: se aíslan fallos y cargas.
- Kafka, con `retention.ms` corto, deja que los eventos efímeros "caducan" solos; no hace
  falta borrarlos a mano.
- Permite demostrar para la asignatura de Sistemas Distribuidos el uso correcto de dos
  modelos de mensajería (cola de trabajo vs. log particionado).

**Contras:**
- Dos componentes más que operar, configurar y asegurar (más superficie de ataque y más
  consumo de recursos).
- Mayor curva de aprendizaje para el equipo.
- Riesgo real de **sobrediseño** si el volumen no lo justifica.

### Opción B — Un solo broker para todo
Elegir uno de los dos y forzarlo a cubrir ambos flujos: solo RabbitMQ, o solo Kafka.

**Pros:** menos infraestructura, una sola tecnología que aprender y operar.  
**Contras:** si se usa solo RabbitMQ, no se aprovecha la retención/reproducción de un log
para el flujo de actividad; si se usa solo Kafka, se renuncia a patrones de cola de trabajo
con DLQ nativa y reintentos dirigidos por mensaje que RabbitMQ ya ofrece. Se gana simplicidad
a costa de forzar una herramienta fuera de su propósito.

---

## Decisión

Se adopta la **Opción A**, con la siguiente separación de responsabilidades:

| Flujo | Broker | Por qué |
|---|---|---|
| Mensajes de chat | **RabbitMQ** | Cola de trabajo con ACK manual, reintentos con TTL y Dead Letter Queue; garantía de entrega fiable. |
| Actividad (typing) | **Kafka** | Log de alto volumen, efímero y reproducible; retención por tiempo y orden por clave. |

La implementación real en el repositorio respalda esta separación:

| Componente | Rol |
|---|---|
| `messaging/rabbitmq/config/RabbitMqTopologyConfig` | Declara el exchange topic `streamchat.events`, la cola `streamchat.messages.created`, el exchange de reintento `streamchat.events.retry` (TTL) y la DLQ `streamchat.messages.created.dlq`. |
| `messaging/rabbitmq/config/RabbitMqProperties` | Centraliza los nombres (`DEFAULT_EXCHANGE`, `DEFAULT_RETRY_TTL_MS = 10_000`, etc.). |
| `messaging/rabbitmq/consumer/MessageCreatedConsumer` | Consumidor idempotente con ACK manual. |
| `messaging/kafka/config/KafkaTopicConfig` | Declara el topic `user-activity` con `retention.ms = 3600000` (1 h), `cleanup.policy = delete` y **1 partición**. |
| `messaging/kafka/consumer/ActivityEventConsumer` | Consume `user-activity` y entrega al motor de presencia. |

**Advertencia del plan (sección 2.4, punto 3):** *"si el equipo no puede justificarlo con
estos argumentos, es señal de que uno de los dos sobra; documentarlo igualmente."* En este
proyecto sí se justifica, porque los dos flujos tienen requisitos opuestos
(fiabilidad con reintentos vs. efímero y reproducible) y se benefician de quedar aislados.

Configuración relevante en `application.yml`: `spring.rabbitmq.publisher-confirm-type:
correlated`, `spring.rabbitmq.listener.simple.acknowledge-mode: manual`, y para Kafka
`spring.kafka.consumer.enable-auto-commit: false` (commit manual tras procesar).

---

## Consecuencia

- El entorno de desarrollo debe levantar dos brokers (agravado en Docker Compose: más
  contenedores, memoria y puertos internos a gestionar, ninguno publicado al exterior).
- El flujo de mensajes queda desacoplado de la carga de typing: un pico de actividad no
  degrada la entrega de mensajes.
- El flujo de actividad se puede revisar/reproducir mientras la retención siga vigente
  (1 hora), y los eventos antiguos se descartan automáticamente.
- Si en el futuro se simplifica el alcance y el flujo de actividad pierde valor, la
  decisión a reconsiderar es retirar Kafka, no RabbitMQ.

---

## Para la sustentación

> *"Usamos dos brokers porque los dos flujos tienen requisitos opuestos. RabbitMQ es una
> cola de trabajo para los mensajes del chat: necesita ACK, reintentos con TTL y una DLQ
> para no perder ni duplicar mensajes. Kafka es un log para la actividad de typing: son
> eventos efímeros y de alto volumen que queremos poder reproducir y que caducan solos
> tras una hora. Si no pudiéramos explicar esta diferencia, tendríamos que aceptar que uno
> de los dos sobra; en nuestro caso está justificado y queda documentado en este ADR."*
