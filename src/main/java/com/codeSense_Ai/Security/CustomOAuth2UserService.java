package com.codeSense_Ai.Security;

import com.codeSense_Ai.Enties.Users;
import com.codeSense_Ai.Enums.Roles;
import com.codeSense_Ai.Repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static java.lang.StableValue.map;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    // GitHub logins that are automatically given ADMIN role
    // Configure in application.yml: codesense.security.admin-logins
    private final List<String> adminLogins;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest)
            throws OAuth2AuthenticationException {

        // Fetch profile from GitHub API
        OAuth2User oAuth2User = super.loadUser(userRequest);
        Map<String, Object> attributes = oAuth2User.getAttributes();

        // Extract GitHub profile fields
        String githubLogin   = (String)  attributes.get("login");
        String email         = (String)  attributes.get("email");
        String displayName   = (String)  attributes.get("name");
        String avatarUrl     = (String)  attributes.get("avatar_url");
        Long githubId     = (Long) attributes.get("id");

        log.info("OAuth2 login: githubLogin={} email={}", githubLogin, email);

        // Determine role based on GitHub login
        Roles role = adminLogins.contains(githubLogin)
                ? Roles.ADMIN
                : Roles.REVIEWER;;

        // Create or update user in our DB
        Users user = userRepository.findByUsername(githubLogin)
                .map(existing -> updateUser(existing, email, displayName, avatarUrl))
                .orElseGet(() -> createUser(githubLogin, email, displayName,
                        avatarUrl, githubId, role));

        // Audit the login
        auditLogService.log(user.getId(), "LOGIN", "User",
                user.getId(), "GitHub OAuth login");

        // Return a Spring Security OAuth2User backed by our User entity
        return new CodeSenseOAuth2User(user, attributes);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Users createUser(String login, String email, String name,
                            String avatar, Long githubId, Roles roles) {
        Users user = new Users();
        user.setUsername(login);
        user.setEmail(email);
        user.setDisplayName(name != null ? name : login);
        user.setAvatarUrl(avatar);
        user.setGithubId(githubId);
        user.setRole(roles);
        user.setCreatedAt(Instant.now());
        user.setLastLoginAt(Instant.now());
        user.setIsActive(true);

        Users saved = userRepository.save(user);
        log.info("Created new user: {} roles={}", login, roles);
        return saved;
    }

    private Users updateUser(Users existing, String email,
                            String name, String avatar) {
        existing.setEmail(email);
        if (name != null) existing.setDisplayName(name);
        if (avatar != null) existing.setAvatarUrl(avatar);
        existing.setLastLoginAt(Instant.now());
        return userRepository.save(existing);
    }
}
