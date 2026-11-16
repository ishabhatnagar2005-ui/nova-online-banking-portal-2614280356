package com.nova.bank.auth;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
/** Sign session identifiers and verify the signature and token expiry. */
@Service public class JwtService {
    private final SecretKey key;
    public JwtService(@Value("${nova.jwt-secret}") String secret) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) throw new IllegalArgumentException("JWT_SECRET must contain at least 32 bytes");
        key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
    /** Create a token bound to one persisted session. */
    public String issue(UUID user, UUID session, Instant now, Instant expires) {
        return Jwts.builder().subject(user.toString()).id(session.toString()).issuedAt(Date.from(now)).expiration(Date.from(expires)).signWith(key).compact();
    }
    /** Extract verified identifiers; malformed or expired tokens fail closed. */
    public TokenIds verify(String token) {
        var claims=Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return new TokenIds(UUID.fromString(claims.getSubject()), UUID.fromString(claims.getId()));
    }
    public record TokenIds(UUID userId, UUID sessionId) {}
}
