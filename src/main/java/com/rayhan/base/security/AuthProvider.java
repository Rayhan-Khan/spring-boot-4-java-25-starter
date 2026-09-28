package com.rayhan.base.security;

/**
 * How the current request was authenticated.
 */
public enum AuthProvider {
    /** The application's own access token (email/password login or Firebase login exchange). */
    JWT,
    /** A Firebase ID token sent directly. */
    FIREBASE
}
