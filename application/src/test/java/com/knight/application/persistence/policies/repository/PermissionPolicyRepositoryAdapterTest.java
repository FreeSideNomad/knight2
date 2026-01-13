package com.knight.application.persistence.policies.repository;

import com.knight.domain.policy.aggregate.PermissionPolicy;
import com.knight.domain.policy.repository.PermissionPolicyRepository;
import com.knight.domain.policy.types.Action;
import com.knight.domain.policy.types.Resource;
import com.knight.domain.policy.types.Subject;
import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.SrfClientId;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for PermissionPolicyRepositoryAdapter.
 */
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
    "spring.jpa.show-sql=false",
    "spring.flyway.enabled=false",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
    "spring.kafka.listener.auto-startup=false",
    "spring.kafka.consumer.auto-startup=false"
})
@DisplayName("PermissionPolicyRepositoryAdapter Tests")
class PermissionPolicyRepositoryAdapterTest {

    @Autowired
    private PermissionPolicyRepository repository;

    @Autowired
    private PermissionPolicyJpaRepository jpaRepository;

    private ProfileId profileId;

    @BeforeEach
    void setUp() {
        jpaRepository.deleteAll();
        profileId = ProfileId.of("servicing", new SrfClientId("123456789"));
    }

    @Nested
    @DisplayName("save()")
    class Save {

        @Test
        @DisplayName("should save new policy")
        void shouldSaveNewPolicy() {
            // Given
            PermissionPolicy policy = createTestPolicy("ADMIN", "payments.*");

            // When
            repository.save(policy);

            // Then
            Optional<PermissionPolicy> found = repository.findById(policy.id());
            assertThat(found).isPresent();
            assertThat(found.get().action().value()).isEqualTo("payments.*");
        }

        @Test
        @DisplayName("should update existing policy")
        void shouldUpdateExistingPolicy() {
            // Given
            PermissionPolicy policy = createTestPolicy("MANAGER", "approvals.view");
            repository.save(policy);

            // Update via domain
            PermissionPolicy updated = PermissionPolicy.reconstitute(
                policy.id(),
                policy.profileId(),
                policy.subject(),
                Action.of("approvals.*"),  // Updated action
                policy.resource(),
                policy.effect(),
                "Updated description",
                policy.createdAt(),
                policy.createdBy(),
                policy.updatedAt()
            );

            // When
            repository.save(updated);

            // Then
            Optional<PermissionPolicy> found = repository.findById(policy.id());
            assertThat(found).isPresent();
            assertThat(found.get().action().value()).isEqualTo("approvals.*");
            assertThat(found.get().description()).isEqualTo("Updated description");
        }
    }

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("should find policy by ID")
        void shouldFindPolicyById() {
            // Given
            PermissionPolicy policy = createTestPolicy("USER", "accounts.view");
            repository.save(policy);

            // When
            Optional<PermissionPolicy> found = repository.findById(policy.id());

            // Then
            assertThat(found).isPresent();
            assertThat(found.get().id()).isEqualTo(policy.id());
            assertThat(found.get().subject().identifier()).isEqualTo("USER");
        }

        @Test
        @DisplayName("should return empty when policy not found")
        void shouldReturnEmptyWhenPolicyNotFound() {
            // When
            Optional<PermissionPolicy> found = repository.findById(UUID.randomUUID().toString());

            // Then
            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByProfileId()")
    class FindByProfileId {

        @Test
        @DisplayName("should find all policies for profile")
        void shouldFindAllPoliciesForProfile() {
            // Given
            repository.save(createTestPolicy("ADMIN", "payments.*"));
            repository.save(createTestPolicy("USER", "accounts.view"));
            repository.save(createTestPolicy("MANAGER", "approvals.*"));

            // When
            List<PermissionPolicy> policies = repository.findByProfileId(profileId);

            // Then
            assertThat(policies).hasSize(3);
        }

        @Test
        @DisplayName("should return empty list when no policies for profile")
        void shouldReturnEmptyListWhenNoPoliciesForProfile() {
            // Given
            ProfileId otherProfile = ProfileId.of("online", new SrfClientId("999999999"));

            // When
            List<PermissionPolicy> policies = repository.findByProfileId(otherProfile);

            // Then
            assertThat(policies).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByProfileIdAndSubject()")
    class FindByProfileIdAndSubject {

        @Test
        @DisplayName("should find policies for specific subject")
        void shouldFindPoliciesForSpecificSubject() {
            // Given
            repository.save(createTestPolicy("ADMIN", "payments.*"));
            repository.save(createTestPolicy("ADMIN", "transfers.*"));
            repository.save(createTestPolicy("USER", "accounts.view"));

            // When
            List<PermissionPolicy> policies = repository.findByProfileIdAndSubject(
                profileId,
                Subject.role("ADMIN")
            );

            // Then
            assertThat(policies).hasSize(2);
            assertThat(policies).allMatch(p -> p.subject().identifier().equals("ADMIN"));
        }

        @Test
        @DisplayName("should return empty when no policies for subject")
        void shouldReturnEmptyWhenNoPoliciesForSubject() {
            // Given
            repository.save(createTestPolicy("ADMIN", "payments.*"));

            // When
            List<PermissionPolicy> policies = repository.findByProfileIdAndSubject(
                profileId,
                Subject.role("NONEXISTENT")
            );

            // Then
            assertThat(policies).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByProfileIdAndSubjects()")
    class FindByProfileIdAndSubjects {

        @Test
        @DisplayName("should find policies for multiple subjects")
        void shouldFindPoliciesForMultipleSubjects() {
            // Given
            repository.save(createTestPolicy("ADMIN", "payments.*"));
            repository.save(createTestPolicy("USER", "accounts.view"));
            repository.save(createTestPolicy("MANAGER", "approvals.*"));

            // When
            List<PermissionPolicy> policies = repository.findByProfileIdAndSubjects(
                profileId,
                List.of(Subject.role("ADMIN"), Subject.role("USER"))
            );

            // Then
            assertThat(policies).hasSize(2);
        }

        @Test
        @DisplayName("should return empty list for empty subjects list")
        void shouldReturnEmptyListForEmptySubjectsList() {
            // Given
            repository.save(createTestPolicy("ADMIN", "payments.*"));

            // When
            List<PermissionPolicy> policies = repository.findByProfileIdAndSubjects(
                profileId,
                List.of()
            );

            // Then
            assertThat(policies).isEmpty();
        }
    }

    @Nested
    @DisplayName("deleteById()")
    class DeleteById {

        @Test
        @DisplayName("should delete existing policy")
        void shouldDeleteExistingPolicy() {
            // Given
            PermissionPolicy policy = createTestPolicy("TEMP", "temp.*");
            repository.save(policy);
            assertThat(repository.findById(policy.id())).isPresent();

            // When
            repository.deleteById(policy.id());

            // Then
            assertThat(repository.findById(policy.id())).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsById()")
    class ExistsById {

        @Test
        @DisplayName("should return true when policy exists")
        void shouldReturnTrueWhenPolicyExists() {
            // Given
            PermissionPolicy policy = createTestPolicy("ADMIN", "all.*");
            repository.save(policy);

            // When
            boolean exists = repository.existsById(policy.id());

            // Then
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("should return false when policy does not exist")
        void shouldReturnFalseWhenPolicyDoesNotExist() {
            // When
            boolean exists = repository.existsById(UUID.randomUUID().toString());

            // Then
            assertThat(exists).isFalse();
        }
    }

    private PermissionPolicy createTestPolicy(String roleName, String actionPattern) {
        return PermissionPolicy.create(
            profileId,
            Subject.role(roleName),
            Action.of(actionPattern),
            Resource.all(),
            PermissionPolicy.Effect.ALLOW,
            "Test policy for " + roleName,
            "test-user"
        );
    }
}
