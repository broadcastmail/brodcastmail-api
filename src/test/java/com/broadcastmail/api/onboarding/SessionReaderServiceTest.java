package com.broadcastmail.api.onboarding;

import com.broadcastmail.api.common.exceptions.ReaderNotReadyException;
import com.broadcastmail.api.oauth.OAuthSessionStore;
import com.broadcastmail.api.supabase.reader.ReaderConnectionVerifier;
import com.broadcastmail.api.supabase.reader.ReaderRoleService;
import com.broadcastmail.api.supabase.management.SupabaseAccessTokens;
import com.broadcastmail.api.supabase.SupabaseSql.TableRef;
import com.broadcastmail.common.connection.ReaderConnectionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionReaderServiceTest {

    @Mock
    private OAuthSessionStore onboardingSessionStore;
    @Mock
    private ReaderRoleService readerRoleService;
    @Mock
    private ReaderConnectionVerifier readerConnectionVerifier;
    @Mock
    private SupabaseAccessTokens supabaseAccessTokens;
    @InjectMocks
    private SessionReaderService sessionReaderService;

    private OnboardingSession baseSession() {
        return OnboardingSession.builder()
                .projectRef("ref")
                .poolerPrefix("aws-0-eu-west-1")
                .schemaDetails(new OnboardingSession.SchemaDetails("customers", "crm", "id", false))
                .build();
    }

    @Test
    void shouldProvisionPersistThenVerify() {
        // Given
        when(onboardingSessionStore.get("token")).thenReturn(baseSession());
        when(supabaseAccessTokens.decrypt(any())).thenReturn("access");
        when(readerRoleService.provision(eq("access"), eq("ref"), any()))
                .thenReturn(new ReaderRoleService.Provisioned("broadcastmail_r_abc", "enc-pw"));
        when(readerRoleService.decryptPassword("enc-pw")).thenReturn("pw");

        // When
        OnboardingSession result = sessionReaderService.ensureReady("token");

        // Then
        verify(readerRoleService).provision("access", "ref", Set.of(new TableRef("crm", "customers")));
        ArgumentCaptor<OnboardingSession> saved = ArgumentCaptor.forClass(OnboardingSession.class);
        var inOrder = inOrder(onboardingSessionStore, readerConnectionVerifier);
        inOrder.verify(onboardingSessionStore).updateSession(eq("token"), saved.capture());
        inOrder.verify(readerConnectionVerifier).verify(
                new ReaderConnectionFactory("ref", "aws-0-eu-west-1", "broadcastmail_r_abc"), "pw");
        assertThat(saved.getAllValues().getFirst().getDbRole()).isEqualTo("broadcastmail_r_abc");
        assertThat(saved.getAllValues().getFirst().isConnectionVerified()).isFalse();
        assertThat(result.isConnectionVerified()).isTrue();
    }

    @Test
    void shouldKeepRoleWhenVerificationFailsSoRetryReusesIt() {
        // Given
        when(onboardingSessionStore.get("token")).thenReturn(baseSession());
        when(readerRoleService.provision(any(), any(), any()))
                .thenReturn(new ReaderRoleService.Provisioned("broadcastmail_r_abc", "enc-pw"));
        doThrow(new ReaderNotReadyException("nope", null))
                .when(readerConnectionVerifier).verify(any(), any());

        // When / Then
        assertThatThrownBy(() -> sessionReaderService.ensureReady("token")).isInstanceOf(ReaderNotReadyException.class);
        ArgumentCaptor<OnboardingSession> saved = ArgumentCaptor.forClass(OnboardingSession.class);
        verify(onboardingSessionStore).updateSession(eq("token"), saved.capture());
        assertThat(saved.getValue().getDbRole()).isEqualTo("broadcastmail_r_abc");
    }

    @Test
    void shouldNotProvisionOrVerifyAgainWhenAlreadyReady() {
        // Given
        OnboardingSession ready = baseSession()
                .withDbRole("broadcastmail_r_abc")
                .withEncryptedRolePassword("enc-pw")
                .withConnectionVerified(true);
        when(onboardingSessionStore.get("token")).thenReturn(ready);
        when(supabaseAccessTokens.decrypt(any())).thenReturn("access");

        // When
        sessionReaderService.ensureReady("token");

        // Then
        verify(readerRoleService, never()).provision(any(), any(), any());
        verifyNoInteractions(readerConnectionVerifier);
        verify(readerRoleService).syncGrants("access", "ref", "broadcastmail_r_abc",
                Set.of(new TableRef("crm", "customers")));
    }

    @Test
    void selectedTablesShouldIncludeEmailTableAndSkipMissingProfileTable() {
        // Given
        OnboardingSession session = baseSession()
                .withSchemaDetails(new OnboardingSession.SchemaDetails(null, null, null, true))
                .withEmailTableDetails(new OnboardingSession.EmailTableDetails("public", "contacts", "id", "email"));

        // Then
        assertThat(SessionReaderService.selectedTables(session)).containsExactly(new TableRef("public", "contacts"));
        assertThat(List.copyOf(SessionReaderService.selectedTables(baseSession()))).hasSize(1);
    }
}
