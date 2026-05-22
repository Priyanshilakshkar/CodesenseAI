package com.codeSense_Ai.Security;

import com.codeSense_Ai.Enties.Users;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider {
    private final JwtProperties jwtProperties;

    /**
     * Generates a signed JWT for an authenticated user.
     * Claims include: userId, githubLogin, roles.
     */
    public String generateToken(Users user) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.jwtExpiryMs());

        return Jwts.builder()
                .setSubject(String.valueOf(user.getId()))
                .claim("login",  user.getUsername())
                .claim("email",  user.getEmail())
                .claim("roles",  user.getRole())       // e.g. ["ROLE_ADMIN"]
                .claim("name",   user.getDisplayName())
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Extracts the user ID from a validated JWT.
     * Called by JwtAuthenticationFilter after validation.
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseClaims(token);
        return Long.valueOf(claims.getSubject());
    }

    /**
     * Extracts roles list from a validated JWT.
     */
    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        Claims claims = parseClaims(token);
        return (List<String>) claims.get("roles");
    }

    /**
     * Validates a JWT — checks signature and expiry.
     * Returns false (not throws) on any validation failure.
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);

            return true;

        } catch (ExpiredJwtException e) {
            log.debug("JWT expired: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("Malformed JWT: {}", e.getMessage());
        } catch (SecurityException e) {
            log.warn("Invalid JWT signature: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("JWT validation error: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Extracts token from Authorization header or cookie.
     * Supports: "Bearer {token}" header and "codesense_token" cookie.
     */
    public String extractToken(jakarta.servlet.http.HttpServletRequest request) {
        // Try Authorization header first (API clients)
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // Try cookie (dashboard browser sessions)
        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                if ("codesense_token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        return null;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtProperties.getJwtSecret()
                .getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
