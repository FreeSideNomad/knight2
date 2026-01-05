package com.knight.application.service.email;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for email service.
 */
@Component
@ConfigurationProperties(prefix = "email")
public class EmailProperties {

    /**
     * Email provider to use: ahasend, mock, smtp
     */
    private String provider = "mock";

    /**
     * Sender email address
     */
    private String fromAddress = "noreply@knight.example.com";

    /**
     * Sender display name
     */
    private String fromName = "Knight Platform";

    /**
     * AhaSend configuration
     */
    private AhaSend ahasend = new AhaSend();

    /**
     * SMTP configuration
     */
    private Smtp smtp = new Smtp();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public AhaSend getAhasend() {
        return ahasend;
    }

    public void setAhasend(AhaSend ahasend) {
        this.ahasend = ahasend;
    }

    public Smtp getSmtp() {
        return smtp;
    }

    public void setSmtp(Smtp smtp) {
        this.smtp = smtp;
    }

    /**
     * AhaSend specific configuration
     */
    public static class AhaSend {
        private String accountId;
        private String apiKey;
        private String apiUrl = "https://api.ahasend.com/v2";

        public String getAccountId() {
            return accountId;
        }

        public void setAccountId(String accountId) {
            this.accountId = accountId;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getApiUrl() {
            return apiUrl;
        }

        public void setApiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
        }

        public boolean isConfigured() {
            return accountId != null && !accountId.isBlank()
                && apiKey != null && !apiKey.isBlank();
        }
    }

    /**
     * SMTP specific configuration
     */
    public static class Smtp {
        private String host;
        private int port = 587;
        private String username;
        private String password;
        private boolean starttls = true;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public boolean isStarttls() {
            return starttls;
        }

        public void setStarttls(boolean starttls) {
            this.starttls = starttls;
        }

        public boolean isConfigured() {
            return host != null && !host.isBlank();
        }
    }
}
