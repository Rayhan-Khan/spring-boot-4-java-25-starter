package com.rayhan.base.security;

import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/**
 * The logged-in user, the same for every login method. Controllers receive it with
 * {@code @AuthenticationPrincipal AuthenticatedUser currentUser}.
 *
 * @param userId   internal user ID (users.id)
 * @param email    user's email
 * @param role     role name, e.g. USER or ADMIN
 * @param provider how the request was authenticated
 */
public record AuthenticatedUser(Integer userId, String email, String role, AuthProvider provider)
        implements AuthenticatedPrincipal {

    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
