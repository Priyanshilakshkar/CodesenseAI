package com.codeSense_Ai.Security;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "codesense.security")
@Validated
@Getter
@Setter
public class JwtProperties {

    @NotBlank(message = "JWT secret must be set via JWT_SECRET environment variable")
    private String jwtSecret;

    /**
     * How long JWTs stay valid — default 24 hours.
     * After expiry, user must re-authenticate via GitHub OAuth.
     */
    @Min(value = 1, message = "JWT expiry must be at least 1 hour")
    private int jwtExpiryHours = 24;

    /**
     * AES-256 key for encrypting secrets in the database.
     * Loaded from AES_ENCRYPTION_KEY environment variable.
     * Must be exactly 32 characters (256 bits).
     */
    @NotBlank(message = "AES encryption key must be set via AES_ENCRYPTION_KEY")
    private String encryptionKey;

    public long jwtExpiryMs() {
        return (long) jwtExpiryHours * 60 * 60 * 1000;
    }
}
