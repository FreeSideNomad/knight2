package com.knight.application.persistence.policies.repository;

import com.knight.domain.policy.aggregate.Policy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MSSQLServer",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=true",
    "spring.flyway.enabled=false",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
    "spring.kafka.listener.auto-startup=false",
    "spring.kafka.consumer.auto-startup=false"
})
class PolicyRepositoryAdapterTest {

    @Autowired
    private PolicyRepositoryAdapter repository;

    @Autowired
    private PolicyJpaRepository jpaRepository;

    @BeforeEach
    void setUp() {
        jpaRepository.deleteAll();
    }

    @Nested
    @DisplayName("save()")
    class SaveTests {

        @Test
        @DisplayName("should save permission policy")
        void shouldSavePermissionPolicy() {
            Policy policy = Policy.create(
                Policy.PolicyType.PERMISSION,
                "user:admin",
                "read",
                "resource:*",
                null
            );

            repository.save(policy);

            assertThat(jpaRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should save approval policy with approver count")
        void shouldSaveApprovalPolicy() {
            Policy policy = Policy.create(
                Policy.PolicyType.APPROVAL,
                "role:manager",
                "approve",
                "expense:high-value",
                3
            );

            repository.save(policy);

            Optional<Policy> found = repository.findById(policy.id());
            assertThat(found).isPresent();
            assertThat(found.get().approverCount()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("findById()")
    class FindByIdTests {

        @Test
        @DisplayName("should find existing policy")
        void shouldFindExistingPolicy() {
            Policy policy = Policy.create(
                Policy.PolicyType.PERMISSION,
                "service:api",
                "invoke",
                "endpoint:/api/*",
                null
            );
            repository.save(policy);

            Optional<Policy> found = repository.findById(policy.id());

            assertThat(found).isPresent();
            assertThat(found.get().id()).isEqualTo(policy.id());
            assertThat(found.get().subject()).isEqualTo("service:api");
        }

        @Test
        @DisplayName("should return empty for non-existing policy")
        void shouldReturnEmptyForNonExistingPolicy() {
            Optional<Policy> found = repository.findById("00000000-0000-0000-0000-000000000000");

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("deleteById()")
    class DeleteByIdTests {

        @Test
        @DisplayName("should delete existing policy")
        void shouldDeleteExistingPolicy() {
            Policy policy = Policy.create(
                Policy.PolicyType.PERMISSION,
                "user:test",
                "delete",
                "resource:temp",
                null
            );
            repository.save(policy);
            assertThat(jpaRepository.count()).isEqualTo(1);

            repository.deleteById(policy.id());

            assertThat(jpaRepository.count()).isEqualTo(0);
        }
    }
}
