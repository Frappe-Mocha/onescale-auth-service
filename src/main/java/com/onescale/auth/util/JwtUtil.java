package com.onescale.auth.util;

import com.onescale.auth.config.JwtProperties;
import com.onescale.auth.entity.User;
import com.onescale.auth.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class JwtUtil {

    private final JwtProperties jwtProperties;

    public JwtUtil(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Access-token claims:
     *   sub          – client_id (UUID String) - the only user identifier exposed to clients
     *   email        – user's email (nullable)
     *   mobileNumber – user's mobile (nullable)
     *   fullName     – user's display name
     *   tokenType    – "access"
     */
    public String generateAccessToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", user.getEmail());
        claims.put("mobileNumber", user.getMobileNumber());
        claims.put("fullName", user.getFullName());
        claims.put("tokenType", "access");

        return Jwts.builder()
                .claims(claims)
                .subject(user.getClientId())  // client_id is the subject
                .issuer(jwtProperties.getIssuer())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusMillis(jwtProperties.getAccessTokenExpiration()))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Refresh-token claims are intentionally minimal — only
     * userId and tokenType.  The refresh token is looked up by
     * its raw value in the refresh_tokens table; all other user
     * data is fetched from that row.
     */
    public String generateRefreshToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tokenType", "refresh");

        return Jwts.builder()
                .claims(claims)
                .subject(user.getClientId())  // client_id is the subject
                .issuer(jwtProperties.getIssuer())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusMillis(jwtProperties.getRefreshTokenExpiration())))
                .signWith(getSigningKey())
                .compact();
    }

    // -----------------------------------------------------------
    // VALIDATION
    // -----------------------------------------------------------

    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (SignatureException ex) {
            log.error("Invalid JWT signature: {}", ex.getMessage());
            throw new InvalidTokenException("Invalid token signature");
        } catch (MalformedJwtException ex) {
            log.error("Invalid JWT token: {}", ex.getMessage());
            throw new InvalidTokenException("Malformed token");
        } catch (ExpiredJwtException ex) {
            log.error("Expired JWT token: {}", ex.getMessage());
            throw new InvalidTokenException("Token has expired");
        } catch (UnsupportedJwtException ex) {
            log.error("Unsupported JWT token: {}", ex.getMessage());
            throw new InvalidTokenException("Unsupported token");
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty: {}", ex.getMessage());
            throw new InvalidTokenException("Token claims are empty");
        }
    }

    // -----------------------------------------------------------
    // CLAIM EXTRACTORS
    // -----------------------------------------------------------

    /**
     * Extract client_id from token subject.
     * This is the primary user identifier for all external APIs.
     */
    public String getClientIdFromToken(String token) {
        Claims claims = validateToken(token);
        return claims.getSubject();  // subject is now client_id
    }

    public String getEmailFromToken(String token) {
        Claims claims = validateToken(token);
        return (String) claims.get("email");
    }

    public String getMobileNumberFromToken(String token) {
        Claims claims = validateToken(token);
        return (String) claims.get("mobileNumber");
    }

    public String getFullNameFromToken(String token) {
        Claims claims = validateToken(token);
        return (String) claims.get("fullName");
    }

    public String getTokenType(String token) {
        Claims claims = validateToken(token);
        return (String) claims.get("tokenType");
    }

    public boolean isAccessToken(String token) {
        return "access".equals(getTokenType(token));
    }

    public boolean isRefreshToken(String token) {
        return "refresh".equals(getTokenType(token));
    }

    // -----------------------------------------------------------
    // EXPIRATION HELPERS
    // -----------------------------------------------------------

    public Long getAccessTokenExpirationInSeconds() {
        return jwtProperties.getAccessTokenExpiration() / 1000;
    }

    public Date getRefreshTokenExpirationDate() {
        return Date.from(Instant.now().plusMillis(jwtProperties.getRefreshTokenExpiration()));
    }
}
