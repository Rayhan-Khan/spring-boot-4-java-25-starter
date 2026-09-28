package com.rayhan.base.validation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class EmailValidatorTest {

    private final EmailValidator validator = new EmailValidator();

    @ParameterizedTest
    @ValueSource(strings = {"user@example.com", "first.last+tag@mail.example.org", "user_1@sub-domain.io"})
    void acceptsValidEmails(String email) {
        assertThat(validator.isValid(email, null)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"plainaddress", "user@", "@example.com", "user name@example.com"})
    void rejectsInvalidEmails(String email) {
        assertThat(validator.isValid(email, null)).isFalse();
    }
}
