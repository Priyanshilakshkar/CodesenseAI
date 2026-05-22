package com.codeSense_Ai.Utility;

import com.codeSense_Ai.Dto.GithubAppProperties;
import com.codeSense_Ai.Exceptions.GithubAuthException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.security.PrivateKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

public class GithubJwtGenerator {
    private final GithubAppProperties githubAppProperties;

    private final RsaKeyLoader rsaKeyLoader;

    public GithubJwtGenerator(GithubAppProperties githubAppProperties, RsaKeyLoader rsaKeyLoader) {
        this.githubAppProperties = githubAppProperties;
        this.rsaKeyLoader = rsaKeyLoader;
    }

    public String generateJwt(){
        try {
            PrivateKey key = rsaKeyLoader.loadPrivateKey(githubAppProperties.getPrivateKeyPath());

            Instant instant = Instant.now();

            return Jwts.builder()
                    .setIssuer(githubAppProperties.getAppId())

                    .setIssuedAt(Date.from(instant.minus(60 , ChronoUnit.SECONDS)))

                    .setExpiration(Date.from(instant.plus(9 , ChronoUnit.MINUTES)))

                    .signWith(key , SignatureAlgorithm.RS256)

                    .compact();
        } catch (Exception e) {
            throw new GithubAuthException("Failed to generate GitHub App JWT", e);
        }
    }
}