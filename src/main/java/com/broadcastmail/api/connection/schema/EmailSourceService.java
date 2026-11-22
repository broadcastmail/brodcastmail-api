package com.broadcastmail.api.connection.schema;

import com.broadcastmail.api.connection.introspection.SupabaseIntrospectionService;
import com.broadcastmail.api.common.exceptions.ConnectionNotFoundException;
import com.broadcastmail.api.connection.dto.EmailSourceResult;
import com.broadcastmail.api.connection.dto.SupabaseIntrospectionData;
import com.broadcastmail.api.connection.dto.EmailSourceOption;
import com.broadcastmail.api.supabase.management.ManagementSqlRunner;
import com.broadcastmail.api.supabase.management.ManagementSqlRunners;
import com.broadcastmail.api.supabase.SupabaseSql;
import com.broadcastmail.common.connection.ConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/** Finds candidate email tables/columns in a project. Onboarding's session steps live in OnboardingEmailSourceService. */
@Service
@RequiredArgsConstructor
public class EmailSourceService {

    private final ConnectionRepository connectionRepository;
    private final SupabaseIntrospectionService supabaseIntrospectionService;
    private final ManagementSqlRunners managementSqlRunners;

    public String detectPrimaryKey(ManagementSqlRunner sql, String schema, String table) {
        return supabaseIntrospectionService.detectPrimaryKey(sql, new SupabaseSql.TableRef(schema, table));
    }

    public EmailSourceResult detect(SupabaseIntrospectionData data) {
        List<EmailSourceOption> options = buildOptions(data.emailColumnsByTable(), data.primaryKeys());
        return detect(options);
    }

    public EmailSourceResult detectForAccount(UUID accountId) {
        com.broadcastmail.common.connection.Connection connection =
                connectionRepository.findByAccountId(accountId).orElseThrow(ConnectionNotFoundException::new);
        SupabaseIntrospectionData data = supabaseIntrospectionService.introspect(
                managementSqlRunners.forAccount(accountId, connection.getProjectRef()));

        List<EmailSourceOption> options = buildOptions(data.emailColumnsByTable(), data.primaryKeys());

        if (connection.getEmailTableName() != null) {
            return options.stream()
                    .filter(o -> o.tableName().equals(connection.getEmailTableName())
                              && o.tableSchema().equals(connection.getEmailTableSchema()))
                    .findFirst()
                    .<EmailSourceResult>map(match -> new EmailSourceResult.Detected(match, connection.getEmailColumn()))
                    .orElseGet(() -> detect(options));
        }

        return detect(options);
    }

    private EmailSourceResult detect(List<EmailSourceOption> options) {
        if (options.isEmpty()) return new EmailSourceResult.NotDetected();
        if (options.size() == 1) {
            EmailSourceOption option = options.getFirst();
            String autoColumn = option.emailColumnCandidates().stream()
                    .filter(c -> c.equals("email") || c.equals("email_address"))
                    .findFirst()
                    .orElse(null);
            if (autoColumn != null) return new EmailSourceResult.Detected(option, autoColumn);
        }
        return new EmailSourceResult.MultipleCandidates(options);
    }

    private List<EmailSourceOption> buildOptions(Map<String, List<String>> textColumnsByTable, Map<String, String> primaryKeys) {
        List<EmailSourceOption> options = new ArrayList<>();
        textColumnsByTable.forEach((key, candidates) -> {
            if (candidates.isEmpty()) return;
            int dot = key.indexOf('.');
            options.add(new EmailSourceOption(
                    key.substring(0, dot), key.substring(dot + 1),
                    primaryKeys.get(key), candidates));
        });
        return options;
    }

}
