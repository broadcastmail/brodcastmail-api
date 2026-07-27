package com.broadcastemail.api.campaign;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "campaigns")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
public class Campaign {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    @Setter
    private UUID id;

    @NotNull
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @NotNull
    @Column(name = "connection_id", nullable = false)
    private UUID connectionId;

    @NotNull
    @Setter
    @Column(name = "name", nullable = false)
    private String name;

    @NotNull
    @Setter
    @Column(name = "subject", nullable = false)
    private String subject;

    @NotNull
    @Setter
    @Column(name = "body_html", nullable = false)
    private String bodyHtml;

    @NotNull
    @Setter
    @Column(name = "status", nullable = false)
    private CampaignStatus status;

    @Setter
    @Column(name = "recipient_count")
    private Integer recipientCount;

    @Builder.Default
    @Column(name = "sent_count", nullable = false)
    private Integer sentCount = 0;

    @Builder.Default
    @Column(name = "delivered_count", nullable = false)
    private Integer deliveredCount = 0;

    @Builder.Default
    @Column(name = "opened_count", nullable = false)
    private Integer openedCount = 0;

    @Builder.Default
    @Column(name = "bounced_count", nullable = false)
    private Integer bouncedCount = 0;

    @Builder.Default
    @Column(name = "failed_count", nullable = false)
    private Integer failedCount = 0;

    @Setter
    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}