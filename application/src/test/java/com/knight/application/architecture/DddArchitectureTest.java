package com.knight.application.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit tests to enforce DDD layered architecture rules across all bounded contexts.
 *
 * Package Structure within each bounded context (e.g., com.knight.domain.serviceprofiles):
 * - api: Command/Query interfaces and DTOs
 * - aggregate: Domain aggregates and entities
 * - service: Application services implementing API interfaces
 *
 * Layer Dependencies:
 * - API: Pure interfaces, no dependencies on other domain layers
 * - Aggregate: Core domain logic, depends only on shared kernel
 * - Service: Orchestrates domain operations, depends on API and Aggregate
 * - Application: Infrastructure layer, depends on all domain modules
 */
@DisplayName("DDD Architecture Rules")
public class DddArchitectureTest {

    private static JavaClasses importedClasses;

    @BeforeAll
    static void setup() {
        importedClasses = new ClassFileImporter()
            .importPackages("com.knight");
    }

    @Nested
    @DisplayName("API Layer Isolation")
    class ApiLayerTests {

        @Test
        @DisplayName("API should not depend on Aggregate")
        void apiShouldNotDependOnAggregate() {
            ArchRule rule = noClasses()
                .that().resideInAPackage("..api..")
                .should().dependOnClassesThat().resideInAPackage("..aggregate..");

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("API should not depend on Service")
        void apiShouldNotDependOnService() {
            ArchRule rule = noClasses()
                .that().resideInAPackage("..api..")
                .should().dependOnClassesThat().resideInAPackage("..service..");

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("API should not depend on Application")
        void apiShouldNotDependOnApplication() {
            ArchRule rule = noClasses()
                .that().resideInAPackage("..api..")
                .should().dependOnClassesThat().resideInAPackage("..application..");

            rule.check(importedClasses);
        }
    }

    @Nested
    @DisplayName("Aggregate Layer Isolation")
    class AggregateLayerTests {

        @Test
        @DisplayName("Aggregate should not depend on Service")
        void aggregateShouldNotDependOnService() {
            ArchRule rule = noClasses()
                .that().resideInAPackage("..aggregate..")
                .should().dependOnClassesThat().resideInAPackage("..service..");

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Aggregate should not depend on Application")
        void aggregateShouldNotDependOnApplication() {
            ArchRule rule = noClasses()
                .that().resideInAPackage("..aggregate..")
                .should().dependOnClassesThat().resideInAPackage("..application..");

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Aggregate should not depend on API")
        void aggregateShouldNotDependOnApi() {
            ArchRule rule = noClasses()
                .that().resideInAPackage("..aggregate..")
                .should().dependOnClassesThat().resideInAPackage("..api..");

            rule.check(importedClasses);
        }
    }

    @Nested
    @DisplayName("Service Layer")
    class ServiceLayerTests {

        @Test
        @DisplayName("Service should not depend on Application")
        void serviceShouldNotDependOnApplication() {
            ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..service..")
                .should().dependOnClassesThat().resideInAPackage("..application..");

            rule.check(importedClasses);
        }
    }

    @Nested
    @DisplayName("Bounded Context Isolation")
    class BoundedContextTests {

        @Test
        @DisplayName("Service Profiles should not depend on other domains")
        void serviceProfilesShouldNotDependOnOtherDomains() {
            ArchRule rule = noClasses()
                .that().resideInAPackage("..domain.serviceprofiles..")
                .should().dependOnClassesThat().resideInAnyPackage(
                    "..domain.indirectclients..",
                    "..domain.users..",
                    "..domain.policy..",
                    "..domain.approvalworkflows.."
                );

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Users should not depend on other domains")
        void usersShouldNotDependOnOtherDomains() {
            ArchRule rule = noClasses()
                .that().resideInAPackage("..domain.users..")
                .should().dependOnClassesThat().resideInAnyPackage(
                    "..domain.serviceprofiles..",
                    "..domain.indirectclients..",
                    "..domain.policy..",
                    "..domain.approvalworkflows.."
                );

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Approval Workflows should not depend on other domains")
        void approvalWorkflowsShouldNotDependOnOtherDomains() {
            ArchRule rule = noClasses()
                .that().resideInAPackage("..domain.approvalworkflows..")
                .should().dependOnClassesThat().resideInAnyPackage(
                    "..domain.serviceprofiles..",
                    "..domain.indirectclients..",
                    "..domain.users..",
                    "..domain.policy.."
                );

            rule.check(importedClasses);
        }
    }
}
