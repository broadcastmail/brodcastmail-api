package com.broadcastmail.api.supabase.reader;

import com.broadcastmail.api.supabase.SupabaseSql;
import com.broadcastmail.api.supabase.management.SupabaseManagementClient;
import com.broadcastmail.api.common.SecurityUtil;
import com.broadcastmail.api.config.EncryptionProperties;
import com.broadcastmail.api.supabase.SupabaseSql.TableRef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Owns the tenant reader role lifecycle: created once per integration (never re-passworded, so Supavisor's secret
 * cache can't go stale), SELECT granted only on the tables in use, dropped when the integration moves project.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReaderRoleService {

    private final SupabaseManagementClient supabaseManagementClient;
    private final EncryptionProperties encryptionProperties;

    public record Provisioned(String role, String encryptedPassword) {}

    public Provisioned provision(String accessToken, String projectRef, Collection<TableRef> tables) {
        String role = SupabaseSql.newReaderRole();
        String password = SecurityUtil.generatePassword();
        supabaseManagementClient.executeSql(accessToken, projectRef,
                SupabaseSql.provisionReaderSql(role, password, tables));
        log.info("[reader] provisioned role={} project={} tables={}", role, projectRef, tables.size());
        bypassRlsQuietly(accessToken, projectRef, role);
        return new Provisioned(role, SecurityUtil.encrypt(password, encryptionProperties.key()));
    }

    /**
     * Makes the role able to read exactly {@code tables} (plus the auth view): GRANTs them and REVOKEs anything else
     * it was granted. Idempotent, one Management API call, takes effect immediately — no pooler verification needed.
     */
    public void syncGrants(String accessToken, String projectRef, String role, Collection<TableRef> tables) {
        supabaseManagementClient.executeSql(accessToken, projectRef,
                SupabaseSql.grantReaderSql(role, tables) + SupabaseSql.revokeOtherReaderGrantsSql(role, tables));
        bypassRlsQuietly(accessToken, projectRef, role);
    }

    // Separate call so a project that refuses BYPASSRLS still gets its grants.
    private void bypassRlsQuietly(String accessToken, String projectRef, String role) {
        try {
            supabaseManagementClient.executeSql(accessToken, projectRef, SupabaseSql.bypassRlsSql(role));
        } catch (RuntimeException e) {
            log.warn("[reader] could not set BYPASSRLS role={} project={}: {}", role, projectRef, e.getMessage());
        }
    }

    /** Best effort — a leftover role on a project we no longer use is harmless, so failures are only logged. */
    public void dropQuietly(String accessToken, String projectRef, String role) {
        if (!SupabaseSql.isPerIntegrationRole(role)) return;
        try {
            supabaseManagementClient.executeSql(accessToken, projectRef, SupabaseSql.dropReaderRoleSql(role));
        } catch (RuntimeException e) {
            log.warn("[reader] could not drop role={} project={}: {}", role, projectRef, e.getMessage());
        }
    }

    /**
     * Moves a connection from {@code oldRole} on {@code oldProjectRef} to {@code newRole} on {@code newProjectRef}.
     * A replaced role is dropped; when the project is unchanged the kept role's grants are synced to {@code tables}
     * (a new project's role was already granted when it was provisioned).
     */
    public void handOver(String accessToken, String oldProjectRef, String oldRole,
                         String newProjectRef, String newRole, Collection<TableRef> tables) {
        if (!newRole.equals(oldRole)) {
            dropQuietly(accessToken, oldProjectRef, oldRole);
        }
        if (newProjectRef.equals(oldProjectRef)) {
            syncGrants(accessToken, newProjectRef, newRole, tables);
        }
    }

    /**
     * Drops this project's per-integration roles that aren't in {@code inUse} — e.g. roles provisioned by onboarding
     * sessions that were abandoned or replaced by a new OAuth sign-in. Best effort: failures are only logged.
     */
    public void dropUnused(String accessToken, String projectRef, Set<String> inUse) {
        try {
            List<String> unused = supabaseManagementClient.sqlRunner(accessToken, projectRef)
                    .query(SupabaseSql.LIST_READER_ROLES_SQL).stream()
                    .map(row -> String.valueOf(row.get("rolname")))
                    .filter(role -> SupabaseSql.isPerIntegrationRole(role) && !inUse.contains(role))
                    .toList();
            unused.forEach(role -> dropQuietly(accessToken, projectRef, role));
            if (!unused.isEmpty()) {
                log.info("[reader] dropped {} unused role(s) on project={}", unused.size(), projectRef);
            }
        } catch (RuntimeException e) {
            log.warn("[reader] could not clean up unused roles on project={}: {}", projectRef, e.getMessage());
        }
    }

    public String decryptPassword(String encryptedPassword) {
        return SecurityUtil.decrypt(encryptedPassword, encryptionProperties.key());
    }

    /** The tables a connection reads: the profile table and the email table, either of which may be unset. */
    public static Set<TableRef> tables(String userSchema, String userTable, String emailSchema, String emailTable) {
        Set<TableRef> tables = new LinkedHashSet<>();
        if (userSchema != null && userTable != null) tables.add(new TableRef(userSchema, userTable));
        if (emailSchema != null && emailTable != null) tables.add(new TableRef(emailSchema, emailTable));
        return tables;
    }
}
