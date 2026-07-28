package com.broadcastemail.api.campaign.recipent;


import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "campaign_recipients")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CampaignRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    @Getter
    private UUID id;

    @NotNull
    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @NotNull
    @Column(name = "external_user_id", nullable = false)
    private String externalUserId;

    @NotNull
    @Column(name = "email", nullable = false)
    private String email;

    @NotNull
    @Column(name = "status", nullable = false)
    @Convert(converter = RecipientStatus.PersistenceConverter.class)
    private RecipientStatus status;

    @NotNull
    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "resend_message_id")
    private String resendMessageId;

    @Column(name = "failed_reason")
    private String failedReason;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "opened_at")
    private OffsetDateTime openedAt;

    @Column(name = "bounced_at")
    private OffsetDateTime bouncedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

}
