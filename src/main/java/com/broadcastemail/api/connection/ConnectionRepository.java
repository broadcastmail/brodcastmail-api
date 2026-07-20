package com.broadcastemail.api.connection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConnectionRepository extends JpaRepository<Connection, UUID> {
    Optional<Connection> findByAccountId(UUID accountId);
    boolean existsByAccountIdAndProjectRef(UUID accountId, String projectRef);
}