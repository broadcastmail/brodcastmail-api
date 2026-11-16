package com.broadcastmail.api.connection;

import com.broadcastmail.api.common.exceptions.ConnectionNotFoundException;
import com.broadcastmail.api.connection.dto.EmailSourceResult;
import com.broadcastmail.api.connection.dto.SupabaseIntrospectionData;
import com.broadcastmail.api.oauth.OAuthSessionStore;
import com.broadcastmail.api.onboarding.OnboardingSession;
import com.broadcastmail.api.onboarding.dto.ConfirmEmailTableRequest;
import com.broadcastmail.api.onboarding.dto.EmailSourceOption;
import com.broadcastmail.api.supabase.SupabaseSql;
import com.broadcastmail.common.connection.ConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class EmailSourceService {

    private final OAuthSessionStore onboardingSessionStore;
    private final ConnectionRepository connectionRepository;
    private final SupabaseIntrospectionService supabaseIntrospectionService;

    public void confirm(String sessionToken, ConfirmEmailTableRequest request) {
        OnboardingSession session = onboardingSessionStore.get(sessionToken).requireEmailTableSource();
        if (request != null) {
            OnboardingSession.EmailTableDetails resolved = new OnboardingSession.EmailTableDetails(
                    request.emailTableSchema(), request.emailTableName(),
                    request.primaryKeyColumn(), request.emailColumn());
            onboardingSessionStore.updateSession(sessionToken,
                    session.withEmailTableDetails(resolved).withEmailTableConfirmed(true));
        } else {
            onboardingSessionStore.updateSession(sessionToken, session.withEmailTableConfirmed(true));
        }
    }

    public String detectPrimaryKey(String jdbcUrl, String encryptedRolePassword, String schema, String table) {
        return supabaseIntrospectionService.detectPrimaryKey(jdbcUrl, encryptedRolePassword, schema, table);
    }

    public EmailSourceResult detect(SupabaseIntrospectionData data) {
        List<EmailSourceOption> options = buildOptions(data.emailColumnsByTable(), data.primaryKeys());
        return detect(options);
    }

    public EmailSourceResult detect(String sessionToken) {
        OnboardingSession session = onboardingSessionStore.get(sessionToken).requireEmailTableSource();
        SupabaseIntrospectionData data = session.getIntrospectionData();
        if (data == null) return new EmailSourceResult.NotDetected();

        EmailSourceResult result = detect(data);
        OnboardingSession updated = switch (result) {
            case EmailSourceResult.Detected(var option, var emailColumn) ->
                    session.withEmailTableDetails(new OnboardingSession.EmailTableDetails(
                            option.tableSchema(), option.tableName(),
                            option.primaryKeyColumn(), emailColumn))
                            .withEmailSourceCandidates(null);
            case EmailSourceResult.MultipleCandidates(List<EmailSourceOption> candidates) ->
                    session.withEmailTableDetails(null).withEmailSourceCandidates(candidates);
            case EmailSourceResult.NotDetected _ ->
                    session.withEmailTableDetails(null).withEmailSourceCandidates(null);
        };
        onboardingSessionStore.updateSession(sessionToken, updated);
        return result;
    }

    public EmailSourceResult detectForAccount(UUID accountId) {
        com.broadcastmail.common.connection.Connection connection =
                connectionRepository.findByAccountId(accountId).orElseThrow(ConnectionNotFoundException::new);
        SupabaseIntrospectionData data = supabaseIntrospectionService.introspect(
                SupabaseSql.buildJdbcUrl(connection.getProjectRef()), connection.getEncryptedCreds());

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
