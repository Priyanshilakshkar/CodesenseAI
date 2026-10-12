package com.codeSense_Ai.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        return http

                // Disable CSRF for webhook endpoint
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/github")
                )

                .authorizeHttpRequests(auth -> auth

                        // GitHub webhook can access this endpoint
                        .requestMatchers("/github").permitAll()

                        // Temporarily allow everything
                        .anyRequest().permitAll()
                )

                .build();
    }
}
