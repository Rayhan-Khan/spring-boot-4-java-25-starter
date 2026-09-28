package com.rayhan.base.entity;

import com.rayhan.base.enums.UserRole;
import com.rayhan.base.enums.UserStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import java.io.Serial;

@Getter
@Setter
@Entity
@DynamicInsert
@DynamicUpdate
@NoArgsConstructor
@Table(name = "users")
public class User extends BaseEntityWithUpdate {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "first_name", length = 100, nullable = false)
    private String firstName;

    @Column(name = "last_name", length = 100, nullable = false)
    private String lastName;

    @Column(name = "email", length = 256, nullable = false, unique = true)
    @Email
    @Size(max = 256)
    private String email;

    @Column(name = "is_email_verified")
    private Boolean isEmailVerified = false;

    /**
     * Set for users who signed in with Firebase; null for email/password-only users.
     */
    @Column(name = "firebase_user_id", length = 100, unique = true)
    private String firebaseUserId;

    /**
     * BCrypt hash (with {@code {bcrypt}} prefix) for email/password login; null for Firebase-only users.
     */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20, nullable = false)
    private UserRole role = UserRole.USER;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "status", length = 20)
    private UserStatus userStatus;
}
