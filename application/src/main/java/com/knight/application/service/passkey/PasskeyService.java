package com.knight.application.service.passkey;

import com.knight.domain.users.aggregate.Passkey;
import com.knight.domain.users.aggregate.User;
import com.knight.domain.users.repository.PasskeyRepository;
import com.knight.domain.users.repository.UserRepository;
import com.knight.platform.sharedkernel.UserId;
import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.authenticator.Authenticator;
import com.webauthn4j.authenticator.AuthenticatorImpl;
import com.webauthn4j.converter.AttestationObjectConverter;
import com.webauthn4j.converter.CollectedClientDataConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.*;
import com.webauthn4j.data.attestation.AttestationObject;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.Challenge;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.server.ServerProperty;
import com.webauthn4j.verifier.exception.VerificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for WebAuthn/Passkey operations.
 * Handles registration and authentication ceremonies using WebAuthn4j.
 */
@Service
public class PasskeyService {

    private static final Logger log = LoggerFactory.getLogger(PasskeyService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PasskeyProperties properties;
    private final PasskeyRepository passkeyRepository;
    private final UserRepository userRepository;
    private final WebAuthnManager webAuthnManager;
    private final ObjectConverter objectConverter;

    // In-memory challenge storage (should be Redis in production)
    private final Map<String, ChallengeRecord> challengeStore = new ConcurrentHashMap<>();

    private record ChallengeRecord(
        Challenge challenge,
        UserId userId,
        CeremonyType ceremonyType,
        long expiresAt
    ) {}

    public enum CeremonyType {
        REGISTRATION,
        AUTHENTICATION
    }

    public PasskeyService(
            PasskeyProperties properties,
            PasskeyRepository passkeyRepository,
            UserRepository userRepository) {
        this.properties = properties;
        this.passkeyRepository = passkeyRepository;
        this.userRepository = userRepository;
        this.objectConverter = new ObjectConverter();
        this.webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager();
    }

    /**
     * Generate registration options for passkey enrollment.
     */
    public RegistrationOptionsResponse generateRegistrationOptions(UserId userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // Check max passkeys limit
        int existingCount = passkeyRepository.countByUserId(userId);
        if (existingCount >= properties.getMaxPasskeysPerUser()) {
            throw new IllegalStateException("Maximum passkeys limit reached");
        }

        // Generate challenge
        byte[] challengeBytes = new byte[32];
        SECURE_RANDOM.nextBytes(challengeBytes);
        Challenge challenge = new DefaultChallenge(challengeBytes);

        // Store challenge for verification
        String challengeId = UUID.randomUUID().toString();
        challengeStore.put(challengeId, new ChallengeRecord(
            challenge,
            userId,
            CeremonyType.REGISTRATION,
            System.currentTimeMillis() + properties.getTimeout()
        ));

        // Get existing credential IDs to exclude
        List<String> excludeCredentials = passkeyRepository.findByUserId(userId)
            .stream()
            .map(Passkey::credentialId)
            .collect(Collectors.toList());

        return new RegistrationOptionsResponse(
            challengeId,
            Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes),
            properties.getRpId(),
            properties.getRpName(),
            user.id().id(),
            user.loginId(),
            user.email(),
            properties.getTimeout(),
            properties.getAttestation(),
            parseAuthenticatorAttachment(),
            properties.getResidentKey(),
            properties.getUserVerification(),
            excludeCredentials
        );
    }

    /**
     * Complete passkey registration.
     */
    @Transactional
    public RegistrationResult completeRegistration(
            String challengeId,
            String clientDataJSON,
            String attestationObject,
            String[] transports,
            String displayName) {

        // Retrieve and validate challenge
        ChallengeRecord record = challengeStore.remove(challengeId);
        if (record == null) {
            return RegistrationResult.failure("Invalid or expired challenge");
        }
        if (System.currentTimeMillis() > record.expiresAt()) {
            return RegistrationResult.failure("Challenge expired");
        }
        if (record.ceremonyType() != CeremonyType.REGISTRATION) {
            return RegistrationResult.failure("Invalid ceremony type");
        }

        try {
            // Decode inputs
            byte[] clientDataBytes = Base64.getUrlDecoder().decode(clientDataJSON);
            byte[] attestationBytes = Base64.getUrlDecoder().decode(attestationObject);

            // Build server property
            Set<Origin> origins = Arrays.stream(properties.getAllowedOrigins())
                .map(Origin::new)
                .collect(Collectors.toSet());

            ServerProperty serverProperty = new ServerProperty(
                origins,
                properties.getRpId(),
                record.challenge(),
                null  // No token binding
            );

            // Parse registration request
            RegistrationRequest registrationRequest = new RegistrationRequest(
                attestationBytes,
                clientDataBytes
            );

            // Validate registration
            RegistrationParameters registrationParameters = new RegistrationParameters(
                serverProperty,
                null,  // pubKeyCredParams - null means accept all
                parseUserVerificationRequirement().equals(UserVerificationRequirement.REQUIRED)
            );

            RegistrationData registrationData = webAuthnManager.parse(registrationRequest);
            webAuthnManager.validate(registrationData, registrationParameters);

            // Extract credential data
            AttestationObject attObj = registrationData.getAttestationObject();
            AttestedCredentialData credData = attObj.getAuthenticatorData().getAttestedCredentialData();

            if (credData == null) {
                return RegistrationResult.failure("No credential data in attestation");
            }

            String credentialId = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(credData.getCredentialId());
            String publicKey = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(objectConverter.getCborConverter().writeValueAsBytes(credData.getCOSEKey()));
            String aaguid = credData.getAaguid().toString();
            long signCount = attObj.getAuthenticatorData().getSignCount();
            boolean userVerification = attObj.getAuthenticatorData().isFlagUV();
            boolean backupEligible = attObj.getAuthenticatorData().isFlagBE();
            boolean backupState = attObj.getAuthenticatorData().isFlagBS();

            // Check for duplicate credential
            if (passkeyRepository.findByCredentialId(credentialId).isPresent()) {
                return RegistrationResult.failure("Credential already registered");
            }

            // Create and save passkey
            Passkey passkey = Passkey.create(
                record.userId(),
                credentialId,
                publicKey,
                aaguid,
                displayName != null ? displayName : "Passkey",
                signCount,
                userVerification,
                backupEligible,
                backupState,
                transports
            );
            passkeyRepository.save(passkey);

            // Update user passkey status
            User user = userRepository.findById(record.userId())
                .orElseThrow(() -> new IllegalStateException("User not found"));
            user.enrollPasskey(userVerification);
            userRepository.save(user);

            log.info("Passkey registered for user: {}, credentialId: {}",
                record.userId(), credentialId.substring(0, 20) + "...");

            return RegistrationResult.success(passkey.id().id());

        } catch (VerificationException e) {
            log.warn("Passkey registration validation failed: {}", e.getMessage());
            return RegistrationResult.failure("Validation failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("Passkey registration error", e);
            return RegistrationResult.failure("Registration failed: " + e.getMessage());
        }
    }

    /**
     * Generate authentication options for passkey login.
     */
    public AuthenticationOptionsResponse generateAuthenticationOptions(String loginId) {
        // Find user by login ID
        Optional<User> userOpt = userRepository.findByLoginId(loginId);

        // Generate challenge
        byte[] challengeBytes = new byte[32];
        SECURE_RANDOM.nextBytes(challengeBytes);
        Challenge challenge = new DefaultChallenge(challengeBytes);

        String challengeId = UUID.randomUUID().toString();
        UserId userId = userOpt.map(User::id).orElse(null);

        challengeStore.put(challengeId, new ChallengeRecord(
            challenge,
            userId,
            CeremonyType.AUTHENTICATION,
            System.currentTimeMillis() + properties.getTimeout()
        ));

        // Get allowed credentials for this user (if found)
        List<AllowedCredential> allowCredentials = new ArrayList<>();
        if (userOpt.isPresent()) {
            allowCredentials = passkeyRepository.findByUserId(userOpt.get().id())
                .stream()
                .map(p -> new AllowedCredential(
                    p.credentialId(),
                    Arrays.asList(p.transports())
                ))
                .collect(Collectors.toList());
        }

        return new AuthenticationOptionsResponse(
            challengeId,
            Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes),
            properties.getRpId(),
            properties.getTimeout(),
            properties.getUserVerification(),
            allowCredentials
        );
    }

    /**
     * Complete passkey authentication.
     */
    @Transactional
    public AuthenticationResult completeAuthentication(
            String challengeId,
            String credentialId,
            String clientDataJSON,
            String authenticatorData,
            String signature,
            String userHandle) {

        // Retrieve and validate challenge
        ChallengeRecord record = challengeStore.remove(challengeId);
        if (record == null) {
            return AuthenticationResult.failure("Invalid or expired challenge");
        }
        if (System.currentTimeMillis() > record.expiresAt()) {
            return AuthenticationResult.failure("Challenge expired");
        }
        if (record.ceremonyType() != CeremonyType.AUTHENTICATION) {
            return AuthenticationResult.failure("Invalid ceremony type");
        }

        try {
            // Find the passkey
            Optional<Passkey> passkeyOpt = passkeyRepository.findByCredentialId(credentialId);
            if (passkeyOpt.isEmpty()) {
                return AuthenticationResult.failure("Credential not found");
            }
            Passkey passkey = passkeyOpt.get();

            // Get the user
            User user = userRepository.findById(passkey.userId())
                .orElseThrow(() -> new IllegalStateException("User not found for passkey"));

            // Check user status
            if (user.status() == User.Status.LOCKED) {
                return AuthenticationResult.failure("Account is locked");
            }
            if (user.status() == User.Status.DEACTIVATED) {
                return AuthenticationResult.failure("Account is deactivated");
            }

            // Decode inputs
            byte[] credentialIdBytes = Base64.getUrlDecoder().decode(credentialId);
            byte[] clientDataBytes = Base64.getUrlDecoder().decode(clientDataJSON);
            byte[] authenticatorDataBytes = Base64.getUrlDecoder().decode(authenticatorData);
            byte[] signatureBytes = Base64.getUrlDecoder().decode(signature);

            // Build server property
            Set<Origin> origins = Arrays.stream(properties.getAllowedOrigins())
                .map(Origin::new)
                .collect(Collectors.toSet());

            ServerProperty serverProperty = new ServerProperty(
                origins,
                properties.getRpId(),
                record.challenge(),
                null
            );

            // Build authenticator for validation
            byte[] publicKeyBytes = Base64.getUrlDecoder().decode(passkey.publicKey());
            Authenticator authenticator = new AuthenticatorImpl(
                null,  // attestedCredentialData not needed for assertion
                null,  // attestationStatement
                passkey.signCount()
            );

            // Parse authentication request
            AuthenticationRequest authRequest = new AuthenticationRequest(
                credentialIdBytes,
                null,  // userHandle
                authenticatorDataBytes,
                clientDataBytes,
                null,  // clientExtensionsJSON
                signatureBytes
            );

            // Build parameters
            AuthenticationParameters authParams = new AuthenticationParameters(
                serverProperty,
                authenticator,
                List.of(credentialIdBytes),
                parseUserVerificationRequirement().equals(UserVerificationRequirement.REQUIRED)
            );

            // Parse and get authenticator data for sign count
            AuthenticationData authData = webAuthnManager.parse(authRequest);

            // Validate authentication
            webAuthnManager.validate(authData, authParams);

            // Update sign count
            long newSignCount = authData.getAuthenticatorData().getSignCount();
            if (newSignCount > 0) {
                passkey.updateSignCount(newSignCount);
                passkeyRepository.save(passkey);
            } else {
                passkey.recordUsage();
                passkeyRepository.save(passkey);
            }

            // Record login
            user.recordLogin();
            userRepository.save(user);

            log.info("Passkey authentication successful for user: {}", user.loginId());

            return AuthenticationResult.success(
                user.id().id(),
                user.loginId(),
                user.email(),
                user.profileId().urn(),
                passkey.userVerification()
            );

        } catch (VerificationException e) {
            log.warn("Passkey authentication validation failed: {}", e.getMessage());
            return AuthenticationResult.failure("Validation failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("Passkey authentication error", e);
            return AuthenticationResult.failure("Authentication failed: " + e.getMessage());
        }
    }

    /**
     * List passkeys for a user.
     */
    public List<PasskeySummary> listPasskeys(UserId userId) {
        return passkeyRepository.findByUserId(userId)
            .stream()
            .map(p -> new PasskeySummary(
                p.id().id(),
                p.displayName(),
                p.createdAt(),
                p.lastUsedAt(),
                p.userVerification(),
                p.backupState()
            ))
            .collect(Collectors.toList());
    }

    /**
     * Delete a passkey.
     */
    @Transactional
    public boolean deletePasskey(UserId userId, String passkeyId) {
        List<Passkey> userPasskeys = passkeyRepository.findByUserId(userId);

        Optional<Passkey> toDelete = userPasskeys.stream()
            .filter(p -> p.id().id().equals(passkeyId))
            .findFirst();

        if (toDelete.isEmpty()) {
            return false;
        }

        passkeyRepository.deleteById(toDelete.get().id());

        // Update user status if no more passkeys
        if (userPasskeys.size() == 1) {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                user.unenrollPasskey();
                userRepository.save(user);
            }
        }

        log.info("Passkey deleted for user: {}, passkeyId: {}", userId, passkeyId);
        return true;
    }

    /**
     * Update passkey display name.
     */
    @Transactional
    public boolean updatePasskeyName(UserId userId, String passkeyId, String displayName) {
        return passkeyRepository.findByUserId(userId).stream()
            .filter(p -> p.id().id().equals(passkeyId))
            .findFirst()
            .map(p -> {
                p.updateDisplayName(displayName);
                passkeyRepository.save(p);
                return true;
            })
            .orElse(false);
    }

    private String parseAuthenticatorAttachment() {
        // Return null to allow any authenticator type (platform or cross-platform)
        return null;
    }

    private UserVerificationRequirement parseUserVerificationRequirement() {
        return switch (properties.getUserVerification().toLowerCase()) {
            case "required" -> UserVerificationRequirement.REQUIRED;
            case "discouraged" -> UserVerificationRequirement.DISCOURAGED;
            default -> UserVerificationRequirement.PREFERRED;
        };
    }

    // Cleanup expired challenges (should be called periodically)
    public void cleanupExpiredChallenges() {
        long now = System.currentTimeMillis();
        challengeStore.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
    }
}
