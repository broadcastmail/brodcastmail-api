package com.broadcastmail.api.supabase.reader;

import com.broadcastmail.api.supabase.management.SupabaseManagementClient;
import com.broadcastmail.api.config.EncryptionProperties;
import com.broadcastmail.api.supabase.SupabaseSql;
import com.broadcastmail.api.supabase.SupabaseSql.TableRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReaderRoleServiceTest {

    @Mock
    private SupabaseManagementClient supabaseManagementClient;
    @Mock
    private EncryptionProperties encryptionProperties;
    @InjectMocks
    private ReaderRoleService readerRoleService;

    private static final TableRef CUSTOMERS = new TableRef("crm", "customers");

    @Test
    void syncGrantsShouldGrantTablesAndRevokeEverythingElseInOneCall() {
        // When
        readerRoleService.syncGrants("token", "ref", "broadcastmail_r_abc", Set.of(CUSTOMERS));

        // Then — SQL content is covered by SupabaseSqlReaderTest; here only that both go out together
        verify(supabaseManagementClient).executeSql("token", "ref",
                SupabaseSql.grantReaderSql("broadcastmail_r_abc", Set.of(CUSTOMERS))
                        + SupabaseSql.revokeOtherReaderGrantsSql("broadcastmail_r_abc", Set.of(CUSTOMERS)));
        verify(supabaseManagementClient).executeSql("token", "ref", SupabaseSql.bypassRlsSql("broadcastmail_r_abc"));
    }

    @Test
    void syncGrantsShouldStillSucceedWhenBypassRlsIsRefused() {
        // Given
        lenient().doThrow(new IllegalStateException("permission denied"))
                .when(supabaseManagementClient).executeSql("token", "ref", SupabaseSql.bypassRlsSql("broadcastmail_r_abc"));

        // When / Then
        assertThatCode(() -> readerRoleService.syncGrants("token", "ref", "broadcastmail_r_abc", Set.of(CUSTOMERS)))
                .doesNotThrowAnyException();
    }

    @Test
    void syncGrantsWithNoTablesShouldOnlyRevoke() {
        // When
        readerRoleService.syncGrants("token", "ref", "broadcastmail_r_abc", Set.of());

        // Then
        verify(supabaseManagementClient).executeSql("token", "ref",
                SupabaseSql.grantReaderSql("broadcastmail_r_abc", Set.of())
                        + SupabaseSql.revokeOtherReaderGrantsSql("broadcastmail_r_abc", Set.of()));
    }

    @Test
    void provisionShouldAlsoBypassRlsForTheNewRole() {
        // Given
        when(encryptionProperties.key()).thenReturn("0123456789abcdef0123456789abcdef");

        // When
        var provisioned = readerRoleService.provision("token", "ref", Set.of(CUSTOMERS));

        // Then
        verify(supabaseManagementClient).executeSql("token", "ref", SupabaseSql.bypassRlsSql(provisioned.role()));
    }

    @Test
    void dropQuietlyShouldNeverTouchLegacySharedRole() {
        // When
        readerRoleService.dropQuietly("token", "ref", SupabaseSql.READER_ROLE);

        // Then
        verifyNoInteractions(supabaseManagementClient);
    }

    @Test
    void dropQuietlyShouldSwallowFailures() {
        // Given
        doThrow(new IllegalStateException("boom")).when(supabaseManagementClient).executeSql(anyString(), anyString(), anyString());

        // When / Then
        assertThatCode(() -> readerRoleService.dropQuietly("token", "ref", "broadcastmail_r_abc"))
                .doesNotThrowAnyException();
        verify(supabaseManagementClient).executeSql(anyString(), anyString(), anyString());
    }

    @Test
    void dropUnusedShouldDropOnlyPerIntegrationRolesNotInUse() {
        // Given
        when(supabaseManagementClient.sqlRunner("token", "ref")).thenReturn(sql -> List.of(
                Map.of("rolname", "broadcastmail_r_old1"),
                Map.of("rolname", "broadcastmail_r_live"),
                Map.of("rolname", "broadcastmail_r_saved"),
                Map.of("rolname", "broadcastmail_r_old2")));

        // When
        readerRoleService.dropUnused("token", "ref", Set.of("broadcastmail_r_live", "broadcastmail_r_saved"));

        // Then
        verify(supabaseManagementClient).executeSql("token", "ref", SupabaseSql.dropReaderRoleSql("broadcastmail_r_old1"));
        verify(supabaseManagementClient).executeSql("token", "ref", SupabaseSql.dropReaderRoleSql("broadcastmail_r_old2"));
        verify(supabaseManagementClient, times(2)).executeSql(anyString(), anyString(), anyString());
    }

    @Test
    void dropUnusedShouldSwallowFailures() {
        // Given
        when(supabaseManagementClient.sqlRunner("token", "ref")).thenReturn(sql -> {
            throw new IllegalStateException("management api down");
        });

        // When / Then
        assertThatCode(() -> readerRoleService.dropUnused("token", "ref", Set.of()))
                .doesNotThrowAnyException();
        verify(supabaseManagementClient, never()).executeSql(anyString(), anyString(), anyString());
    }
}
