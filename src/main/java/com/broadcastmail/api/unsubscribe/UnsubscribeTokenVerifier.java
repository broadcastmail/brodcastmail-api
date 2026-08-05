package com.broadcastmail.api.unsubscribe;

import com.broadcastmail.api.common.exceptions.InvalidUnsubscribeTokenException;
import com.broadcastmail.api.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UnsubscribeTokenVerifier {


    private final AppProperties appProperties;

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
