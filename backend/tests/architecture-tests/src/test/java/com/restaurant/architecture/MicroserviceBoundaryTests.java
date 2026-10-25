package com.restaurant.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.nio.file.Path;
import java.util.List;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class MicroserviceBoundaryTests {

    private static final List<String> BUSINESS_PACKAGES = List.of(
            "com.restaurant.identity..",
            "com.restaurant.restaurant..",
            "com.restaurant.workforce..",
            "com.restaurant.operations..",
            "com.restaurant.billing..",
            "com.restaurant.notification..",
            "com.restaurant.audit..",
            "com.restaurant.reporting..");

    private static final List<String> MODULES = List.of(
            "service-foundation",
            "api-gateway",
            "identity-service",
            "restaurant-service",
            "workforce-service",
            "operations-service",
            "billing-service",
            "notification-service",
            "audit-service",
            "reporting-service");

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPaths(MODULES.stream()
                    .map(module -> Path.of(
                            System.getProperty("restaurant.backend.directory"),
                            "services",
                            module,
                            "target",
                            "classes"))
                    .toList());

    @Test
    void businessServicesDoNotDependOnEachOtherOrGateway() {
        for (String sourcePackage : BUSINESS_PACKAGES) {
            List<String> forbiddenPackages = BUSINESS_PACKAGES.stream()
                    .filter(candidate -> !candidate.equals(sourcePackage))
                    .toList();
            forbiddenPackages = new java.util.ArrayList<>(forbiddenPackages);
            forbiddenPackages.add("com.restaurant.gateway..");

            noClasses()
                    .that().resideInAPackage(sourcePackage)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            forbiddenPackages.toArray(String[]::new))
                    .because("a microservice may communicate through contracts, not another service's classes")
                    .check(classes);
        }
    }

    @Test
    void gatewayDoesNotDependOnBusinessImplementations() {
        noClasses()
                .that().resideInAPackage("com.restaurant.gateway..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BUSINESS_PACKAGES.toArray(String[]::new))
                .because("the gateway may route to services but must not import their implementation")
                .check(classes);
    }

    @Test
    void sharedFoundationContainsNoBusinessDependencies() {
        noClasses()
                .that().resideInAPackage("com.restaurant.foundation..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BUSINESS_PACKAGES.toArray(String[]::new))
                .because("the shared foundation must remain technical")
                .check(classes);
    }
}
