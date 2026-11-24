package com.broadcastmail.api.suppressions;

import com.broadcastmail.api.common.exceptions.InvalidUnsubscribeTokenException;
import com.broadcastmail.api.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UnsubscribeTokenVerifier {

    private final AppProperties appProperties;

    private static final int MIN_SECRET_BYTES = 32;

    /** Fails startup instead of every unsubscribe: HMAC-SHA256 needs a 32-byte key, and an unset env var stays as "${...}". */
    @PostConstruct
    void requireUsableSecret() {
        String secret = appProperties.unsubscribeSecret();
        if (secret == null || secret.contains("${") || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("UNSUBSCRIBE_SECRET must be set to at least " + MIN_SECRET_BYTES + " bytes");
        }
    }

    public UUID verify(String token){
        try{
            Claims claims = Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return UUID.fromString(claims.getSubject());
        }
        catch(JwtException _) {
            throw new InvalidUnsubscribeTokenException();
        }
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(appProperties.unsubscribeSecret().getBytes(StandardCharsets.UTF_8));
    }
}
