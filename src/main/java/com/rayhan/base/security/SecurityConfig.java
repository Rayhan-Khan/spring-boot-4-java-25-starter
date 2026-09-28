package com.rayhan.base.security;

import com.rayhan.base.config.AuthSettings;
import com.rayhan.base.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Security configuration class for the application.
 * Configures authentication, authorization, CORS and security filters.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(
        securedEnabled = true,
        jsr250Enabled = true
)
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * Endpoints that don't require authentication: auth endpoints, Swagger, health checks,
     * and {@code /error} (otherwise errors on public endpoints would be reported as 401).
     */
    private static final String[] PUBLIC_MATCHER = {
            "/api/v1/auth/**",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/actuator/health/**",
            "/actuator/info",
            "/error"
    };

    /**
     * Empty when the application runs with {@code FIREBASE_ENABLED=false}.
     */
    private final ObjectProvider<FirebaseAuth> firebaseAuth;
    private final JwtGenerator jwtGenerator;
    private final AuthSettings authSettings;
    private final UserRepository userRepository;
    private final CustomAuthEntryPoint authEntryPoint;

    /**
     * Comma-separated origins (patterns such as {@code https://*.example.com} are allowed).
     */
    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    /**
     * Configures the security filter chain for the application.
     *
     * @param http the HttpSecurity to configure
     * @return the configured SecurityFilterChain
     * @throws Exception if an error occurs during configuration
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests((requests) -> requests
                        .requestMatchers(PUBLIC_MATCHER).permitAll()
                        .anyRequest().authenticated())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptionHandling ->
                        exceptionHandling.authenticationEntryPoint(authEntryPoint))
                // The enabled login methods are decided here, once; the filter never re-reads the settings
                .addFilterBefore(new BearerTokenAuthenticationFilter(
                                authSettings.jwtEnabled() ? jwtGenerator : null,
                                firebaseAuth.getIfAvailable(),
                                userRepository),
                        UsernamePasswordAuthenticationFilter.class);

        // Authentication is done by BearerTokenAuthenticationFilter; this manager only stops
        // Spring Boot from creating a default in-memory user with a generated password.
        http.authenticationManager(customAuthenticationManager());
        return http.build();
    }

    /**
     * Hashes passwords for email/password login. Uses BCrypt and stores the algorithm as a prefix
     * ({@code {bcrypt}...}), so the algorithm can be upgraded later without breaking existing hashes.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * CORS rules used by {@code http.cors()}: the frontend origins from {@code app.cors.allowed-origins}
     * may call the API with any header, using bearer tokens (no cookies).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(parseOrigins(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Creates a custom AuthenticationManager for Firebase authentication.
     * Sets the authentication in the SecurityContextHolder.
     *
     * @return the custom AuthenticationManager instance
     */
    @Bean
    public AuthenticationManager customAuthenticationManager() {
        return authentication -> {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            return authentication;
        };
    }

    private static List<String> parseOrigins(String origins) {
        return Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }
}
