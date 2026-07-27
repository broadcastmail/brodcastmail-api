package com.broadcastemail.api.outbox;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jdk.jfr.Timestamp;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OutboxEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Getter
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "campaign_recipient_id", nullable = false)
    private UUID campaignRecipientId;

    @NotNull
    @Column(name = "status", nullable = false, length = Integer.MAX_VALUE)
    @Convert(converter = OutboxStatus.PersistenceConverter.class)
    private OutboxStatus status;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "attempts", nullable = false)
    private Integer attempts;

    @NotNull
    @Timestamp
    @Column(name = "next_attempt_at", nullable = false)
    private OffsetDateTime nextAttemptAt;

    @Column(name = "last_attempted_at")
    @Timestamp
    private OffsetDateTime lastAttemptedAt;

    @CreationTimestamp
    private OffsetDateTime createdAt;

}
