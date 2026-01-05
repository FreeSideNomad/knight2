package com.knight.application.service.passkey;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "passkey")
public class PasskeyProperties {

    /**
     * Relying Party ID - typically the domain name.
     * Must match the domain where the passkey is used.
     */
    private String rpId = "localhost";

    /**
     * Relying Party name - displayed to users during registration.
     */
    private String rpName = "Knight Platform";

    /**
     * Origins allowed for WebAuthn ceremonies.
     * Should include the full origin (e.g., https://login.example.com)
     */
    private String[] allowedOrigins = {"http://localhost:8001"};

    /**
     * Timeout for registration/authentication ceremonies in milliseconds.
     */
    private long timeout = 60000;

    /**
     * Whether to require user verification (biometric/PIN).
     * Options: required, preferred, discouraged
     */
    private String userVerification = "preferred";

    /**
     * Whether to require resident keys (discoverable credentials).
     * Options: required, preferred, discouraged
     */
    private String residentKey = "preferred";

    /**
     * Attestation conveyance preference.
     * Options: none, indirect, direct, enterprise
     */
    private String attestation = "none";

    /**
     * Maximum number of passkeys per user.
     */
    private int maxPasskeysPerUser = 10;
}
