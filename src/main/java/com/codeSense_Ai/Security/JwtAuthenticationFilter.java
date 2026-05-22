package com.codeSense_Ai.Security;

import com.codeSense_Ai.Repository.UserRepository;
import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            // Extract token from header or cookie
            String token = jwtTokenProvider.extractToken(request);

            if (token != null && jwtTokenProvider.validateToken(token)) {
                // Token is valid — load user and set authentication
                Long userId = jwtTokenProvider.getUserIdFromToken(token);

                userRepository.findById(userId).ifPresent(user -> {
                    List<String> roles = jwtTokenProvider.getRolesFromToken(token);

                    List<SimpleGrantedAuthority> authorities = roles.stream()
                            .map(SimpleGrantedAuthority::new)
                            .toList();

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    user,        // principal
                                    null,        // credentials (not needed — token-based)
                                    authorities  // roles
                            );

                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );

                    // Set in SecurityContext — Spring Security reads this
                    SecurityContextHolder.getContext()
                            .setAuthentication(authentication);

                    log.debug("JWT authenticated user: {} roles={}",
                            user.getUsername(), roles);
                });
            }

        } catch (Exception e) {
            // Never let filter errors block the request
            // SecurityContext remains empty → Spring Security handles 401
            log.error("JWT filter error: {}", e.getMessage());
        }

        // Always continue the filter chain
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Skip JWT filter for public endpoints
        String path = request.getServletPath();
        return path.startsWith("/api/v1/webhooks/")
                || path.startsWith("/actuator/health")
                || path.startsWith("/oauth2/")
                || path.startsWith("/login")
                || path.equals("/error");
    }

}
