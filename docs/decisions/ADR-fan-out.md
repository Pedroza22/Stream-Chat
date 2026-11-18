# ADR: Estrategia de fan-out con varias instancias

**Estado:** Decisión tomada — limitación documentada para la versión de una sola instancia  
**Fecha:** 2026-11-18  
**Autores:** Equipo Stream-Chat (3 programadores)

---

## Contexto

Un evento de actividad, por ejemplo `TYPING_STARTED` del usuario A en el grupo G, debe
llegar por WebSocket a **todos** los miembros conectados de G. Cuando solo hay una
instancia, esto es trivial: la instancia procesa el evento y notifica a las sesiones que
tiene en su mapa interno, a través del puerto `ClientNotifier`.

El problema aparece con **varias instancias** del servidor detrás de un balanceador:

- Las conexiones WebSocket de los miembros de un mismo grupo pueden estar repartidas entre
  distintas instancias (cada miembro conectado a la instancia que le asignó el balanceo).
- El evento de typing se publica **una sola vez** (lo consume un único grupo de
  consumidores de Kafka), así que si esa instancia no tiene conectado a todos los miembros,
  algunos **no reciben la notificación**.
- Peor aún: si cada instancia mantiene su estado de presencia en memoria, ninguna ve el
  estado completo del grupo.

La pregunta es cómo hacer que un evento llegue a la instancia que tiene conectado a **cada**
miembro del grupo (el *fan-out* entre instancias), y qué hacer mientras no se resuelve.

---

## Opciones evaluadas

### Opción A — Versión académica de una sola instancia, con la limitación documentada (decisión tomada ✅)
Mantener **una sola instancia**. El estado de escritura vive en memoria
(`TypingStateRegistry`) y las notificaciones salen por `WebSocketClientNotifier`, que
publica en un topic STOMP local. Se documenta explícitamente que **no escala
horizontalmente**.

**Pros:**
- Coherente con el alcance académico y con el despliegue real del proyecto (una instancia).
- Sin estado compartido ni coordinación: nada que sincronizar, nada que romper.
- El estado de presencia es correcto y consistente dentro de la única instancia.
- Permite avanzar ya con pruebas de concurrencia, TTL y expiración reales.

**Contras:**
- **No es horizontalmente escalable**: no soporta más de una instancia sin cambios.
- Si la instancia cae, se pierde todo el estado de typing en memoria (se recupera vacío).
- El *fan-out* queda resuelto solo "de facto", porque todos los clientes cuelgan de la
  misma instancia.

### Opción B — `group.id` de Kafka distinto por instancia
Cada instancia usa su **propio** `group.id` de Kafka, de modo que **cada una lee todos** los
eventos de `user-activity` y **filtra** los que corresponden a las sesiones que tiene
conectadas.

**Pros:**
- Es la forma más directa de fan-out sobre Kafka: cada instancia recibe todos los eventos y
  decide a quién notificar según sus sesiones locales.
- No requiere un almacén de estado compartido para el fan-out (aunque sí para el estado).

**Contras:**
- El patrón de consumo pasa de *load-balancing* (grupo compartido) a *broadcast* (un grupo
  por instancia); hay que asegurar que cada instancia tenga un `group.id` estable y único
  (p. ej. derivado del hostname o del ID de pod).
- Se abandona el *auto-scaling* del grupo de consumidores: cada instancia procesa el 100 %
  del topic, así que el costo de CPU crece linealmente con el número de instancias.

### Opción C — Almacén de estado compartido + enrutamiento por presencia
Mover el estado de presencia y el registro de sesiones a un almacén compartido (por ejemplo,
Redis) y enrutar las notificaciones a la instancia que tiene cada sesión.

**Pros:** es la solución que escala de verdad y desacopla el estado de la instancia.  
**Contras:** introduce un componente más (Redis/Cluster) y una capa de coordinación; excede
el alcance académico y contradice el objetivo de mantener el estado en estructuras
concurrentes simples (`ConcurrentHashMap`).

---

## Decisión

Se adopta la **Opción A** para la entrega académica actual, dejando **la Opción B como el
camino natural de escalado** y la Opción C como evolución futura.

Lo que realmente existe hoy en el repositorio:

| Componente | Rol y limitación |
|---|---|
| `presence/domain/service/TypingStateRegistry` | `ConcurrentHashMap<TypingKey, UserTypingState>` **en memoria** de la instancia. Usa `merge` y `remove(key, value)` atómicos; sin `Map` estático. **No se comparte entre instancias.** |
| `chat/infrastructure/websocket/WebSocketClientNotifier` | Implementa `ClientNotifier`; publica con `SimpMessagingTemplate.convertAndSend("/topic/groups/" + groupId.value(), event)`. El broker STOMP es local a la instancia. |
| `messaging/kafka/config/KafkaTopicConfig` | Topic `user-activity` con **1 partición** (`partitions(1)`), coherente con la versión de una instancia. |
| `messaging/kafka/consumer/ActivityEventConsumer` | Consume `user-activity` con `groupId = ${spring.kafka.consumer.group-id:streamchat-presence}`, es decir, el modo estándar de grupo compartido. |

**Limitación documentada:** con más de una instancia y el `group.id` compartido actual
(`streamchat-presence`), Kafka reparte las particiones del topic entre las instancias, de
modo que un evento de typing solo lo procesa **una** instancia. Los miembros conectados a
las demás instancias no recibirían `TYPING_UPDATE`, y el estado en memoria quedaría
incompleto por instancia. Por eso la versión entregada se restringe a **una sola instancia**.

Para escalar, el cambio mínimo es la **Opción B**: asignar a cada instancia un `group.id`
distinto (`streamchat-presence-<instancia>`) y aumentar las particiones del topic; cada
instancia lee todo y filtra por sus sesiones locales.

---

## Consecuencia

- La solución entregada **no escala horizontalmente**; se asume y se declara como
  limitación conocida, no como descuido.
- Basta con levantar **una** instancia de la aplicación para que el *fan-out* de typing sea
  correcto.
- El día que se necesite más de una instancia, las piezas a cambiar están localizadas:
  `KafkaTopicConfig` (particiones), la configuración de `group.id` de
  `ActivityEventConsumer`, y —si se busca consistencia global— el reemplazo de
  `TypingStateRegistry` por un almacén compartido.

---

## Para la sustentación

> *"El fan-out con varias instancias es el problema de hacer llegar un evento de typing a
> la instancia que tiene conectado a cada miembro del grupo. Hoy entregamos una sola
> instancia: el estado vive en memoria en `TypingStateRegistry` y las notificaciones salen
> por `WebSocketClientNotifier` con un broker local, así que todo funciona, pero **no
> escala horizontalmente** y lo documentamos como limitación. Para escalar, la opción más
> directa es darle a cada instancia su propio `group.id` de Kafka para que cada una lea todo
> y filtre por sus sesiones; la alternativa completa sería un almacén de estado compartido."*
