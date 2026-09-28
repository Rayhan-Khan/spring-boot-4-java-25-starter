package com.rayhan.base.enums;

/**
 * Application roles. Spring Security sees them as {@code ROLE_USER} / {@code ROLE_ADMIN},
 * so endpoints can use {@code @PreAuthorize("hasRole('ADMIN')")}.
 */
public enum UserRole {
    USER,
    ADMIN
}
