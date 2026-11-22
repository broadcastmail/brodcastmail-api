package com.broadcastmail.api.connection;

import com.broadcastmail.api.connection.introspection.SupabaseIntrospectionService;
import com.broadcastmail.api.common.exceptions.ConnectionNotFoundException;
import com.broadcastmail.api.connection.dto.FilterableColumnResponse;
import com.broadcastmail.api.connection.dto.ReconnectCompatibilityResponse;
import com.broadcastmail.api.connection.dto.SupabaseIntrospectionData;
import com.broadcastmail.api.oauth.dto.ProjectOption;
import com.broadcastmail.api.supabase.management.SupabaseAccessTokens;
import com.broadcastmail.api.supabase.management.SupabaseManagementClient;
import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.filterablecolumn.FilterableColumn;
import com.broadcastmail.common.filterablecolumn.FilterableColumnRepository;
import com.broadcastmail.common.connection.ConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConnectionQueryService {

    private final SupabaseManagementClient supabaseManagementClient;
    private final SupabaseAccessTokens supabaseAccessTokens;
    private final ConnectionRepository connectionRepository;
    private final FilterableColumnRepository filterableColumnRepository;
    private final SupabaseIntrospectionService supabaseIntrospectionService;

    public List<FilterableColumnResponse> listColumns(UUID accountId) {
        var connection = connectionRepository.findByAccountId(accountId)
                .orElseThrow(ConnectionNotFoundException::new);
        return filterableColumnRepository.findByConnectionId(connection.getId()).stream()
                .map(FilterableColumnResponse::from)
                .toList();
    }

    public ReconnectCompatibilityResponse checkCompatibility(UUID accountId, String projectRef) {
        Connection connection = connectionRepository.findByAccountId(accountId)
                .orElseThrow(ConnectionNotFoundException::new);

        SupabaseIntrospectionData data = supabaseIntrospectionService.introspect(
                supabaseManagementClient.sqlRunner(supabaseAccessTokens.forAccount(accountId), projectRef));

        String userKey = connection.getUserTableSchema() + "." + connection.getUserTableName();
        List<SupabaseIntrospectionData.Column> userCols = data.columnsByTable().get(userKey);
        boolean userTableOk = userCols != null;

        String emailKey = connection.getEmailTableSchema() != null
                ? connection.getEmailTableSchema() + "." + connection.getEmailTableName()
                : userKey;
        List<SupabaseIntrospectionData.Column> emailCols = data.columnsByTable().get(emailKey);
        boolean emailColumnOk = emailCols != null && emailCols.stream()
                .anyMatch(c -> c.name().equals(connection.getEmailColumn()));

        Set<String> availableNames = emailCols != null
                ? emailCols.stream().map(SupabaseIntrospectionData.Column::name).collect(Collectors.toSet())
                : Set.of();
        List<FilterableColumn> filterCols = filterableColumnRepository.findByConnectionId(connection.getId());
        List<String> missing = filterCols.stream()
                .map(FilterableColumn::getColumnName)
                .filter(name -> !availableNames.contains(name))
                .toList();

        return new ReconnectCompatibilityResponse(userTableOk, emailColumnOk, missing);
    }

    public List<ProjectOption> listProjects(UUID accountId) {
        String rawAccessToken = supabaseAccessTokens.forAccount(accountId);

        return supabaseManagementClient.listProjects(rawAccessToken).stream()
                .map(p -> new ProjectOption(p.ref(), p.name(), p.status()))
                .toList();
    }
}
