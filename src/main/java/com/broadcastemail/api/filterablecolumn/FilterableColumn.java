package com.broadcastemail.api.filterablecolumn;


import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "filterable_columns")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FilterableColumn {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "connection_id", nullable = false)
    private UUID connectionId;

    @NotNull
    @Column(name = "column_name", nullable = false)
    private String columnName;

    @NotNull
    @Column(name = "column_type", nullable = false)
    private String columnType;

    @NotNull
    @Column(name = "display_name", nullable = false)
    private String displayName;

    @NotNull
    @ColumnDefault("true")
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "cardinality")
    private Integer cardinality;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "cardinality_warning", nullable = false)
    private Boolean cardinalityWarning = false;

    @NotNull
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;


}
