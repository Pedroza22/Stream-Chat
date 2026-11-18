# ADR: Acoplamiento del dominio a Jakarta Persistence (JPA)

**Estado:** Decisión tomada — acoplamiento aceptado conscientemente  
**Fecha:** 2026-11-18  
**Autores:** Equipo Stream-Chat (3 programadores)

---

## Contexto

Las entidades de dominio (`ChatGroup`, `ChatMessage`, `GroupMember`, `User`) llevan
anotaciones de Jakarta Persistence (`@Entity`, `@Table`, `@Column`, `@OneToMany`, etc.).

En una arquitectura hexagonal estricta, el dominio no debería conocer cómo se persiste.
Sin embargo, este proyecto tiene restricciones de tiempo y es de naturaleza académica.

---

## Opciones evaluadas

### Opción A — Mantener el acoplamiento (decisión tomada ✅)
Dejar las anotaciones JPA en las entidades de dominio. Es el patrón Active Record,
común en proyectos Spring Boot.

**Pros:**
- Menos código (sin mappers ni clases `*Entity` separadas).
- El sistema ya funciona y está probado con 38+ tests en verde.
- No introduce riesgo de romper funcionalidad que ya opera correctamente.
- Libera tiempo para completar los hitos pendientes (E1–E6, hardening).

**Contras:**
- El dominio depende de `jakarta.persistence`, violando el DIP en sentido estricto.
- Teóricamente más difícil de reutilizar el dominio en otro contexto de persistencia.

### Opción B — Separar entidades JPA de modelos de dominio
Crear clases `*Entity` en `infrastructure/persistence/` con las anotaciones JPA
y mappers que conviertan entre entidad JPA y modelo de dominio.

**Pros:** dominio 100% puro, sin dependencia de frameworks.  
**Contras:** ~8 clases nuevas + riesgo de romper código que ya funciona + tiempo
que el equipo no tiene disponible dado el plazo del proyecto.

---

## Decisión

Se adopta la **Opción A**. El acoplamiento dominio-JPA se acepta como decisión
consciente para este proyecto académico por las siguientes razones:

1. El sistema ya funciona y está probado.
2. El patrón Active Record es válido y ampliamente usado en Spring Boot.
3. El costo de la Opción B supera el beneficio en el contexto actual.
4. Para la sustentación, el equipo puede explicar esta decisión con sus pros y contras.

---

## Consecuencia

La regla ArchUnit `domainShouldNotDependOnJpa` permanece con `@ArchIgnore`
como recordatorio visible de la deuda técnica. Si en el futuro el proyecto
escala o se reutiliza el dominio, la Opción B sería el camino a seguir.

---

## Para la sustentación

> *"Tomamos la decisión de mantener las anotaciones JPA en el dominio (patrón Active Record)
> porque el sistema ya estaba funcionando y probado, y el costo de separar las entidades
> superaba el beneficio en el tiempo disponible. Lo documentamos como deuda técnica en este
> ADR para que sea una decisión explícita y no un descuido."*
