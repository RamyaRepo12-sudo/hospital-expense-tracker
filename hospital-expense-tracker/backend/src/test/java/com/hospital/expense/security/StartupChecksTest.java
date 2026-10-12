package com.hospital.expense.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class StartupChecksTest {
    private static final String GOOD_SECRET = "x7Qp2LmN8vTz4RkW1sYb6HdJ9cEaF3uG5oIe0";

    @Test
    void rejectsDevelopmentJwtSecret() {
        assertThrows(IllegalStateException.class, () -> StartupChecks.validate(StartupChecks.DEV_JWT_SECRET, false, "demo", "x"));
    }

    @Test
    void rejectsDefaultDemoPasswordWhenSeedingDemoData() {
        assertThrows(IllegalStateException.class, () -> StartupChecks.validate(GOOD_SECRET, true, "demo", StartupChecks.DEFAULT_SEED_PASSWORD));
    }

    @Test
    void allowsStrongSettings() {
        assertDoesNotThrow(() -> StartupChecks.validate(GOOD_SECRET, true, "demo", "Str0ng-Demo-Pass"));
        assertDoesNotThrow(() -> StartupChecks.validate(GOOD_SECRET, true, "bootstrap", StartupChecks.DEFAULT_SEED_PASSWORD));
        assertDoesNotThrow(() -> StartupChecks.validate(GOOD_SECRET, false, "demo", StartupChecks.DEFAULT_SEED_PASSWORD));
    }
}
