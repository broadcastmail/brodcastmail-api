package com.broadcastemail.api.onboarding;

import com.broadcastemail.api.common.exceptions.InvalidOnboardingSessionException;
import com.broadcastemail.api.connection.dto.DetectedColumn;
import lombok.Builder;
import lombok.Getter;
import lombok.With;

import java.time.Instant;
import java.util.List;


@Builder
@Getter
@With
public class OnboardingSession {
    private String projectRef;
    private String projectUrl;
    private String jdbcUrl;
    private String encryptedRolePassword;
    private String ownerEmail;
    private String encryptedAccessToken;
    private String encryptedRefreshToken;
    private ResendDetails resendDetails;
    private Instant tokenExpiresAt;
    private Instant expiresAt;
    private SchemaDetails schemaDetails;
    private List<String> confirmedColumnNames;
    private List<DetectedColumn> detectedColumns;

    public OnboardingSession requireSchemaConfirmed() {
        if (schemaDetails == null || !schemaDetails.confirmed()) {
            throw new InvalidOnboardingSessionException();
        }
        return this;
    }

    public OnboardingSession requireResendDetails() {
        if (resendDetails == null) {
            throw new InvalidOnboardingSessionException();
        }
        return this;
    }

    public OnboardingSession requireSchemaDetected() {
        if (schemaDetails == null) {
            throw new InvalidOnboardingSessionException();
        }
        return this;
    }

    public record SchemaDetails(
            String userTable,
            String userSchema,
            boolean confirmed
    ) {}

    public record ResendDetails(
            String encryptedResendApiKey,
            String fromAddress
    )
    {}
}