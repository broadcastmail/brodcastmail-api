package com.broadcastmail.api.supabase.management;

import com.broadcastmail.api.supabase.reader.TenantConnectionGateway;

import java.util.List;
import java.util.Map;

/**
 * Runs privileged SQL (as the project's postgres role) against a tenant project through the Supabase Management API
 * ({@link SupabaseManagementClient#sqlRunner}) and returns rows as column-label → value maps. Used for introspection
 * and provisioning, so it never depends on the reader role or the pooler. Reading tenant data as the reader role goes
 * through {@link TenantConnectionGateway} instead.
 */
@FunctionalInterface
public interface ManagementSqlRunner {
    List<Map<String, Object>> query(String sql);
}
