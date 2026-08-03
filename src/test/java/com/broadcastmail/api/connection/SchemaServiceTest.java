package com.broadcastmail.api.connection;

import com.broadcastmail.api.common.exceptions.InvalidOnboardingSessionException;
import com.broadcastmail.api.connection.dto.DetectedColumn;
import com.broadcastmail.api.connection.dto.SchemaIntrospectionResult;
import com.broadcastmail.api.onboarding.OnboardingSession;
import com.broadcastmail.api.onboarding.OnboardingSessionStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchemaServiceTest {

    @Mock
    private OnboardingSessionStore onboardingSessionStore;

    @Mock
    private SchemaIntrospectionService schemaIntrospectionService;

    @Captor
    private ArgumentCaptor<OnboardingSession> sessionCaptor;

    @InjectMocks
    private SchemaService schemaService;

    private OnboardingSession baseSession() {
        return OnboardingSession.builder()
                .ownerEmail("test@example.com")
                .encryptedAccessToken("encrypted-access")
                .encryptedRefreshToken("encrypted-refresh")
                .projectRef("project-ref")
                .projectUrl("https://project-ref.supabase.co")
                .jdbcUrl("jdbc:postgresql://db.project-ref.supabase.co:5432/postgres")
                .encryptedRolePassword("encrypted-role-password")
                .tokenExpiresAt(Instant.now().plusSeconds(3600))
                .expiresAt(Instant.now().plusSeconds(1800))
                .build();
    }

    private SchemaIntrospectionResult detectedResult() {
        return new SchemaIntrospectionResult(
                "profiles",
                "public",
                "email",
                "id",
                List.of(
                        new DetectedColumn("plan", "text", true, 3, false),
                        new DetectedColumn("full_name", "text", true, 0, false),
                        new DetectedColumn("created_at", "timestamptz", true, 0, false)
                )
        );
    }

    @Test
    void shouldStoreDetectedTableAfterIntrospection() {
        // Given
        when(onboardingSessionStore.get("token")).thenReturn(baseSession());
        when(schemaIntrospectionService.introspect(anyString(), anyString())).thenReturn(detectedResult());

        // When
        schemaService.detect("token");

        // Then
        verify(onboardingSessionStore).updateSession(eq("token"), sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getSchemaDetails().userTable()).isEqualTo("profiles");
        assertThat(sessionCaptor.getValue().getSchemaDetails().userSchema()).isEqualTo("public");
        assertThat(sessionCaptor.getValue().getSchemaDetails().confirmed()).isFalse();
    }

    @Test
    void shouldConfirmSchemaWithColumnNames() {
        // Given
        OnboardingSession sessionWithSchema = baseSession()
                .withSchemaDetails(new OnboardingSession.SchemaDetails("profiles", "public", false));
        when(onboardingSessionStore.get("token")).thenReturn(sessionWithSchema);

        // When
        schemaService.confirm("token", List.of("plan", "full_name"));

        // Then
        verify(onboardingSessionStore).updateSession(eq("token"), sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getSchemaDetails().confirmed()).isTrue();
        assertThat(sessionCaptor.getValue().getConfirmedColumnNames()).containsExactly("plan", "full_name");
    }

    @Test
    void shouldThrowWhenConfirmingWithoutDetection() {
        // Given
        OnboardingSession session = baseSession(); // no schemaDetails
        when(onboardingSessionStore.get("token")).thenReturn(session);

        // When
        List<String> columns = List.of("plan");

        // Then
        assertThatThrownBy(() -> schemaService.confirm("token", columns))
                .isInstanceOf(InvalidOnboardingSessionException.class);
    }
}