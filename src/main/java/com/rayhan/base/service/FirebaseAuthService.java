package com.rayhan.base.service;

import com.rayhan.base.dto.FirebaseRegisterRequest;
import com.rayhan.base.response.LoginResponse;
import com.rayhan.base.response.UserRegistrationResponse;

/**
 * Registration and login with Firebase ID tokens. Throws
 * {@link com.rayhan.base.exception.FirebaseNotConfiguredException} when {@code FIREBASE_ENABLED=false}.
 */
public interface FirebaseAuthService {

    UserRegistrationResponse registerUser(FirebaseRegisterRequest request);

    String resendVerificationEmail(String email);

    /**
     * Logs in with a Firebase ID token. When JWT login is enabled, the response also contains
     * the application's access and refresh tokens.
     */
    LoginResponse login(String idToken);
}
