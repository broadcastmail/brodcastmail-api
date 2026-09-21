package com.broadcastmail.api.onboarding;

import com.broadcastmail.api.common.exceptions.InvalidOnboardingSessionException;
import com.broadcastmail.api.connection.dto.DetectedColumn;
import com.broadcastmail.api.connection.dto.SchemaIntrospectionResult;
import lombok.Builder;
import lombok.Getter;
import lombok.With;

import java.time.Instant;
import java.util.List;
import java.util.UUID;


@Builder
@Getter
@With
public class OnboardingSession {
    private UUID accountId;
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
    private List<DetectedColumn> authColumns;
    private List<SchemaIntrospectionResult.Detected> schemaCandidates;


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

    public boolean isReconfigure() {
        return accountId != null;
    }

    public record SchemaDetails(
            String userTable,
            String userSchema,
            String userIdColumn,
            boolean confirmed
    ) {}

    public record ResendDetails(
            String encryptedResendApiKey,
            String fromAddress
    )
    {}
}