package com.rayhan.base.annotation;

import com.rayhan.base.validation.AvatarValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = AvatarValidator.class)
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidAvatar {
    String message() default "Invalid file format or size.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

