package com.broadcastemail.api.emailprovider;


import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmailProviderRepository extends JpaRepository<EmailProvider, UUID> {
    Optional<EmailProvider> findByAccountId(UUID accountId);
}