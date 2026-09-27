package com.mss.base.service;

import com.mss.base.dto.FirebaseRegisterRequest;
import com.mss.base.response.LoginResponse;
import com.mss.base.response.UserRegistrationResponse;

/**
 * Registration and login with Firebase ID tokens. Throws
 * {@link com.mss.base.exception.FirebaseNotConfiguredException} when {@code FIREBASE_ENABLED=false}.
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
