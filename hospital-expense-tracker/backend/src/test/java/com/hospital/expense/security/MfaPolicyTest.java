package com.hospital.expense.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hospital.expense.model.User;
import org.junit.jupiter.api.Test;

class MfaPolicyTest {
    private static User user(String role, boolean enabled) {
        User u = new User("x", "h", "X", role);
        u.totpEnabled = enabled;
        return u;
    }

    @Test
    void requiresTheConfiguredRolesOnly() {
        MfaPolicy p = new MfaPolicy("SUPER_ADMIN, FINANCE_ADMIN");
        assertTrue(p.requiredFor(user("SUPER_ADMIN", false)));
        assertTrue(p.requiredFor(user("FINANCE_ADMIN", true)));
        assertFalse(p.requiredFor(user("STAFF", false)));
    }

    @Test
    void setupIsPendingOnlyUntilTwoFactorIsOn() {
        MfaPolicy p = new MfaPolicy("AUDITOR");
        assertTrue(p.setupPending(user("AUDITOR", false)));
        assertFalse(p.setupPending(user("AUDITOR", true)));
    }

    @Test
    void noneOrBlankTurnsItOff() {
        assertFalse(new MfaPolicy("none").requiredFor(user("SUPER_ADMIN", false)));
        assertFalse(new MfaPolicy("").requiredFor(user("SUPER_ADMIN", false)));
    }

    @Test
    void usersWhoMustSetUpTwoFactorMayOnlyReachTheSetupEndpoints() {
        assertTrue(JwtFilter.allowedBeforeMfaSetup("/api/auth/me"));
        assertTrue(JwtFilter.allowedBeforeMfaSetup("/api/account/mfa/setup"));
        assertTrue(JwtFilter.allowedBeforeMfaSetup("/index.html"));
        assertFalse(JwtFilter.allowedBeforeMfaSetup("/api/expenses"));
        assertFalse(JwtFilter.allowedBeforeMfaSetup("/api/users"));
    }
}
