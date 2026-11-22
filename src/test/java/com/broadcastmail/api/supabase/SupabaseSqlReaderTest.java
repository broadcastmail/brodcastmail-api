package com.broadcastmail.api.supabase;

import com.broadcastmail.api.supabase.SupabaseSql.TableRef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupabaseSqlReaderTest {

    @Test
    void newReaderRoleShouldBeUniqueValidIdentifier() {
        // When
        String a = SupabaseSql.newReaderRole();
        String b = SupabaseSql.newReaderRole();

        // Then
        assertThat(a).matches("broadcastmail_r_[0-9a-f]{10}").isNotEqualTo(b);
        assertThat(SupabaseSql.isPerIntegrationRole(a)).isTrue();
        assertThat(SupabaseSql.isPerIntegrationRole(SupabaseSql.READER_ROLE)).isFalse();
    }

    @Test
    void provisionShouldCreateRoleWithoutAlterAndGrantOnlySelectedTables() {
        // When
        String sql = SupabaseSql.provisionReaderSql("broadcastmail_r_abc", "Pw_abcdefghijklmnop",
                List.of(new TableRef("crm", "customers")));

        // Then
        assertThat(sql)
                .contains("CREATE ROLE broadcastmail_r_abc NOINHERIT LOGIN PASSWORD 'Pw_abcdefghijklmnop'")
                .contains("GRANT USAGE ON SCHEMA \"crm\" TO broadcastmail_r_abc")
                .contains("GRANT SELECT ON \"crm\".\"customers\" TO broadcastmail_r_abc")
                .contains("GRANT SELECT ON _broadcastmail.user_emails TO broadcastmail_r_abc")
                .doesNotContain("ALTER ROLE")
                .doesNotContain("ALL TABLES");
    }

    @Test
    void provisionShouldRejectPasswordThatCouldBreakOutOfLiteral() {
        assertThatThrownBy(() -> SupabaseSql.provisionReaderSql("broadcastmail_r_abc", "x'; DROP ROLE postgres; --", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tableRefShouldRejectInvalidIdentifiers() {
        assertThatThrownBy(() -> new TableRef("public", "users\"; --")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dropShouldRefuseSharedLegacyRole() {
        assertThatThrownBy(() -> SupabaseSql.dropReaderRoleSql(SupabaseSql.READER_ROLE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void introspectionShouldIncludeCustomSchemasButExcludeSystemOnes() {
        assertThat(SupabaseSql.INTROSPECT_SCHEMA)
                .contains("table_schema = 'auth' AND table_name = 'users'")
                .contains("'storage'").contains("'_broadcastmail'")
                .doesNotContain("IN ('public', 'auth')");
        assertThat(SupabaseSql.FIND_USER_LINKED_TABLES).contains("kcu.table_schema NOT IN (");
    }

    @Test
    void revokeOthersShouldKeepGivenTablesAndOurViewAndEscapeFormatPlaceholders() {
        // When
        String sql = SupabaseSql.revokeOtherReaderGrantsSql("broadcastmail_r_abc",
                List.of(new TableRef("public", "profiles"), new TableRef("crm", "contacts")));

        // Then
        assertThat(sql)
                .contains("grantee = 'broadcastmail_r_abc'")
                .contains("NOT IN (('public', 'profiles'), ('crm', 'contacts'))")
                .contains("table_schema <> '_broadcastmail'")
                .contains("format('REVOKE SELECT ON %I.%I FROM broadcastmail_r_abc'");
    }

    @Test
    void dropShouldGrantMembershipBeforeDroppingOwnedObjects() {
        // When
        String sql = SupabaseSql.dropReaderRoleSql("broadcastmail_r_abc");

        // Then — postgres isn't a member of the reader role, and DROP OWNED BY requires membership
        assertThat(sql.indexOf("GRANT broadcastmail_r_abc TO CURRENT_USER"))
                .isNotNegative()
                .isLessThan(sql.indexOf("DROP OWNED BY broadcastmail_r_abc"));
    }
}
