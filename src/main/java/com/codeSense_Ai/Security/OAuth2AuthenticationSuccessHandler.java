package com.codeSense_Ai.Security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j

public class OAuth2AuthenticationSuccessHandler  extends SimpleUrlAuthenticationSuccessHandler {
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException {

        CodeSenseOAuth2User oAuth2User = (CodeSenseOAuth2User) authentication.getPrincipal();

        // Generate JWT
        String token = jwtTokenProvider.generateToken(oAuth2User.getUser());

        // Store in HttpOnly, Secure, SameSite=Strict cookie
        // HttpOnly = not accessible from JavaScript (XSS protection)
        // Secure   = only sent over HTTPS
        Cookie cookie = new Cookie("codesense_token", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);   // set false in dev if not using HTTPS locally
        cookie.setPath("/");
        cookie.setMaxAge(24 * 60 * 60); // 24 hours — matches JWT expiry
        cookie.setAttribute("SameSite", "Strict");
        response.addCookie(cookie);

        log.info("OAuth2 success: user={} token generated",
                oAuth2User.getUser().getUsername());

        // Redirect to dashboard
        response.sendRedirect("/dashboard");
    }
}
