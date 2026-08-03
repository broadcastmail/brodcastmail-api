package com.broadcastmail.api.connection;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "connections")
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class Connection {
    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @NotNull
    @Column(name = "name", nullable = false)
    private String name;

    @NotNull
    @ColumnDefault("'supabase'")
    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "project_ref")
    @Getter
    private String projectRef;

    @NotNull
    @Column(name = "project_url", nullable = false)
    private String projectUrl;

    @NotNull
    @Column(name = "encrypted_creds", nullable = false)
    @Getter
    private String encryptedCreds;

    @NotNull
    @ColumnDefault("'public'")
    @Getter
    @Column(name = "user_table_schema", nullable = false)
    private String userTableSchema;

    @NotNull
    @Getter
    @Column(name = "user_table_name", nullable = false)
    private String userTableName;

    @NotNull
    @Column(name = "email_column", nullable = false)
    @Getter
    private String emailColumn;

    @NotNull
    @Column(name = "user_id_column", nullable = false)
    @Getter
    private String userIdColumn;

    @Column(name = "estimated_user_count")
    private Integer estimatedUserCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

}
