package com.knight.domain.auth0identity.adapter;

import com.knight.domain.auth0identity.adapter.dto.*;
import com.knight.domain.auth0identity.api.Auth0IdentityService;
import com.knight.domain.auth0identity.api.Auth0IntegrationException;
import com.knight.domain.auth0identity.api.UserAlreadyExistsException;
import com.knight.domain.auth0identity.api.events.Auth0UserBlocked;
import com.knight.domain.auth0identity.api.events.Auth0UserCreated;
import com.knight.domain.auth0identity.api.events.Auth0UserLinked;
import com.knight.domain.auth0identity.config.Auth0Config;
import com.knight.platform.sharedkernel.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * Auth0 Identity Adapter implementation.
 * Implements the anti-corruption layer for Auth0 identity operations.
 * This adapter translates between domain concepts and Auth0 API calls.
 */
@Service
public class Auth0IdentityAdapter implements Auth0IdentityService {

    private static final Logger log = LoggerFactory.getLogger(Auth0IdentityAdapter.class);
    private static final int PASSWORD_RESET_TTL_SECONDS = 604800; // 7 days

    private final Auth0Config config;
    private final Auth0HttpClient httpClient;
    private final ApplicationEventPublisher eventPublisher;
    private final SecureRandom secureRandom = new SecureRandom();

    public Auth0IdentityAdapter(
        Auth0Config config,
        Auth0HttpClient httpClient,
        ApplicationEventPublisher eventPublisher
    ) {
        this.config = config;
        this.httpClient = httpClient;
        this.eventPublisher = eventPublisher;
    }

    // ==================== User Provisioning ====================

    @Override
    public ProvisionUserResult provisionUser(ProvisionUserRequest request) {
        log.info("Provisioning user with loginId: {}", request.loginId());

        // 1. Check if user already exists (using loginId as Auth0 email)
        Optional<Auth0UserInfo> existing = getUserByEmail(request.loginId());
        if (existing.isPresent()) {
            throw new UserAlreadyExistsException(request.loginId(), existing.get().auth0UserId());
        }

        // 2. Generate temporary password
        String tempPassword = generateSecurePassword();

        // 3. Build user creation request
        // Note: loginId is used as Auth0 email field (must be valid email format)
        // Real email is stored only in local database for OTP delivery
        String fullName = buildFullName(request.firstName(), request.lastName());

        var createRequest = new Auth0CreateUserRequest(
            request.loginId(),   // Use loginId as Auth0 email field
            config.connection(),
            tempPassword,
            fullName,
            request.firstName(),
            request.lastName(),
            true,   // emailVerifiedStatus - set to true (we verify via our own OTP)
            false,  // triggerEmailVerificationOnCreate - don't send Auth0 verification email
            new Auth0CreateUserRequest.AppMetadata(
                request.internalUserId(),
                request.profileId(),
                "knight_platform",
                Instant.now().toString(),
                "pending",
                false
            )
        );

        // 4. Create user in Auth0
        Auth0UserResponse userResponse = httpClient.post(
            "/users",
            createRequest,
            Auth0UserResponse.class
        );

        if (userResponse == null || userResponse.userId() == null) {
            throw new Auth0IntegrationException("Failed to create user in Auth0");
        }

        String auth0UserId = userResponse.userId();
        log.info("User created in Auth0: {}", auth0UserId);

        // 5. Create password change ticket
        var ticketRequest = new Auth0PasswordChangeTicketRequest(
            auth0UserId,
            config.getPasswordResetResultUrl(),
            PASSWORD_RESET_TTL_SECONDS,
            true  // Mark email as verified after password set
        );

        Auth0PasswordChangeTicketResponse ticketResponse = httpClient.post(
            "/tickets/password-change",
            ticketRequest,
            Auth0PasswordChangeTicketResponse.class
        );

        String resetUrl = ticketResponse != null ? ticketResponse.ticket() : null;
        log.info("Password reset ticket created for user: {}", auth0UserId);

        // 6. Publish domain event
        eventPublisher.publishEvent(new Auth0UserCreated(
            auth0UserId,
            request.loginId(),  // Use loginId as Auth0 email
            fullName,
            Instant.now()
        ));

        return new ProvisionUserResult(auth0UserId, resetUrl, Instant.now());
    }

    @Override
    public OnboardingStatus getOnboardingStatus(String identityProviderUserId) {
        log.debug("Getting onboarding status for: {}", identityProviderUserId);

        Auth0UserResponse user = httpClient.get(
            "/users/" + identityProviderUserId,
            Auth0UserResponse.class
        );

        if (user == null) {
            throw new Auth0IntegrationException("User not found: " + identityProviderUserId);
        }

        // Check MFA enrollments
        Auth0MfaEnrollment[] enrollments = httpClient.get(
            "/users/" + identityProviderUserId + "/enrollments",
            Auth0MfaEnrollment[].class
        );

        boolean mfaEnrolled = enrollments != null && enrollments.length > 0;
        boolean passwordSet = user.emailVerifiedStatus(); // Proxy: verified after password reset

        OnboardingState state = determineOnboardingState(passwordSet, mfaEnrolled);

        return new OnboardingStatus(
            identityProviderUserId,
            passwordSet,
            mfaEnrolled,
            state,
            user.lastLogin() != null ? Instant.parse(user.lastLogin()) : null
        );
    }

    @Override
    public String resendPasswordResetEmail(String identityProviderUserId) {
        log.info("Resending password reset email for: {}", identityProviderUserId);

        var ticketRequest = new Auth0PasswordChangeTicketRequest(
            identityProviderUserId,
            config.getPasswordResetResultUrl(),
            PASSWORD_RESET_TTL_SECONDS,
            true
        );

        Auth0PasswordChangeTicketResponse ticketResponse = httpClient.post(
            "/tickets/password-change",
            ticketRequest,
            Auth0PasswordChangeTicketResponse.class
        );

        return ticketResponse != null ? ticketResponse.ticket() : null;
    }

    // ==================== Basic CRUD Operations ====================

    @Override
    public String createUser(CreateAuth0UserRequest request) {
        log.info("Creating user: {}", request.email());

        var auth0Request = new Auth0CreateUserRequest(
            request.email(),
            request.connection(),
            request.password(),
            request.name(),
            null,  // givenName
            null,  // familyName
            request.emailVerified(),
            false, // triggerEmailVerificationOnCreate
            null   // appMetadata
        );

        Auth0UserResponse response = httpClient.post(
            "/users",
            auth0Request,
            Auth0UserResponse.class
        );

        if (response == null || response.userId() == null) {
            throw new Auth0IntegrationException("Failed to create user in Auth0");
        }

        String auth0UserId = response.userId();

        eventPublisher.publishEvent(new Auth0UserCreated(
            auth0UserId,
            request.email(),
            request.name(),
            Instant.now()
        ));

        return auth0UserId;
    }

    @Override
    public Optional<Auth0UserInfo> getUser(String auth0UserId) {
        log.debug("Getting user: {}", auth0UserId);

        try {
            Auth0UserResponse user = httpClient.get(
                "/users/" + auth0UserId,
                Auth0UserResponse.class
            );
            return Optional.ofNullable(user).map(this::mapToUserInfo);
        } catch (Auth0IntegrationException e) {
            log.debug("User not found: {}", auth0UserId);
            return Optional.empty();
        }
    }

    @Override
    public Optional<Auth0UserInfo> getUserByEmail(String email) {
        log.debug("Getting user by email: {}", email);

        // Don't URL encode here - the HTTP client handles it
        Auth0UserResponse[] users = httpClient.getWithQueryParam(
            "/users-by-email",
            "email",
            email,
            Auth0UserResponse[].class
        );

        if (users == null || users.length == 0) {
            log.debug("No user found by email: {}", email);
            return Optional.empty();
        }

        log.debug("Found user by email: {} -> {}", email, users[0].userId());
        return Optional.of(mapToUserInfo(users[0]));
    }

    @Override
    public void updateUser(String auth0UserId, UpdateAuth0UserRequest request) {
        log.info("Updating user: {}", auth0UserId);

        httpClient.patch(
            "/users/" + auth0UserId,
            request,
            Auth0UserResponse.class
        );
    }

    @Override
    public void blockUser(String auth0UserId) {
        log.info("Blocking user: {}", auth0UserId);

        httpClient.patch(
            "/users/" + auth0UserId,
            Map.of("blocked", true),
            Auth0UserResponse.class
        );

        eventPublisher.publishEvent(new Auth0UserBlocked(
            auth0UserId,
            Instant.now()
        ));
    }

    @Override
    public void unblockUser(String auth0UserId) {
        log.info("Unblocking user: {}", auth0UserId);

        httpClient.patch(
            "/users/" + auth0UserId,
            Map.of("blocked", false),
            Auth0UserResponse.class
        );
    }

    @Override
    public void deleteUser(String auth0UserId) {
        log.info("Deleting user: {}", auth0UserId);

        httpClient.delete("/users/" + auth0UserId);
    }

    @Override
    public void sendPasswordResetEmail(String email) {
        log.info("Sending password reset email to: {}", email);

        // Use the Authentication API endpoint
        // This is different from the Management API
        httpClient.post(
            "/dbconnections/change_password",
            Map.of(
                "client_id", config.clientId(),
                "email", email,
                "connection", config.connection()
            ),
            String.class
        );
    }

    @Override
    public void linkToInternalUser(String auth0UserId, UserId internalUserId) {
        log.info("Linking Auth0 user {} to internal user {}", auth0UserId, internalUserId.id());

        httpClient.patch(
            "/users/" + auth0UserId,
            Map.of("app_metadata", Map.of("internal_user_id", internalUserId.id())),
            Auth0UserResponse.class
        );

        eventPublisher.publishEvent(new Auth0UserLinked(
            auth0UserId,
            internalUserId.id(),
            Instant.now()
        ));
    }

    // ==================== MFA Management ====================

    @Override
    public void deleteAllMfaEnrollments(String auth0UserId) {
        log.info("Deleting all MFA enrollments for user: {}", auth0UserId);

        // Get all MFA enrollments via Management API
        Auth0MfaEnrollment[] enrollments = httpClient.get(
            "/users/" + auth0UserId + "/enrollments",
            Auth0MfaEnrollment[].class
        );

        if (enrollments == null || enrollments.length == 0) {
            log.info("No MFA enrollments found for user: {}", auth0UserId);
            return;
        }

        // Delete each enrollment
        for (Auth0MfaEnrollment enrollment : enrollments) {
            try {
                log.info("Deleting MFA enrollment: {} for user: {}", enrollment.id(), auth0UserId);
                httpClient.delete("/users/" + auth0UserId + "/authenticators/" + enrollment.id());
            } catch (Exception e) {
                // Log but continue - enrollment may already be deleted
                log.warn("Failed to delete MFA enrollment {}: {}", enrollment.id(), e.getMessage());
            }
        }

        log.info("Deleted {} MFA enrollments for user: {}", enrollments.length, auth0UserId);
    }

    // ==================== Helper Methods ====================

    private OnboardingState determineOnboardingState(boolean passwordSet, boolean mfaEnrolled) {
        if (!passwordSet) {
            return OnboardingState.PENDING_PASSWORD;
        } else if (!mfaEnrolled) {
            return OnboardingState.PENDING_MFA;
        } else {
            return OnboardingState.COMPLETE;
        }
    }

    private Auth0UserInfo mapToUserInfo(Auth0UserResponse response) {
        return new Auth0UserInfo(
            response.userId(),
            response.email(),
            response.name(),
            response.emailVerifiedStatus(),
            response.blocked(),
            response.picture(),
            response.lastLogin()
        );
    }

    private String generateSecurePassword() {
        // Generate a 24-character random password that meets Auth0's "excellent" policy
        byte[] randomBytes = new byte[18];
        secureRandom.nextBytes(randomBytes);
        String base = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        // Ensure we have special characters
        return base + "!@#";
    }

    private String buildFullName(String firstName, String lastName) {
        if (firstName == null && lastName == null) return "";
        if (firstName == null) return lastName;
        if (lastName == null) return firstName;
        return firstName + " " + lastName;
    }
}
