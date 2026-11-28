package com.broadcastmail.api.onboarding;

import com.broadcastmail.api.common.exceptions.InvalidOnboardingSessionException;
import com.broadcastmail.api.connection.ConnectionSpec;
import com.broadcastmail.api.connection.EmailSourceSpec;
import com.broadcastmail.api.connection.dto.DetectedColumn;
import com.broadcastmail.api.connection.dto.SchemaIntrospectionResult;
import com.broadcastmail.api.connection.dto.SupabaseIntrospectionData;
import com.broadcastmail.api.connection.dto.EmailSourceOption;
import com.broadcastmail.api.filterablecolumn.ColumnSelection;
import com.broadcastmail.common.connection.ReaderConnectionFactory;
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
    private String projectName;
    private String projectUrl;
    private String poolerPrefix;
    // Set together once the reader role exists on projectRef (provisioned at /schema/test, or reused on same-project
    // reconfigure). Read through hasReader() / readerConnectionFactory() rather than null-checking.
    private String dbRole;
    private String encryptedRolePassword;
    private boolean connectionVerified;
    private String ownerEmail;
    private String encryptedAccessToken;
    private String encryptedRefreshToken;
    private ResendDetails resendDetails;
    private Instant tokenExpiresAt;
    private Instant expiresAt;
    private SchemaDetails schemaDetails;
    private EmailTableDetails emailTableDetails;
    private boolean emailTableConfirmed;
    private SupabaseIntrospectionData introspectionData;
    private List<EmailSourceOption> emailSourceCandidates;
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

    public OnboardingSession requireEmailTableSource() {
        requireSchemaConfirmed();
        return this;
    }

    public OnboardingSession requireEmailTableConfirmed() {
        if (!emailTableConfirmed) {
            throw new InvalidOnboardingSessionException();
        }
        return this;
    }

    public OnboardingSession requireConnectionVerified() {
        if (!connectionVerified || !hasReader()) {
            throw new InvalidOnboardingSessionException();
        }
        return this;
    }

    public boolean hasReader() {
        return dbRole != null;
    }

    /** @throws InvalidOnboardingSessionException before the reader role has been provisioned */
    public ReaderConnectionFactory readerConnectionFactory() {
        if (!hasReader()) throw new InvalidOnboardingSessionException();
        return new ReaderConnectionFactory(projectRef, poolerPrefix, dbRole);
    }

    public ConnectionSpec connectionSpec() {
        return new ConnectionSpec(projectRef, projectName, projectUrl, poolerPrefix, dbRole, encryptedRolePassword,
                schemaDetails.userSchema(), schemaDetails.userTable(), schemaDetails.userIdColumn());
    }

    public EmailSourceSpec emailSourceSpec() {
        if (emailTableDetails == null) return new EmailSourceSpec(null, null, null, null);
        return new EmailSourceSpec(emailTableDetails.emailTableSchema(), emailTableDetails.emailTableName(),
                emailTableDetails.emailTableIdColumn(), emailTableDetails.emailColumn());
    }

    public ColumnSelection columnSelection() {
        return new ColumnSelection(detectedColumns, authColumns, confirmedColumnNames);
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

    public record EmailTableDetails(
            String emailTableSchema,
            String emailTableName,
            String emailTableIdColumn,
            String emailColumn
    ) {}

    public record ResendDetails(
            String encryptedResendApiKey,
            String fromAddress
    )
    {}
}