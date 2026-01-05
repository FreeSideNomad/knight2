package com.knight.application.service.otp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for OTP verification.
 */
@Component
@ConfigurationProperties(prefix = "otp")
public class OtpProperties {

    /**
     * OTP code expiration time in seconds.
     * Default: 120 seconds (2 minutes)
     */
    private int expirationSeconds = 120;

    /**
     * Maximum number of verification attempts before OTP is invalidated.
     * Default: 3 attempts
     */
    private int maxAttempts = 3;

    /**
     * Rate limiting window in seconds.
     * Default: 60 seconds (1 minute)
     */
    private int rateLimitWindowSeconds = 60;

    /**
     * Maximum OTP requests allowed per rate limit window.
     * Default: 3 requests per minute
     */
    private int rateLimitMaxRequests = 3;

    /**
     * Cooldown period in seconds before a new OTP can be sent to same email.
     * Default: 30 seconds
     */
    private int resendCooldownSeconds = 30;

    public int getExpirationSeconds() {
        return expirationSeconds;
    }

    public void setExpirationSeconds(int expirationSeconds) {
        this.expirationSeconds = expirationSeconds;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getRateLimitWindowSeconds() {
        return rateLimitWindowSeconds;
    }

    public void setRateLimitWindowSeconds(int rateLimitWindowSeconds) {
        this.rateLimitWindowSeconds = rateLimitWindowSeconds;
    }

    public int getRateLimitMaxRequests() {
        return rateLimitMaxRequests;
    }

    public void setRateLimitMaxRequests(int rateLimitMaxRequests) {
        this.rateLimitMaxRequests = rateLimitMaxRequests;
    }

    public int getResendCooldownSeconds() {
        return resendCooldownSeconds;
    }

    public void setResendCooldownSeconds(int resendCooldownSeconds) {
        this.resendCooldownSeconds = resendCooldownSeconds;
    }
}
