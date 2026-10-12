package com.hospital.expense.web;

import com.hospital.expense.model.User;
import com.hospital.expense.repo.UserRepository;
import com.hospital.expense.security.RecoveryCodes;
import com.hospital.expense.security.SecretBox;
import com.hospital.expense.security.Totp;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MfaService {
    private final SecretBox box;
    private final UserRepository users;
    private final String hashKey;

    public MfaService(SecretBox box, UserRepository users, @Value("${app.mfa.key}") String mfaKey, @Value("${app.jwt.secret}") String jwtSecret) {
        this.box = box; this.users = users;
        this.hashKey = mfaKey == null || mfaKey.isBlank() ? jwtSecret : mfaKey;
    }

    /** Accepts a 6-digit authenticator code or a one-time recovery code, and records its use so it cannot be replayed. */
    public boolean verifyAndConsume(User u, String code) {
        if (!u.totpEnabled || u.totpSecretEnc == null) return false;
        String c = code == null ? "" : code.trim();
        if (c.matches("\\d{6}")) {
            long step = Totp.verify(box.open(u.totpSecretEnc), c, System.currentTimeMillis(), u.totpLastStep);
            if (step < 0) return false;
            u.totpLastStep = step;
            users.save(u);
            return true;
        }
        if (RecoveryCodes.looksLikeRecoveryCode(c) && u.recoveryHashes.remove(RecoveryCodes.hash(c, hashKey))) {
            users.save(u);
            return true;
        }
        return false;
    }

    public List<String> issueRecoveryCodes(User u) {
        List<String> codes = RecoveryCodes.generate(10);
        u.recoveryHashes = codes.stream().map(c -> RecoveryCodes.hash(c, hashKey)).collect(Collectors.toCollection(ArrayList::new));
        return codes;
    }

    public void clear(User u) {
        u.totpEnabled = false; u.totpSecretEnc = null; u.totpPendingEnc = null; u.totpLastStep = 0;
        u.recoveryHashes = new ArrayList<>();
    }
}
