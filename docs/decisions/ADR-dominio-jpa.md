# ADR: Acoplamiento del dominio a Jakarta Persistence (JPA)

**Estado:** Deuda técnica activa — pendiente de resolución  
**Fecha:** 2026-11-18  
**Responsable de resolución:** Programador 1

---

## Contexto

Las entidades de dominio (`ChatGroup`, `ChatMessage`, `GroupMember`, `User`) llevan
anotaciones de Jakarta Persistence (`@Entity`, `@Table`, `@Column`, `@OneToMany`, etc.).

Esto acopla el modelo de dominio a la capa de persistencia, contradiciendo el principio
de inversión de dependencias (DIP) y la arquitectura hexagonal: el dominio no debería
conocer cómo se persiste.

La regla ArchUnit `domainShouldNotDependOnJpa` detecta este acoplamiento pero está
suprimida (`@ArchIgnore`) para no bloquear el build mientras se resuelve.

## Decisión tomada en la v1 del proyecto

Por velocidad de desarrollo, se anotaron directamente las entidades de dominio con JPA.
Es un patrón válido para proyectos simples (Active Record), pero contradice la
arquitectura hexagonal declarada en el plan v2.

## Opciones

### Opción A — Mantener el acoplamiento (decisión actual)
- Pros: más simple, menos código, sin mapeos.
- Contras: viola DIP, el dominio depende de un framework de persistencia, dificulta
  probar el dominio en aislamiento puro.
- **Acción requerida:** documentar explícitamente como decisión y quitar `@ArchIgnore`.

### Opción B — Separar entidades JPA de modelos de dominio
- Crear clases `*Entity` en `infrastructure/persistence/` con las anotaciones JPA.
- Crear mappers (`*Mapper`) que conviertan entre entidad JPA y modelo de dominio.
- El dominio queda libre de cualquier framework.
- Pros: arquitectura limpia, dominio 100 % testeable sin Spring/JPA.
- Contras: más código (mappers), refactor significativo.

## Resolución pendiente

P1 debe elegir una de las dos opciones y:
1. Si elige A: quitar `@ArchIgnore` de `domainShouldNotDependOnJpa` y actualizar este ADR.
2. Si elige B: implementar la separación, quitar `@ArchIgnore` y actualizar este ADR.

Mientras no se resuelva, la regla permanece suprimida y este ADR sirve como registro
de la deuda técnica.
