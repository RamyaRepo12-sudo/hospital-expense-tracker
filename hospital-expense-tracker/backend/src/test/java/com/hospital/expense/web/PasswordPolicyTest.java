package com.hospital.expense.web;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PasswordPolicyTest {
    @Test
    void acceptsLongPasswordWithLetterAndNumber() {
        assertDoesNotThrow(() -> UserController.checkPassword("Wq7mFz3LpRt9"));
        assertDoesNotThrow(() -> UserController.checkPassword("correct horse 42 battery", "meera"));
    }

    @Test
    void rejectsShortOrSimplePasswords() {
        assertThrows(ResponseStatusException.class, () -> UserController.checkPassword("short1"));
        assertThrows(ResponseStatusException.class, () -> UserController.checkPassword("onlyletterslong"));
        assertThrows(ResponseStatusException.class, () -> UserController.checkPassword("1234567890123"));
    }

    @Test
    void rejectsCommonPasswordsAndPasswordsContainingTheUsername() {
        assertThrows(ResponseStatusException.class, () -> UserController.checkPassword("Password12345"));
        assertThrows(ResponseStatusException.class, () -> UserController.checkPassword("Qwerty2026abc"));
        assertThrows(ResponseStatusException.class, () -> UserController.checkPassword("meera.nair2026", "meera.nair"));
    }
}
