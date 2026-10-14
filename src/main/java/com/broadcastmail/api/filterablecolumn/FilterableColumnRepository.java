package com.broadcastmail.api.filterablecolumn;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FilterableColumnRepository extends JpaRepository<FilterableColumn, UUID> {
    List<FilterableColumn> findByConnectionId(UUID connectionId);
    Optional<FilterableColumn> findByConnectionIdAndColumnName(UUID connectionId, String columnName);
    void deleteByConnectionId(UUID connectionId);
}