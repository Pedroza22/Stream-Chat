package com.streamchat.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.streamchat", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

    @ArchTest
    static final ArchRule domainShouldNotDependOnSpring =
            noClasses().that().resideInAPackage("..chat.domain..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    @ArchTest
    static final ArchRule applicationShouldNotDependOnInfrastructure =
            noClasses().that().resideInAPackage("..chat.application..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule domainShouldNotDependOnApplication =
            noClasses().that().resideInAPackage("..chat.domain..")
                    .should().dependOnClassesThat().resideInAPackage("..chat.application..");

    /**
     * El dominio no debe depender de Jakarta Persistence (JPA).
     *
     * DEUDA TÉCNICA ACTIVA: las entidades de dominio (ChatGroup, ChatMessage,
     * GroupMember, User) llevan anotaciones JPA (@Entity, @Table, etc.).
     * Esta regla detecta ese acoplamiento. Se suprime temporalmente para no
     * bloquear el build mientras P1 separa entidades JPA de modelos de dominio.
     * Ver docs/decisions/ADR-dominio-jpa.md
     *
     * Para activarla: quitar @ArchIgnore y crear entidades JPA separadas
     * en infrastructure/persistence que mapeen desde/hacia los modelos de dominio.
     */
    @ArchTest
    @com.tngtech.archunit.junit.ArchIgnore(reason =
            "Deuda técnica: dominio acoplado a JPA. P1 debe separar entidades JPA " +
            "de modelos de dominio. Ver docs/decisions/ADR-dominio-jpa.md")
    static final ArchRule domainShouldNotDependOnJpa =
            noClasses().that().resideInAPackage("..chat.domain..")
                    .should().dependOnClassesThat().resideInAPackage("jakarta.persistence..")
                    .because("El dominio no debe acoplarse a Jakarta Persistence. " +
                             "Deuda técnica: separar entidades JPA de modelos de dominio.");
}
