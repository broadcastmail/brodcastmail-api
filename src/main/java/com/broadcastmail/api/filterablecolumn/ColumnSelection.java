package com.broadcastmail.api.filterablecolumn;

import com.broadcastmail.api.connection.dto.DetectedColumn;

import java.util.List;

public record ColumnSelection(List<DetectedColumn> profileColumns, List<DetectedColumn> authColumns,
                              List<String> confirmedNames) {}
