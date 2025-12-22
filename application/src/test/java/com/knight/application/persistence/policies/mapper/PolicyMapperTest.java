package com.knight.application.persistence.policies.mapper;

import com.knight.application.persistence.policies.entity.PolicyEntity;
import com.knight.domain.policy.aggregate.Policy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PolicyMapper.
 */
class PolicyMapperTest {

    private PolicyMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new PolicyMapper();
    }

    @Nested
    @DisplayName("Domain to Entity Mapping")
    class DomainToEntityTests {

        @Test
        @DisplayName("should map permission policy to entity")
        void shouldMapPermissionPolicyToEntity() {
            Policy policy = Policy.create(
                Policy.PolicyType.PERMISSION,
                "user:admin",
                "read",
                "resource:/api/*",
                null
            );

            PolicyEntity entity = mapper.toEntity(policy);

            assertThat(entity.getId()).isEqualTo(UUID.fromString(policy.id()));
            assertThat(entity.getPolicyType()).isEqualTo("PERMISSION");
            assertThat(entity.getSubject()).isEqualTo("user:admin");
            assertThat(entity.getAction()).isEqualTo("read");
            assertThat(entity.getResource()).isEqualTo("resource:/api/*");
            assertThat(entity.getApproverCount()).isNull();
            assertThat(entity.getCreatedAt()).isNotNull();
            assertThat(entity.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("should map approval policy to entity with approver count")
        void shouldMapApprovalPolicyToEntity() {
            Policy policy = Policy.create(
                Policy.PolicyType.APPROVAL,
                "role:manager",
                "approve",
                "transaction:high-value",
                3
            );

            PolicyEntity entity = mapper.toEntity(policy);

            assertThat(entity.getPolicyType()).isEqualTo("APPROVAL");
            assertThat(entity.getApproverCount()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Entity to Domain Mapping")
    class EntityToDomainTests {

        @Test
        @DisplayName("should map entity to permission policy")
        void shouldMapEntityToPermissionPolicy() {
            PolicyEntity entity = new PolicyEntity();
            entity.setId(UUID.randomUUID());
            entity.setPolicyType("PERMISSION");
            entity.setSubject("user:operator");
            entity.setAction("execute");
            entity.setResource("job:batch-*");
            entity.setApproverCount(null);
            entity.setCreatedAt(Instant.now().minusSeconds(3600));
            entity.setUpdatedAt(Instant.now());

            Policy policy = mapper.toDomain(entity);

            assertThat(policy.id()).isEqualTo(entity.getId().toString());
            assertThat(policy.policyType()).isEqualTo(Policy.PolicyType.PERMISSION);
            assertThat(policy.subject()).isEqualTo("user:operator");
            assertThat(policy.action()).isEqualTo("execute");
            assertThat(policy.resource()).isEqualTo("job:batch-*");
            assertThat(policy.approverCount()).isNull();
            assertThat(policy.createdAt()).isEqualTo(entity.getCreatedAt());
            assertThat(policy.updatedAt()).isEqualTo(entity.getUpdatedAt());
        }

        @Test
        @DisplayName("should map entity to approval policy with approver count")
        void shouldMapEntityToApprovalPolicy() {
            PolicyEntity entity = new PolicyEntity();
            entity.setId(UUID.randomUUID());
            entity.setPolicyType("APPROVAL");
            entity.setSubject("department:finance");
            entity.setAction("approve");
            entity.setResource("expense:>10000");
            entity.setApproverCount(2);
            entity.setCreatedAt(Instant.now().minusSeconds(3600));
            entity.setUpdatedAt(Instant.now());

            Policy policy = mapper.toDomain(entity);

            assertThat(policy.policyType()).isEqualTo(Policy.PolicyType.APPROVAL);
            assertThat(policy.approverCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Round-trip Mapping")
    class RoundTripTests {

        @Test
        @DisplayName("should preserve permission policy data in round-trip")
        void shouldPreservePermissionPolicyInRoundTrip() {
            Policy original = Policy.create(
                Policy.PolicyType.PERMISSION,
                "service:payment",
                "invoke",
                "api:/payments/*",
                null
            );

            PolicyEntity entity = mapper.toEntity(original);
            Policy reconstructed = mapper.toDomain(entity);

            assertThat(reconstructed.id()).isEqualTo(original.id());
            assertThat(reconstructed.policyType()).isEqualTo(original.policyType());
            assertThat(reconstructed.subject()).isEqualTo(original.subject());
            assertThat(reconstructed.action()).isEqualTo(original.action());
            assertThat(reconstructed.resource()).isEqualTo(original.resource());
            assertThat(reconstructed.approverCount()).isEqualTo(original.approverCount());
        }

        @Test
        @DisplayName("should preserve approval policy data in round-trip")
        void shouldPreserveApprovalPolicyInRoundTrip() {
            Policy original = Policy.create(
                Policy.PolicyType.APPROVAL,
                "team:risk",
                "review",
                "trade:derivative",
                5
            );

            PolicyEntity entity = mapper.toEntity(original);
            Policy reconstructed = mapper.toDomain(entity);

            assertThat(reconstructed.id()).isEqualTo(original.id());
            assertThat(reconstructed.policyType()).isEqualTo(original.policyType());
            assertThat(reconstructed.approverCount()).isEqualTo(original.approverCount());
        }
    }
}
