package com.codeSense_Ai.Utility;

import com.codeSense_Ai.Dto.GithubAppProperties;
import com.codeSense_Ai.Exceptions.GithubAuthException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;
import lombok.extern.slf4j.XSlf4j;
import org.springframework.stereotype.Component;

import java.security.PrivateKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Component
@Slf4j
public class GithubJwtGenerator {
    private final GithubAppProperties githubAppProperties;
    private final RsaKeyLoader rsaKeyLoader;

    public GithubJwtGenerator(
            GithubAppProperties githubAppProperties,
            RsaKeyLoader rsaKeyLoader
    ) {
        this.githubAppProperties = githubAppProperties;
        this.rsaKeyLoader = rsaKeyLoader;
    }

    public String generateJwt() {

        try {

            // 1. Load GitHub App private key
            PrivateKey key = rsaKeyLoader.loadPrivateKey(
                    githubAppProperties.getPrivateKeyPath()
            );

            log.info("GitHub private key loaded successfully");

            // 2. Current time
            Instant now = Instant.now();

            // 3. Generate GitHub App JWT
            String jwt = Jwts.builder()
                    .setIssuer(githubAppProperties.getAppId())
                    .setIssuedAt(
                            Date.from(now.minus(60, ChronoUnit.SECONDS))
                    )
                    .setExpiration(
                            Date.from(now.plus(9, ChronoUnit.MINUTES))
                    )
                    .signWith(key, SignatureAlgorithm.RS256)
                    .compact();

            log.info("GitHub App JWT generated successfully");

            return jwt;

        } catch (Exception e) {

            // VERY IMPORTANT:
            // Print the real reason instead of hiding it.
            log.error(
                    "Failed to generate GitHub App JWT",
                    e
            );

            throw new GithubAuthException(
                    "Failed to generate GitHub App JWT: " + e.getMessage()
            );
        }
    }
}