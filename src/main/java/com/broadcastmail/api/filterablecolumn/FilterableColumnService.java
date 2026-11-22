package com.broadcastmail.api.filterablecolumn;

import com.broadcastmail.common.campaign.filter.FilterSource;
import com.broadcastmail.common.filterablecolumn.FilterableColumn;
import com.broadcastmail.common.filterablecolumn.FilterableColumnRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FilterableColumnService {

    private final FilterableColumnRepository filterableColumnRepository;

    /** Saves the selected columns for a connection that has none yet. */
    @Transactional
    public void create(UUID connectionId, ColumnSelection selection) {
        filterableColumnRepository.saveAll(build(connectionId, selection));
    }

    /** Drops the connection's columns and saves the new selection in their place. */
    @Transactional
    public void replace(UUID connectionId, ColumnSelection selection) {
        filterableColumnRepository.deleteByConnectionId(connectionId);
        filterableColumnRepository.saveAll(build(connectionId, selection));
    }

    private static List<FilterableColumn> build(UUID connectionId, ColumnSelection selection) {
        List<FilterableColumn> columns = new ArrayList<>();
        columns.addAll(FilterableColumnFactory.from(
                connectionId, selection.profileColumns(), FilterSource.PROFILE_TABLE, selection.confirmedNames()));
        columns.addAll(FilterableColumnFactory.from(
                connectionId, selection.authColumns(), FilterSource.AUTH_METADATA, selection.confirmedNames()));
        return columns;
    }
}
