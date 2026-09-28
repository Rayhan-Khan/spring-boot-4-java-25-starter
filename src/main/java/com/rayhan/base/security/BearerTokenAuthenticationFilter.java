package com.rayhan.base.security;

import com.rayhan.base.entity.User;
import com.rayhan.base.repository.UserRepository;
import com.rayhan.base.utils.ResponseBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authenticates requests that carry {@code Authorization: Bearer <token>}. The token's signing algorithm
 * decides how it is checked:
 * <ul>
 *     <li>{@code HS256/384/512}: the application's own access token, verified with JWT_SECRET</li>
 *     <li>{@code RS256}: a Firebase ID token, verified by Firebase; the user is then loaded by Firebase UID</li>
 * </ul>
 * A verifier is {@code null} when its login method is switched off. That is decided once, when
 * {@link SecurityConfig} creates this filter, so requests never re-read the settings.
 * <p>
 * Not a Spring bean on purpose: Spring Boot would also register it with the servlet container and run it twice.
 */
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(BearerTokenAuthenticationFilter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern ALGORITHM = Pattern.compile("\"alg\"\s*:\s*\"([A-Za-z0-9]+)\"");

    private final JwtGenerator jwtGenerator;
    private final FirebaseAuth firebaseAuth;
    private final UserRepository userRepository;

    /**
     * @param jwtGenerator   verifies the app's access tokens, or {@code null} when JWT login is disabled
     * @param firebaseAuth   verifies Firebase ID tokens, or {@code null} when Firebase is disabled
     * @param userRepository loads the user behind a Firebase ID token
     */
    public BearerTokenAuthenticationFilter(JwtGenerator jwtGenerator, FirebaseAuth firebaseAuth,
                                           UserRepository userRepository) {
        this.jwtGenerator = jwtGenerator;
        this.firebaseAuth = firebaseAuth;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            try {
                AuthenticatedUser user = authenticate(authHeader.substring(BEARER_PREFIX.length()).trim());
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(user, null, user.authorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Authenticated user {} ({}) for {}", user.userId(), user.provider(), request.getRequestURI());
            } catch (BadCredentialsException e) {
                log.warn("Rejected bearer token for {}: {}", request.getRequestURI(), e.getMessage());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                MAPPER.writeValue(response.getOutputStream(), ResponseBuilder.error(null, e.getMessage()));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private AuthenticatedUser authenticate(String token) {
        String algorithm = readAlgorithm(token);
        if (algorithm.startsWith("HS") && jwtGenerator != null) {
            return fromAccessToken(token);
        }
        if ("RS256".equals(algorithm) && firebaseAuth != null) {
            return fromFirebaseToken(token);
        }
        throw new BadCredentialsException("Unsupported token, or its login method is not enabled.");
    }

    private AuthenticatedUser fromAccessToken(String token) {
        try {
            JwtGenerator.AccessTokenClaims claims = jwtGenerator.parseAccessToken(token);
            return new AuthenticatedUser(claims.userId(), claims.email(), claims.role(), AuthProvider.JWT);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BadCredentialsException("Invalid or expired access token.");
        }
    }

    private AuthenticatedUser fromFirebaseToken(String token) {
        FirebaseToken decodedToken;
        try {
            decodedToken = firebaseAuth.verifyIdToken(token);
        } catch (FirebaseAuthException e) {
            throw new BadCredentialsException("Invalid or expired Firebase ID token.");
        }
        User user = userRepository.findByFirebaseUserId(decodedToken.getUid())
                .orElseThrow(() -> new BadCredentialsException(
                        "This Firebase account is not registered yet. Call /api/v1/auth/firebase/login first."));
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole().name(), AuthProvider.FIREBASE);
    }

    /**
     * Reads "alg" from the token header without verifying anything; verification follows.
     */
    private static String readAlgorithm(String token) {
        int headerEnd = token.indexOf('.');
        if (headerEnd <= 0) {
            throw new BadCredentialsException("Malformed token.");
        }
        try {
            String header = new String(Base64.getUrlDecoder().decode(token.substring(0, headerEnd)), StandardCharsets.UTF_8);
            Matcher matcher = ALGORITHM.matcher(header);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (IllegalArgumentException e) {
            // not Base64URL: handled below
        }
        throw new BadCredentialsException("Malformed token.");
    }
}
