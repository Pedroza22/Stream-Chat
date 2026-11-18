# ADR: Patrón Outbox para evitar el problema de doble escritura

**Estado:** Decisión tomada — patrón Outbox adoptado  
**Fecha:** 2026-11-18  
**Autores:** Equipo Stream-Chat (3 programadores)

---

## Contexto

Al enviar un mensaje hay que hacer dos cosas: guardarlo en PostgreSQL y publicar el
evento en RabbitMQ. Son dos operaciones independientes sobre dos sistemas distintos.

Si el proceso cae entre ambas (justo después de confirmar el `INSERT` en
`messages` y antes de publicar en RabbitMQ), el mensaje queda persistido pero **nunca
se distribuye** a los demás miembros del grupo. El emisor lo ve guardado, pero los
demás no lo reciben. Es el clásico **problema de doble escritura** (dual write).

Una transacción distribuida (XA/2PC) entre PostgreSQL y RabbitMQ resolvería la
atomicidad, pero RabbitMQ no es un recurso transaccional XA en este despliegue y el
costo operativo sería desproporcionado para un proyecto académico.

---

## Opciones evaluadas

### Opción A — Patrón Outbox transaccional (decisión tomada ✅)
El caso de uso guarda el mensaje **y** una fila en la tabla `outbox_events` dentro de
la **misma transacción de PostgreSQL**. Un proceso aparte, `OutboxRelay`, lee las filas
pendientes y las publica en RabbitMQ con *publisher confirm*; solo las marca como
enviadas si el broker confirma.

**Pros:**
- Atomicidad real: mensaje y evento se confirman o se revierten **juntos** (misma
  transacción de base de datos).
- Si el proceso cae, el evento se publica al recuperarse: la fila `PENDING` sobrevive.
- Desacopla la persistencia de la publicación; el caso de uso no conoce RabbitMQ.
- Reintento natural sin perder eventos; base sólida para la garantía *at-least-once*.
- Únicamente añade una tabla y un `@Scheduled`; no requiere infraestructura extra.

**Contras:**
- Aumenta la latencia de publicación (el relay corre cada `outbox-relay-delay-ms`,
  por defecto 5000 ms).
- La tabla `outbox_events` crece y necesita limpieza/purga periódica.
- Duplica información: el payload del evento se guarda serializado en la fila.

### Opción B — Publicar directamente en RabbitMQ dentro del caso de uso
Publicar el evento en el mismo flujo del caso de uso, sin fila de outbox.

**Pros:** menos código, latencia de publicación mínima, sin tabla adicional.  
**Contras:** reproduce el problema de doble escritura: si falla la publicación tras el
`COMMIT`, el mensaje queda guardado pero no se distribuye; si se publica antes del
`COMMIT`, se puede distribuir un mensaje que luego se revierte. No hay forma de
recuperarse automáticamente de una caída en medio de las dos operaciones.

---

## Decisión

Se adopta la **Opción A**. La implementación real en el repositorio es:

| Componente | Rol |
|---|---|
| `chat/infrastructure/persistence/OutboxEvent` | Entidad JPA de la tabla `outbox_events` (`PENDING`, `SENT`, `FAILED`). |
| `chat/infrastructure/persistence/OutboxRepository` | Repositorio JPA; `findPendingEvents(limit)` ordena por `createdAt ASC`. |
| `chat/infrastructure/persistence/OutboxEventPublisher` | Implementa el puerto `MessageEventPublisher`; serializa el evento a JSON y guarda la fila con `@Transactional(propagation = Propagation.MANDATORY)`. |
| `messaging/outbox/OutboxRelay` | `@Scheduled(fixedDelayString = "${app.messaging.outbox-relay-delay-ms:5000}")`; lee hasta 50 filas `PENDING`, las publica vía `RabbitMqMessagePublisher` y las marca `SENT` o `FAILED`. |
| `RabbitMqMessagePublisher` + `PublishException` | Publicación con *publisher confirm*; si el broker no confirma, lanza `PublishException`. |
| Migración `V5__create_outbox.sql` | Crea la tabla `outbox_events` mediante Liquibase. |

La clave de la implementación es que `OutboxEventPublisher.publish(...)` está anotado
con `Propagation.MANDATORY`: **exige** una transacción en curso, de modo que el caso de
uso (`SendMessage`) inserta el mensaje y la fila de outbox en la misma unidad atómica.

`OutboxRelay` marca `markSent()` solo tras un `publish` exitoso; ante `PublishException`
u otra excepción llama a `markFailed()`, y el siguiente ciclo vuelve a intentarlo.

---

## Consecuencia

- El sistema pasa a ofrecer **entrega al menos una vez** (*at-least-once*): un evento
  puede publicarse más de una vez (p. ej. si el relay cae tras publicar y antes de
  hacer `COMMIT` del `markSent`). Esto obliga a consumidores idempotentes
  (ver `ADR-idempotencia.md`).
- El relay asume *publisher confirm* habilitado en `application.yml`
  (`publisher-confirm-type: correlated`).
- Se necesita una tarea de limpieza de `outbox_events` (purgar filas `SENT` antiguas)
  para que la tabla no crezca sin límite.

---

## Para la sustentación

> *"En la v1, guardar en PostgreSQL y publicar en RabbitMQ eran dos operaciones
> independientes: si el proceso caía entre ambas, el mensaje quedaba guardado pero nunca
> llegaba a los demás. Lo resolvimos con el patrón Outbox: el caso de uso guarda el mensaje
> y una fila en `outbox_events` en la misma transacción, y un relay (`OutboxRelay`) publica
> las filas pendientes con publisher confirm y las marca como enviadas. Como el relay puede
> republicar tras una caída, la garantía es 'al menos una vez' y por eso los consumidores
> son idempotentes."*
