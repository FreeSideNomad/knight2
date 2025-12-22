package com.knight.application.rest.users;

import com.knight.application.rest.users.dto.*;
import com.knight.domain.users.api.commands.UserCommands;
import com.knight.domain.users.api.commands.UserCommands.*;
import com.knight.domain.users.api.queries.UserQueries;
import com.knight.domain.users.api.queries.UserQueries.*;
import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST controller for managing users within profiles.
 */
@RestController
@RequestMapping("/api")
public class ProfileUsersController {

    private static final Logger logger = LoggerFactory.getLogger(ProfileUsersController.class);

    private final UserCommands userCommands;
    private final UserQueries userQueries;

    public ProfileUsersController(UserCommands userCommands, UserQueries userQueries) {
        this.userCommands = userCommands;
        this.userQueries = userQueries;
    }

    // ==================== Profile Users Endpoints ====================

    /**
     * List all users for a profile.
     */
    @GetMapping("/profiles/{profileId}/users")
    public ResponseEntity<List<ProfileUserDto>> listProfileUsers(@PathVariable String profileId) {
        logger.info("Listing users for profile: {}", profileId);

        ProfileId profId = ProfileId.fromUrn(profileId);
        List<ProfileUserSummary> users = userQueries.listUsersByProfile(profId);

        List<ProfileUserDto> dtos = users.stream()
            .map(this::toProfileUserDto)
            .toList();

        return ResponseEntity.ok(dtos);
    }

    /**
     * Add a new user to a profile (Indirect users with Auth0 provisioning).
     */
    @PostMapping("/profiles/{profileId}/users")
    public ResponseEntity<AddUserResponse> addUserToProfile(
        @PathVariable String profileId,
        @RequestBody AddUserRequest request,
        Principal principal
    ) {
        String createdBy = principal != null ? principal.getName() : "system";
        logger.info("Adding user {} to profile {} by {}", request.email(), profileId, createdBy);

        ProfileId profId = ProfileId.fromUrn(profileId);

        // Create the user
        CreateUserCmd createCmd = new CreateUserCmd(
            request.email(),
            request.firstName(),
            request.lastName(),
            "INDIRECT_USER",
            "AUTH0",
            profId,
            request.roles(),
            createdBy
        );

        UserId userId = userCommands.createUser(createCmd);

        // Provision to Auth0
        ProvisionResult provisionResult = userCommands.provisionUser(new ProvisionUserCmd(userId));

        // Get the created user details
        UserDetail userDetail = userQueries.getUserDetail(userId);

        AddUserResponse response = new AddUserResponse(
            userDetail.userId(),
            userDetail.email(),
            userDetail.firstName(),
            userDetail.lastName(),
            userDetail.status(),
            userDetail.roles(),
            provisionResult.passwordResetUrl(),
            userDetail.createdAt()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get user details.
     */
    @GetMapping("/profiles/{profileId}/users/{userId}")
    public ResponseEntity<UserDetailDto> getUserDetail(
        @PathVariable String profileId,
        @PathVariable String userId
    ) {
        logger.info("Getting user {} for profile {}", userId, profileId);

        UserDetail user = userQueries.getUserDetail(UserId.of(userId));

        // Verify user belongs to profile
        if (!user.profileId().equals(profileId)) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(toUserDetailDto(user));
    }

    /**
     * Get user count by status for a profile.
     */
    @GetMapping("/profiles/{profileId}/users/counts")
    public ResponseEntity<Map<String, Integer>> getUserCounts(@PathVariable String profileId) {
        logger.info("Getting user counts for profile: {}", profileId);

        ProfileId profId = ProfileId.fromUrn(profileId);
        Map<String, Integer> counts = userQueries.countUsersByStatusForProfile(profId);

        return ResponseEntity.ok(counts);
    }

    // ==================== User Management Endpoints ====================

    /**
     * Resend invitation (password reset email) to user.
     */
    @PostMapping("/users/{userId}/resend-invitation")
    public ResponseEntity<Map<String, String>> resendInvitation(@PathVariable String userId) {
        logger.info("Resending invitation for user: {}", userId);

        String resetUrl = userCommands.resendInvitation(new ResendInvitationCmd(UserId.of(userId)));

        return ResponseEntity.ok(Map.of("passwordResetUrl", resetUrl));
    }

    /**
     * Lock a user.
     */
    @PutMapping("/users/{userId}/lock")
    public ResponseEntity<Void> lockUser(
        @PathVariable String userId,
        @RequestBody LockUserRequest request
    ) {
        logger.info("Locking user: {}", userId);

        userCommands.lockUser(new LockUserCmd(UserId.of(userId), request.reason()));

        return ResponseEntity.noContent().build();
    }

    /**
     * Unlock a user.
     */
    @PutMapping("/users/{userId}/unlock")
    public ResponseEntity<Void> unlockUser(@PathVariable String userId) {
        logger.info("Unlocking user: {}", userId);

        userCommands.unlockUser(new UnlockUserCmd(UserId.of(userId)));

        return ResponseEntity.noContent().build();
    }

    /**
     * Deactivate a user.
     */
    @PutMapping("/users/{userId}/deactivate")
    public ResponseEntity<Void> deactivateUser(
        @PathVariable String userId,
        @RequestBody DeactivateUserRequest request
    ) {
        logger.info("Deactivating user: {}", userId);

        userCommands.deactivateUser(new DeactivateUserCmd(UserId.of(userId), request.reason()));

        return ResponseEntity.noContent().build();
    }

    /**
     * Activate a user.
     */
    @PutMapping("/users/{userId}/activate")
    public ResponseEntity<Void> activateUser(@PathVariable String userId) {
        logger.info("Activating user: {}", userId);

        userCommands.activateUser(new ActivateUserCmd(UserId.of(userId)));

        return ResponseEntity.noContent().build();
    }

    /**
     * Update user name.
     */
    @PutMapping("/users/{userId}")
    public ResponseEntity<UserDetailDto> updateUser(
        @PathVariable String userId,
        @RequestBody UpdateUserRequest request
    ) {
        logger.info("Updating user: {}", userId);

        userCommands.updateUserName(new UpdateUserNameCmd(
            UserId.of(userId),
            request.firstName(),
            request.lastName()
        ));

        UserDetail updated = userQueries.getUserDetail(UserId.of(userId));
        return ResponseEntity.ok(toUserDetailDto(updated));
    }

    // ==================== Role Management Endpoints ====================

    /**
     * Add a role to a user.
     */
    @PostMapping("/users/{userId}/roles")
    public ResponseEntity<Void> addRole(
        @PathVariable String userId,
        @RequestBody RoleRequest request
    ) {
        logger.info("Adding role {} to user {}", request.role(), userId);

        userCommands.addRole(new AddRoleCmd(UserId.of(userId), request.role()));

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Remove a role from a user.
     */
    @DeleteMapping("/users/{userId}/roles/{role}")
    public ResponseEntity<Void> removeRole(
        @PathVariable String userId,
        @PathVariable String role
    ) {
        logger.info("Removing role {} from user {}", role, userId);

        userCommands.removeRole(new RemoveRoleCmd(UserId.of(userId), role));

        return ResponseEntity.noContent().build();
    }

    // ==================== Helper Methods ====================

    private ProfileUserDto toProfileUserDto(ProfileUserSummary summary) {
        boolean canResendInvitation = "PENDING_VERIFICATION".equals(summary.status());
        boolean canLock = "ACTIVE".equals(summary.status());
        boolean canDeactivate = "ACTIVE".equals(summary.status()) || "LOCKED".equals(summary.status());

        return new ProfileUserDto(
            summary.userId(),
            summary.email(),
            summary.firstName(),
            summary.lastName(),
            summary.status(),
            summary.statusDisplayName(),
            summary.roles(),
            canResendInvitation,
            canLock,
            canDeactivate,
            summary.createdAt(),
            summary.lastLogin()
        );
    }

    private UserDetailDto toUserDetailDto(UserDetail detail) {
        return new UserDetailDto(
            detail.userId(),
            detail.email(),
            detail.firstName(),
            detail.lastName(),
            detail.status(),
            detail.userType(),
            detail.identityProvider(),
            detail.profileId(),
            detail.identityProviderUserId(),
            detail.roles(),
            detail.passwordSet(),
            detail.mfaEnrolled(),
            detail.createdAt(),
            detail.createdBy(),
            detail.lastSyncedAt(),
            detail.lockReason(),
            detail.deactivationReason()
        );
    }
}
