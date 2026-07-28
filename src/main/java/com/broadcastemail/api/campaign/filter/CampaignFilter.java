package com.broadcastemail.api.campaign.filter;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name="campaign_filters")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignFilter {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Getter
    @NotNull
    @Column(name = "column_name", nullable = false)
    private String columnName;

    @NotNull
    @Getter
    @Column(name = "operator", nullable = false)
    @Enumerated(EnumType.STRING)
    private FilterOperator operator;

    @NotNull
    @Getter
    @Column(name = "filter_value", nullable = false)
    private String filterValue;

    @Getter
    @NotNull
    @Column(name = "filter_order", nullable = false)
    private Integer filterOrder;

    @NotNull
    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private OffsetDateTime createdAt;
}
