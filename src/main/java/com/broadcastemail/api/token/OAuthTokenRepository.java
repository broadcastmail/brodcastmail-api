package com.broadcastemail.api.token;

import com.broadcastemail.api.oauth.OAuthToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OAuthTokenRepository extends JpaRepository<OAuthToken, UUID> {
    Optional<OAuthToken> findByAccountId(UUID accountId);
    void deleteByAccountId(UUID accountId);
}