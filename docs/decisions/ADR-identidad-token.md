# ADR: Identidad siempre desde el token de autenticación

**Estado:** Decisión tomada — restricción dura del proyecto  
**Fecha:** 2026-11-18  
**Autores:** Equipo Stream-Chat (3 programadores)

---

## Contexto

Cada vez que un usuario envía un mensaje o emite un evento de actividad, el sistema
necesita saber **quién** es: el `senderId` del mensaje y el `userId` del evento de typing.
Ese dato determina permisos (pertenencia al grupo), trazabilidad y a quién se notifica.

Existen dos caminos para obtenerlo:

1. Confiar en el **cuerpo del mensaje** (que el cliente incluya su propio `senderId` o
   `userId` en el JSON).
2. Obtenerlo de la **identidad autenticada** (el token validado en el handshake WebSocket
   o en el filtro REST).

Tomar la identidad del cuerpo es un anti-patrón clásico: cualquier cliente podría declarar
un `senderId` ajeno y **suplantar a otro usuario** (envío de mensajes en nombre de otros,
falsificación de actividad). El plan de trabajo (sección 8.4) lo lista explícitamente entre
los anti-patrones a evitar: *"Tomar `senderId` del cuerpo de la petición."*

---

## Opciones evaluadas

### Opción A — Identidad desde la autenticación (decisión tomada ✅)
El `senderId` y el `userId` provienen **siempre** de la identidad autenticada. En WebSocket,
del usuario establecido durante el handshake; en REST, del `SecurityContext` rellenado por
el filtro JWT. El cuerpo del mensaje **nunca** aporta la identidad.

**Pros:**
- Impide la suplantación: un cliente no puede enviar en nombre de otro.
- La autorización se apoya en un dato que el cliente no controla.
- Un token ausente o inválido se rechaza (401/403), como exige la matriz de pruebas.
- Único punto de verdad de la identidad: handshake y filtro comparten el mismo mecanismo
  (`JwtTokenProvider`).

**Contras:**
- Obliga a propagar la identidad desde la capa de transporte (WebSocket/REST) hasta los
  casos de uso; hay que pasarla explícitamente en los puertos.
- Exige pruebas específicas de "sin token" y "senderId falsificado".

### Opción B — Identidad desde el cuerpo del mensaje
Aceptar el `senderId`/`userId` enviado por el cliente en el JSON.

**Pros:** algo más simple de escribir en el cliente.  
**Contras:** abre la suplantación de identidad; el servidor confiaría en un dato no
verificado. Es un fallo de seguridad inaceptable y, además, un anti-patrón prohibido por el
plan.

---

## Decisión

Se adopta la **Opción A** como **restricción dura** del proyecto: la identidad sale siempre
de la autenticación, nunca del cuerpo. La implementación real en el repositorio lo respalda:

| Componente | Rol |
|---|---|
| `chat/infrastructure/websocket/WebSocketAuthInterceptor` | `ChannelInterceptor` que, en `StompCommand.CONNECT`, lee el token de las cabeceras `passcode` o `Authorization: Bearer`, lo valida con `JwtTokenProvider`, extrae `userId`/`username` y establece el `Principal` en la sesión STOMP. |
| `chat/infrastructure/security/JwtAuthenticationFilter` | `OncePerRequestFilter` que valida el `Bearer` en REST y publica un `AuthenticatedUser` en el `SecurityContext`. |
| `chat/infrastructure/security/AuthenticatedUser` | Portador de `userId` y `username` de la identidad autenticada. |
| `presence/infrastructure/websocket/ActivityWebSocketController` | Extrae el `userId` de `headerAccessor.getUser()` y lanza `IllegalStateException` si no hay usuario autenticado. |
| `presence/application/usecase/PublishActivityUseCaseImpl` | Construye el `UserActivityEvent` con el `userId` recibido de la identidad; su comentario lo declara: *"El userId viene de la identidad autenticada (nunca del body)"*. |

De forma simétrica, el flujo de mensajes toma el `senderId` del usuario autenticado en el
canal WebSocket/REST y lo usa al construir el `MessageCreatedEvent`; el cuerpo solo aporta
el contenido del mensaje.

---

## Consecuencia

- Un token ausente o inválido se rechaza: el handshake WebSocket lanza `MessagingException`
  y el filtro REST no autentica la petición (401/403).
- El `senderId`/`userId` de cualquier evento es **fiable** por construcción.
- Cada caso de uso que necesite identidad debe recibirla como parámetro desde la capa de
  transporte; no debe leerla de un DTO de entrada.
- Es una regla verificable en pruebas: enviar un `senderId` distinto al del token debe ser
  ignorado o rechazado.

---

## Para la sustentación

> *"La identidad del usuario nunca sale del cuerpo del mensaje; siempre la tomamos de la
> autenticación. En WebSocket, el `WebSocketAuthInterceptor` valida el token en el handshake
> y deja el usuario en la sesión; en REST, el `JwtAuthenticationFilter` publica la identidad
> en el `SecurityContext`. Así, el `senderId` y el `userId` son datos en los que el servidor
> puede confiar y un cliente no puede suplantar a otro. Tomar el `senderId` del body sería
> un anti-patrón de seguridad y lo tenemos prohibido como restricción dura del proyecto."*
