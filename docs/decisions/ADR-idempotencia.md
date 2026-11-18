# ADR: Entrega "al menos una vez" y consumidor idempotente

**Estado:** Decisión tomada — idempotencia por tabla `processed_events`  
**Fecha:** 2026-11-18  
**Autores:** Equipo Stream-Chat (3 programadores)

---

## Contexto

El patrón Outbox y el *publisher confirm* de RabbitMQ garantizan que un evento se
publica, pero **no** garantizan que se consuma exactamente una vez. Ante un NACK, un
reintento o una caída del consumidor, el broker puede reentregar el mismo mensaje.

Si un consumidor no deduplica, un mismo `MESSAGE_CREATED` produciría efectos de negocio
duplicados (por ejemplo, notificar dos veces `MESSAGE_NEW` a los miembros del grupo).

En un sistema de mensajería distribuida conseguir *exactly-once* end-to-end es costoso
y, en la práctica, la estrategia estándar es asumir **entrega al menos una vez**
(*at-least-once*) y hacer que los efectos sean idempotentes.

---

## Opciones evaluadas

### Opción A — At-least-once + consumidor con deduplicación persistente (decisión tomada ✅)
Aceptar la reentrega y deduplicar en el consumidor. Antes de producir efectos, cada
consumidor registra el `eventId` en la tabla `processed_events`, con restricción única
por `(event_id, consumer)`. Si el `INSERT` viola la restricción, el evento ya fue
procesado: se hace ACK sin volver a ejecutar la lógica.

**Pros:**
- No promete una garantía que el sistema no puede cumplir; el comportamiento es honesto
  y verificable.
- La deduplicación es persistente: sobrevive a reinicios del proceso (no depende de
  memoria).
- El índice único `(event_id, consumer)` permite que **varios** consumidores procesen el
  mismo evento sin interferirse entre sí (cada uno deduplica por su propio nombre).
- Encaja con la garantía *at-least-once* que ya impone el Outbox.

**Contras:**
- La tabla `processed_events` crece y requiere purga/limpieza.
- Aparece una ventana en la que el registro de idempotencia y el efecto de negocio no son
  atómicos entre sí (se resuelve priorizando el registro y usando NACK).
- Cada consumidor paga una escritura extra por mensaje.

### Opción B — Prometer exactly-once
Usar transacciones distribuidas o delegar por completo en el broker una garantía de
entrega exactamente una vez.

**Pros:** conceptualmente "más limpio" para el emisor.  
**Contras:** RabbitMQ no ofrece *exactly-once* de extremo a extremo; replicarlo exige
transacciones XA o un coordinador adicional, con un costo y una complejidad que el
proyecto académico no justifica. Además, el efecto de negocio (enviar por WebSocket) no
es transaccional, así que ninguna coordinación lo haría realmente exacto.

---

## Decisión

Se adopta la **Opción A**. El patrón es **Template Method**: una clase base define el
flujo (deduplicar → procesar → ACK/NACK) y las subclases solo implementan la lógica de
negocio. La implementación real en el repositorio es:

| Componente | Rol |
|---|---|
| `messaging/rabbitmq/consumer/AbstractIdempotentConsumer<T>` | Clase base (Template Method) con el flujo de idempotencia. |
| `messaging/rabbitmq/consumer/ProcessedEvent` | Entidad de la tabla `processed_events`; clave compuesta `(event_id, consumer)`. |
| `messaging/rabbitmq/consumer/ProcessedEventRepository` | Repositorio JPA; el `INSERT` viola el índice único en caso de duplicado. |
| `messaging/rabbitmq/consumer/MessageCreatedConsumer` | Consumidor concreto (`consumerName = "message-created"`); ACK manual. |
| Migración `V6__create_processed_events.sql` | Crea la tabla y la restricción única. |

El flujo de `AbstractIdempotentConsumer.handle(...)` es:

1. `processedEventRepository.saveAndFlush(ProcessedEvent.of(eventId, consumerName()))`.
2. Si salta `DataIntegrityViolationException` → duplicado → `basicAck` sin procesar.
3. Si no → `doProcess(message)` (método gancho de la subclase).
4. Si `doProcess` lanza excepción → `basicNack(..., requeue = false)` (va a la cola de
   reintento con TTL, y finalmente a la DLQ).
5. Si todo va bien → `basicAck`.

`MessageCreatedConsumer` se registra con `@RabbitListener(..., ackMode = "MANUAL")` y en
`doProcess` notifica al grupo por el puerto `ClientNotifier` (`WebSocketClientNotifier`).

Configuración relevante en `application.yml`:
`spring.rabbitmq.listener.simple.acknowledge-mode: manual` y
`spring.rabbitmq.publisher-confirm-type: correlated`.

---

## Consecuencia

- Los efectos de negocio son idempotentes por `(eventId, consumer)`: un `eventId` repetido
  no produce notificaciones duplicadas.
- El mismo `eventId` puede ser procesado por consumidores distintos sin colisión, porque
  el discriminador `consumer` forma parte de la clave.
- Se necesita una tarea de limpieza de `processed_events` para evitar su crecimiento
  indefinido.

---

## Para la sustentación

> *"No prometemos exactly-once, porque el broker y el envío por WebSocket no lo pueden
> garantizar de forma realista; prometemos entrega 'al menos una vez'. Para que los efectos
> de negocio no se dupliquen, cada consumidor registra el `eventId` en la tabla
> `processed_events` con clave única `(event_id, consumer)` antes de procesar. Lo
> implementamos con el patrón Template Method en `AbstractIdempotentConsumer`: si el
> `INSERT` choca con la restricción única, el evento ya se procesó y se hace ACK sin
> repetir la lógica."*
