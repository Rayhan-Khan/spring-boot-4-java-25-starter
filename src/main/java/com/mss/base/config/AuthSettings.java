package com.mss.base.config;

import com.mss.base.security.JwtGenerator;
import com.google.firebase.auth.FirebaseAuth;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Which login methods are switched on, decided once at startup from .env:
 * <ul>
 *     <li>{@code JWT_ENABLED}: email/password login with the application's own tokens</li>
 *     <li>{@code FIREBASE_ENABLED}: Firebase login (enabled when a {@link FirebaseAuth} bean exists)</li>
 * </ul>
 * The values never change while the application runs, so checking them costs a field read.
 */
@Slf4j
@Component
public class AuthSettings {

    private final boolean jwtEnabled;
    private final boolean firebaseEnabled;

    public AuthSettings(@Value("${app.jwt.enabled}") boolean jwtEnabled,
                        ObjectProvider<FirebaseAuth> firebaseAuth,
                        JwtGenerator jwtGenerator) {
        this.jwtEnabled = jwtEnabled;
        this.firebaseEnabled = firebaseAuth.getIfAvailable() != null;

        if (jwtEnabled) {
            try {
                jwtGenerator.requireValidSecret();
            } catch (IllegalStateException e) {
                throw new IllegalStateException(e.getMessage()
                        + " Or set JWT_ENABLED=false to switch off email/password login.", e);
            }
        }

        log.info("Login methods: email/password (JWT) {}, Firebase {}",
                jwtEnabled ? "enabled" : "disabled", firebaseEnabled ? "enabled" : "disabled");
        if (!jwtEnabled && !firebaseEnabled) {
            log.warn("No login method is enabled (JWT_ENABLED=false, FIREBASE_ENABLED=false): "
                    + "protected endpoints will reject every request with 401.");
        }
    }

    public boolean jwtEnabled() {
        return jwtEnabled;
    }

    public boolean firebaseEnabled() {
        return firebaseEnabled;
    }
}
