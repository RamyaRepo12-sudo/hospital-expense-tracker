package com.hospital.expense.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** One-time recovery codes for a lost phone. Only keyed hashes are stored; the plain codes are shown once. */
public final class RecoveryCodes {
    private static final String CHARS = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private RecoveryCodes() {}

    public static List<String> generate(int count) {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 10; j++) {
                if (j == 5) sb.append('-');
                sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
            }
            codes.add(sb.toString());
        }
        return codes;
    }

    public static String normalize(String code) { return code == null ? "" : code.toLowerCase().replaceAll("[^a-z0-9]", ""); }

    public static boolean looksLikeRecoveryCode(String code) { return normalize(code).length() == 10 && !code.trim().matches("\\d{6}"); }

    public static String hash(String code, String secretMaterial) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretMaterial.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(normalize(code).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
