package com.broadcastmail.api.campaign.filter;

import com.broadcastmail.api.campaign.filter.dto.FilterRequest;
import com.broadcastmail.api.common.exceptions.ConnectionNotFoundException;
import com.broadcastmail.api.common.exceptions.InvalidCampaignFilterException;
import com.broadcastmail.api.filterablecolumn.FilterableColumnRepository;
import com.broadcastmail.common.campaign.filter.CampaignFilter;
import com.broadcastmail.common.campaign.filter.CampaignFilterSerializer;
import com.broadcastmail.common.campaign.filter.FilterSource;
import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.connection.ConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;


@Component
@RequiredArgsConstructor
public class CampaignFilterValidator {

    private static final String IDENTIFIER_PATTERN = "[a-zA-Z_]\\w*";

    private final ConnectionRepository connectionRepository;
    private final FilterableColumnRepository filterableColumnRepository;

    public void validateRequests(UUID accountId, List<FilterRequest> requests) {
        if (requests == null || requests.isEmpty()) return;
        validate(accountId, requests.stream()
                .map(r -> new Candidate(r.columnName(), r.sourceOrDefault(), r.jsonKey()))
                .toList());
    }

    public void validateSaved(UUID accountId, List<CampaignFilter> filters) {
        if (filters == null || filters.isEmpty()) return;
        validate(accountId, filters.stream()
                .map(f -> new Candidate(f.getColumnName(), f.getSource(), f.getJsonKey()))
                .toList());
    }

    private void validate(UUID accountId, List<Candidate> candidates) {
        Connection connection = connectionRepository.findByAccountId(accountId)
                .orElseThrow(ConnectionNotFoundException::new);
        Set<String> enabled = filterableColumnRepository.findByConnectionId(connection.getId()).stream()
                .filter(c -> Boolean.TRUE.equals(c.getEnabled()))
                .map(c -> key(c.getSource(), c.getColumnName()))
                .collect(Collectors.toSet());

        List<String> problems = new ArrayList<>();
        for (Candidate c : candidates) {
            if (c.source() == FilterSource.AUTH_METADATA_JSON) {
                if (!CampaignFilterSerializer.JSON_METADATA_COLUMNS.contains(c.columnName())) {
                    problems.add("'" + c.columnName() + "' is not a valid metadata column");
                } else if (c.jsonKey() == null || !c.jsonKey().matches(IDENTIFIER_PATTERN)) {
                    problems.add("'" + c.jsonKey() + "' is not a valid key for " + c.columnName());
                }
            } else if (!enabled.contains(key(c.source(), c.columnName()))) {
                problems.add("'" + c.columnName() + "' is not an enabled " + c.source() + " column");
            }
        }
        if (!problems.isEmpty()) {
            throw new InvalidCampaignFilterException(problems);
        }
    }

    private static String key(FilterSource source, String columnName) {
        return source + ":" + columnName;
    }

    private record Candidate(String columnName, FilterSource source, String jsonKey) {}
}
