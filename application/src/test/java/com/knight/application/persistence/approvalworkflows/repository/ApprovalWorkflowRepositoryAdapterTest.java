package com.knight.application.persistence.approvalworkflows.repository;

import com.knight.domain.approvalworkflows.aggregate.ApprovalWorkflow;
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
    "spring.kafka.listener.auto-startup=false",
    "spring.kafka.consumer.auto-startup=false"
})
class ApprovalWorkflowRepositoryAdapterTest {

    @Autowired
    private ApprovalWorkflowRepositoryAdapter repository;

    @Autowired
    private ApprovalWorkflowJpaRepository jpaRepository;

    @BeforeEach
    void setUp() {
        jpaRepository.deleteAll();
    }

    @Nested
    @DisplayName("save()")
    class SaveTests {

        @Test
        @DisplayName("should save new workflow")
        void shouldSaveNewWorkflow() {
            ApprovalWorkflow workflow = ApprovalWorkflow.initiate(
                "EXPENSE",
                "exp-001",
                2,
                "user:requester"
            );

            repository.save(workflow);

            assertThat(jpaRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should save workflow with approvals")
        void shouldSaveWorkflowWithApprovals() {
            ApprovalWorkflow workflow = ApprovalWorkflow.initiate(
                "PURCHASE",
                "po-002",
                2,
                "user:buyer"
            );
            workflow.recordApproval("user:manager", ApprovalWorkflow.Decision.APPROVE, "OK");

            repository.save(workflow);

            Optional<ApprovalWorkflow> found = repository.findById(workflow.id());
            assertThat(found).isPresent();
            assertThat(found.get().approvals()).hasSize(1);
        }

        @Test
        @DisplayName("should update existing workflow")
        void shouldUpdateExistingWorkflow() {
            ApprovalWorkflow workflow = ApprovalWorkflow.initiate(
                "CONTRACT",
                "con-003",
                1,
                "user:legal"
            );
            repository.save(workflow);

            // Add approval and save again
            workflow.recordApproval("user:director", ApprovalWorkflow.Decision.APPROVE, "Approved");
            repository.save(workflow);

            Optional<ApprovalWorkflow> found = repository.findById(workflow.id());
            assertThat(found).isPresent();
            assertThat(found.get().status()).isEqualTo(ApprovalWorkflow.Status.APPROVED);
        }
    }

    @Nested
    @DisplayName("findById()")
    class FindByIdTests {

        @Test
        @DisplayName("should find existing workflow")
        void shouldFindExistingWorkflow() {
            ApprovalWorkflow workflow = ApprovalWorkflow.initiate(
                "INVOICE",
                "inv-004",
                1,
                "user:vendor"
            );
            repository.save(workflow);

            Optional<ApprovalWorkflow> found = repository.findById(workflow.id());

            assertThat(found).isPresent();
            assertThat(found.get().resourceType()).isEqualTo("INVOICE");
            assertThat(found.get().resourceId()).isEqualTo("inv-004");
        }

        @Test
        @DisplayName("should return empty for non-existing workflow")
        void shouldReturnEmptyForNonExistingWorkflow() {
            Optional<ApprovalWorkflow> found = repository.findById("00000000-0000-0000-0000-000000000000");

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("should reconstruct approvals correctly")
        void shouldReconstructApprovalsCorrectly() {
            ApprovalWorkflow workflow = ApprovalWorkflow.initiate(
                "TIMESHEET",
                "ts-005",
                2,
                "user:employee"
            );
            workflow.recordApproval("user:supervisor", ApprovalWorkflow.Decision.APPROVE, "Hours verified");
            repository.save(workflow);

            Optional<ApprovalWorkflow> found = repository.findById(workflow.id());

            assertThat(found).isPresent();
            assertThat(found.get().approvals()).hasSize(1);
            ApprovalWorkflow.Approval approval = found.get().approvals().get(0);
            assertThat(approval.approverUserId()).isEqualTo("user:supervisor");
            assertThat(approval.comments()).isEqualTo("Hours verified");
            assertThat(approval.decision()).isEqualTo(ApprovalWorkflow.Decision.APPROVE);
        }
    }
}
