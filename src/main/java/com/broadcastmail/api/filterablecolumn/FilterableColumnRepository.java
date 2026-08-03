package com.broadcastmail.api.filterablecolumn;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FilterableColumnRepository extends JpaRepository<FilterableColumn, UUID> {
    List<FilterableColumn> findByConnectionId(UUID connectionId);
    void deleteByConnectionId(UUID connectionId);
}