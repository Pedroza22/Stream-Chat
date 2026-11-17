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
}
