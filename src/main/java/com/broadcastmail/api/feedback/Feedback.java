package com.broadcastmail.api.feedback;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "feedback")
@Builder
@Getter
@RequiredArgsConstructor
@AllArgsConstructor
public class Feedback {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "plan")
    private String plan;

    @NotNull
    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "page")
    private String page;

    @NotNull
    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "email")
    private String email;

    // No @NotNull: bean validation runs before Hibernate fills @CreationTimestamp, so it would reject every insert.
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
