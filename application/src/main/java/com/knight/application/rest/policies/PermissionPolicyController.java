package com.knight.application.rest.policies;

import com.knight.application.rest.policies.dto.*;
import com.knight.domain.policy.api.commands.PermissionPolicyCommands;
import com.knight.domain.policy.api.commands.PermissionPolicyCommands.*;
import com.knight.domain.policy.api.queries.PermissionPolicyQueries;
import com.knight.domain.policy.api.queries.PermissionPolicyQueries.*;
import com.knight.domain.policy.api.types.PolicyDto;
import com.knight.domain.policy.service.PermissionAuthorizationService;
import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Set;

/**
 * REST controller for managing permission policies.
 */
@RestController
@RequestMapping("/api")
public class PermissionPolicyController {

    private static final Logger logger = LoggerFactory.getLogger(PermissionPolicyController.class);

    private final PermissionPolicyCommands policyCommands;
    private final PermissionPolicyQueries policyQueries;
    private final PermissionAuthorizationService authorizationService;

    public PermissionPolicyController(
            PermissionPolicyCommands policyCommands,
            PermissionPolicyQueries policyQueries,
            PermissionAuthorizationService authorizationService) {
        this.policyCommands = policyCommands;
        this.policyQueries = policyQueries;
        this.authorizationService = authorizationService;
    }

    // ==================== Permission Policy CRUD ====================

    /**
     * List all permission policies for a profile.
     */
    @GetMapping("/profiles/{profileId}/permission-policies")
    public ResponseEntity<List<PermissionPolicyDto>> listPolicies(@PathVariable String profileId) {
        logger.info("Listing permission policies for profile: {}", profileId);

        ProfileId profId = ProfileId.fromUrn(profileId);
        List<PolicyDto> policies = policyQueries.listPoliciesByProfile(profId);

        List<PermissionPolicyDto> dtos = policies.stream()
            .map(this::toDto)
            .toList();

        return ResponseEntity.ok(dtos);
    }

    /**
     * Get a specific permission policy.
     */
    @GetMapping("/profiles/{profileId}/permission-policies/{policyId}")
    public ResponseEntity<PermissionPolicyDto> getPolicy(
            @PathVariable String profileId,
            @PathVariable String policyId) {
        logger.info("Getting permission policy {} for profile {}", policyId, profileId);

        return policyQueries.getPolicyById(policyId)
            .filter(p -> p.profileId() != null && p.profileId().equals(profileId))
            .map(this::toDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a new permission policy.
     */
    @PostMapping("/profiles/{profileId}/permission-policies")
    public ResponseEntity<PermissionPolicyDto> createPolicy(
            @PathVariable String profileId,
            @RequestBody CreatePermissionPolicyRequest request,
            Principal principal) {
        String createdBy = principal != null ? principal.getName() : "system";
        logger.info("Creating permission policy for profile {} by {}", profileId, createdBy);

        ProfileId profId = ProfileId.fromUrn(profileId);

        CreatePolicyCmd cmd = new CreatePolicyCmd(
            profId,
            request.subject(),
            request.action(),
            request.resource(),
            request.effect(),
            request.description(),
            createdBy
        );

        PolicyDto policy = policyCommands.createPolicy(cmd);

        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(policy));
    }

    /**
     * Update a permission policy.
     */
    @PutMapping("/profiles/{profileId}/permission-policies/{policyId}")
    public ResponseEntity<PermissionPolicyDto> updatePolicy(
            @PathVariable String profileId,
            @PathVariable String policyId,
            @RequestBody UpdatePermissionPolicyRequest request) {
        logger.info("Updating permission policy {} for profile {}", policyId, profileId);

        // Verify policy belongs to profile
        var existingPolicy = policyQueries.getPolicyById(policyId);
        if (existingPolicy.isEmpty() ||
            existingPolicy.get().profileId() == null ||
            !existingPolicy.get().profileId().equals(profileId)) {
            return ResponseEntity.notFound().build();
        }

        UpdatePolicyCmd cmd = new UpdatePolicyCmd(
            policyId,
            request.action(),
            request.resource(),
            request.effect(),
            request.description()
        );

        PolicyDto policy = policyCommands.updatePolicy(cmd);

        return ResponseEntity.ok(toDto(policy));
    }

    /**
     * Delete a permission policy.
     */
    @DeleteMapping("/profiles/{profileId}/permission-policies/{policyId}")
    public ResponseEntity<Void> deletePolicy(
            @PathVariable String profileId,
            @PathVariable String policyId) {
        logger.info("Deleting permission policy {} for profile {}", policyId, profileId);

        // Verify policy belongs to profile
        var existingPolicy = policyQueries.getPolicyById(policyId);
        if (existingPolicy.isEmpty() ||
            existingPolicy.get().profileId() == null ||
            !existingPolicy.get().profileId().equals(profileId)) {
            return ResponseEntity.notFound().build();
        }

        policyCommands.deletePolicy(new DeletePolicyCmd(policyId));

        return ResponseEntity.noContent().build();
    }

    // ==================== Authorization Endpoints ====================

    /**
     * Check if user can perform an action.
     */
    @PostMapping("/profiles/{profileId}/authorize")
    public ResponseEntity<AuthorizeResponse> checkAuthorization(
            @PathVariable String profileId,
            @RequestBody AuthorizeRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader) {
        logger.info("Checking authorization for profile {} action {}", profileId, request.action());

        if (userId == null || rolesHeader == null) {
            return ResponseEntity.badRequest().body(
                new AuthorizeResponse(false, "Missing X-User-Id or X-User-Roles header", null)
            );
        }

        ProfileId profId = ProfileId.fromUrn(profileId);
        UserId uid = UserId.of(userId);
        Set<String> roles = Set.of(rolesHeader.split(","));

        AuthorizationRequest authRequest = new AuthorizationRequest(
            profId,
            uid,
            roles,
            request.action(),
            request.resourceId()
        );

        AuthorizationResult result = policyQueries.checkAuthorization(authRequest);

        return ResponseEntity.ok(new AuthorizeResponse(
            result.allowed(),
            result.reason(),
            result.effectiveEffect()
        ));
    }

    /**
     * Get effective permissions for a user.
     */
    @GetMapping("/profiles/{profileId}/users/{userId}/permissions")
    public ResponseEntity<EffectivePermissionsResponse> getUserPermissions(
            @PathVariable String profileId,
            @PathVariable String userId,
            @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader) {
        logger.info("Getting permissions for user {} in profile {}", userId, profileId);

        if (rolesHeader == null) {
            return ResponseEntity.badRequest().build();
        }

        ProfileId profId = ProfileId.fromUrn(profileId);
        UserId uid = UserId.of(userId);
        Set<String> roles = Set.of(rolesHeader.split(","));

        List<PolicyDto> policies = policyQueries.getEffectivePermissions(profId, uid, roles);
        Set<String> allowedActions = authorizationService.getAllowedActions(profId, uid, roles);

        List<PermissionPolicyDto> policyDtos = policies.stream()
            .map(this::toDto)
            .toList();

        return ResponseEntity.ok(new EffectivePermissionsResponse(
            userId,
            roles,
            policyDtos,
            allowedActions
        ));
    }

    // ==================== Helper Methods ====================

    private PermissionPolicyDto toDto(PolicyDto policy) {
        return new PermissionPolicyDto(
            policy.id(),
            policy.profileId(),
            policy.subjectUrn(),
            policy.actionPattern(),
            policy.resourcePattern(),
            policy.effect(),
            policy.description(),
            policy.systemPolicy(),
            policy.createdAt(),
            policy.createdBy(),
            policy.updatedAt()
        );
    }
}
