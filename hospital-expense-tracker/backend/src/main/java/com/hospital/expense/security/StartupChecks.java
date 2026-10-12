package com.hospital.expense.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** In the "prod" profile (the Docker image uses it) the app refuses to start with insecure defaults. */
@Component
@Profile("prod")
public class StartupChecks {
    public static final String DEV_JWT_SECRET = "dev-only-secret-change-me-please-32-chars-minimum";
    public static final String DEFAULT_SEED_PASSWORD = "Password@123";

    private final String jwtSecret, seedMode, seedPassword;
    private final boolean seedEnabled;

    public StartupChecks(@Value("${app.jwt.secret}") String jwtSecret, @Value("${app.seed.enabled}") boolean seedEnabled,
                         @Value("${app.seed.mode}") String seedMode, @Value("${app.seed.password}") String seedPassword) {
        this.jwtSecret = jwtSecret; this.seedEnabled = seedEnabled; this.seedMode = seedMode; this.seedPassword = seedPassword;
    }

    @PostConstruct
    void check() { validate(jwtSecret, seedEnabled, seedMode, seedPassword); }

    public static void validate(String jwtSecret, boolean seedEnabled, String seedMode, String seedPassword) {
        if (DEV_JWT_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException("Refusing to start: set JWT_SECRET to a long random value (see the deployment guide).");
        }
        if (seedEnabled && !"bootstrap".equalsIgnoreCase(seedMode) && DEFAULT_SEED_PASSWORD.equals(seedPassword)) {
            throw new IllegalStateException("Refusing to start: set a strong SEED_PASSWORD, or use SEED_MODE=bootstrap with ADMIN_PASSWORD, or set SEED_DATA=false.");
        }
    }
}
