package com.codeSense_Ai.Security;

import com.codeSense_Ai.Enties.Users;
import lombok.Getter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;
@Getter
public class CodeSenseOAuth2User implements OAuth2User {
    private final Users user;

    private final Map<String, Object> attributes;

    public CodeSenseOAuth2User(
            Users user,
            Map<String, Object> attributes
    ) {

        this.user = user;
        this.attributes = attributes;
    }

    @Override
    public Map<String, Object> getAttributes() {

        return attributes;
    }

    @Override
    public Collection<SimpleGrantedAuthority>
    getAuthorities() {

        return List.of(
                new SimpleGrantedAuthority(
                        user.getRole().name()
                )
        );
    }

    @Override
    public String getName() {

        return user.getUsername();
    }
}
