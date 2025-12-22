package com.knight.domain.policy.service;

import com.knight.domain.policy.aggregate.PermissionPolicy;
import com.knight.domain.policy.api.commands.PermissionPolicyCommands;
import com.knight.domain.policy.api.queries.PermissionPolicyQueries;
import com.knight.domain.policy.api.types.PolicyDto;
import com.knight.domain.policy.repository.PermissionPolicyRepository;
import com.knight.domain.policy.types.Action;
import com.knight.domain.policy.types.Resource;
import com.knight.domain.policy.types.Subject;
import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Application service for PermissionPolicy CRUD and query operations.
 */
@Service
@Transactional
public class PermissionPolicyApplicationService implements PermissionPolicyCommands, PermissionPolicyQueries {

    private final PermissionPolicyRepository policyRepository;
    private final PermissionAuthorizationService authorizationService;

    public PermissionPolicyApplicationService(
            PermissionPolicyRepository policyRepository,
            PermissionAuthorizationService authorizationService) {
        this.policyRepository = policyRepository;
        this.authorizationService = authorizationService;
    }

    // ===== Commands =====

    @Override
    public PolicyDto createPolicy(CreatePolicyCmd cmd) {
        // Parse and validate inputs
        Subject subject = Subject.fromUrn(cmd.subjectUrn());
        Action action = Action.of(cmd.actionPattern());
        Resource resource = cmd.resourcePattern() != null
            ? Resource.of(cmd.resourcePattern())
            : Resource.all();
        PermissionPolicy.Effect effect = cmd.effect() != null
            ? PermissionPolicy.Effect.valueOf(cmd.effect().toUpperCase())
            : PermissionPolicy.Effect.ALLOW;

        // Create the policy
        PermissionPolicy policy = PermissionPolicy.create(
            cmd.profileId(),
            subject,
            action,
            resource,
            effect,
            cmd.description(),
            cmd.createdBy()
        );

        policyRepository.save(policy);
        return toDto(policy);
    }

    @Override
    public PolicyDto updatePolicy(UpdatePolicyCmd cmd) {
        PermissionPolicy policy = policyRepository.findById(cmd.policyId())
            .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + cmd.policyId()));

        if (policy.isSystemPolicy()) {
            throw new IllegalStateException("Cannot update system policy");
        }

        Action action = cmd.actionPattern() != null
            ? Action.of(cmd.actionPattern())
            : policy.action();
        Resource resource = cmd.resourcePattern() != null
            ? Resource.of(cmd.resourcePattern())
            : policy.resource();
        PermissionPolicy.Effect effect = cmd.effect() != null
            ? PermissionPolicy.Effect.valueOf(cmd.effect().toUpperCase())
            : policy.effect();

        policy.update(action, resource, effect, cmd.description());
        policyRepository.save(policy);
        return toDto(policy);
    }

    @Override
    public void deletePolicy(DeletePolicyCmd cmd) {
        PermissionPolicy policy = policyRepository.findById(cmd.policyId())
            .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + cmd.policyId()));

        if (policy.isSystemPolicy()) {
            throw new IllegalStateException("Cannot delete system policy");
        }

        policyRepository.deleteById(cmd.policyId());
    }

    // ===== Queries =====

    @Override
    @Transactional(readOnly = true)
    public Optional<PolicyDto> getPolicyById(String policyId) {
        return policyRepository.findById(policyId).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PolicyDto> listPoliciesByProfile(ProfileId profileId) {
        return policyRepository.findByProfileId(profileId).stream()
            .map(this::toDto)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PolicyDto> listPoliciesBySubject(ProfileId profileId, String subjectUrn) {
        Subject subject = Subject.fromUrn(subjectUrn);
        return policyRepository.findByProfileIdAndSubject(profileId, subject).stream()
            .map(this::toDto)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PolicyDto> getEffectivePermissions(ProfileId profileId, UserId userId, Set<String> userRoles) {
        return authorizationService.getEffectivePermissions(profileId, userId, userRoles).stream()
            .map(this::toDto)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AuthorizationResult checkAuthorization(AuthorizationRequest request) {
        Action action = Action.of(request.action());
        String resourceId = request.resourceId() != null ? request.resourceId() : "*";

        PermissionAuthorizationService.PermissionResult result = authorizationService.checkPermission(
            request.profileId(),
            request.userId(),
            request.userRoles(),
            action,
            resourceId
        );

        return new AuthorizationResult(
            result.allowed(),
            result.reason(),
            result.effectiveEffect() != null ? result.effectiveEffect().name() : null
        );
    }

    // ===== Helper Methods =====

    private PolicyDto toDto(PermissionPolicy policy) {
        return new PolicyDto(
            policy.id(),
            policy.profileId() != null ? policy.profileId().urn() : null,
            policy.subject().toUrn(),
            policy.action().value(),
            policy.resource().value(),
            policy.effect().name(),
            policy.description(),
            policy.isSystemPolicy(),
            policy.createdAt(),
            policy.createdBy(),
            policy.updatedAt()
        );
    }
}
