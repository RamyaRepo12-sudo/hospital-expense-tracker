package com.hospital.expense.security;

import com.hospital.expense.model.User;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Which roles must use two-factor authentication (MFA_REQUIRED_ROLES, e.g. SUPER_ADMIN,FINANCE_ADMIN; "none" turns it off). */
@Component
public class MfaPolicy {
    private final Set<String> roles;

    public MfaPolicy(@Value("${app.mfa.required-roles:}") String csv) {
        this.roles = Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty() && !s.equalsIgnoreCase("none")).collect(Collectors.toSet());
    }

    public boolean requiredFor(User u) { return roles.contains(u.role); }

    public boolean setupPending(User u) { return requiredFor(u) && !u.totpEnabled; }
}
