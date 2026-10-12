package com.hospital.expense.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Roles: SUPER_ADMIN, FINANCE_ADMIN, DEPARTMENT_MANAGER, ACCOUNTANT, STAFF, AUDITOR */
@Document("users")
public class User {
    @Id public String id;
    @Indexed(unique = true) public String username;
    public String passwordHash;
    public String name;
    public String role;
    public boolean active = true;
    public String email;
    public boolean mustChangePassword;
    /** Bumped on password change, reset or deactivation. Tokens carrying an older number stop working immediately. */
    public int tokenVersion;
    /** Two-factor authentication (TOTP). Secrets are stored encrypted (SecretBox); recovery codes only as keyed hashes. */
    public boolean totpEnabled;
    public String totpSecretEnc, totpPendingEnc;
    public long totpLastStep;
    public java.util.List<String> recoveryHashes = new java.util.ArrayList<>();

    public User() {}
    public User(String username, String passwordHash, String name, String role) {
        this.username = username; this.passwordHash = passwordHash; this.name = name; this.role = role;
    }
}
