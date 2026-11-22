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
}
