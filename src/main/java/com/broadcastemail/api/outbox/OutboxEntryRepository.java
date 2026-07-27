package com.broadcastemail.api.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxEntryRepository extends JpaRepository<OutboxEntry, UUID> {

    List<OutboxEntry> findByStatusAndLastAttemptedAtBefore(String status, OffsetDateTime cutoff);
}