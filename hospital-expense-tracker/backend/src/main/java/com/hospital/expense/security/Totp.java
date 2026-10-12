package com.hospital.expense.security;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Time-based one-time passwords (RFC 6238, SHA-1, 6 digits, 30 s): works with Google/Microsoft Authenticator, Authy, 1Password, etc. */
public final class Totp {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final long STEP_MS = 30_000;

    private Totp() {}

    public static String newSecret() {
        byte[] b = new byte[20];
        new SecureRandom().nextBytes(b);
        return base32(b);
    }

    static String base32(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte x : data) {
            buffer = (buffer << 8) | (x & 0xFF);
            bits += 8;
            while (bits >= 5) {
                out.append(ALPHABET.charAt((buffer >>> (bits - 5)) & 31));
                bits -= 5;
            }
            buffer &= (1 << bits) - 1;
        }
        if (bits > 0) out.append(ALPHABET.charAt((buffer << (5 - bits)) & 31));
        return out.toString();
    }

    static byte[] decode(String text) {
        String t = text.replace("=", "").replace(" ", "").toUpperCase();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0, bits = 0;
        for (char c : t.toCharArray()) {
            int v = ALPHABET.indexOf(c);
            if (v < 0) throw new IllegalArgumentException("Invalid base32 secret");
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >>> (bits - 8)) & 0xFF);
                bits -= 8;
            }
            buffer &= (1 << bits) - 1;
        }
        return out.toByteArray();
    }

    public static String code(byte[] key, long step, int digits) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int off = h[h.length - 1] & 0xF;
            int bin = ((h[off] & 0x7F) << 24) | ((h[off + 1] & 0xFF) << 16) | ((h[off + 2] & 0xFF) << 8) | (h[off + 3] & 0xFF);
            int otp = bin % (int) Math.pow(10, digits);
            return String.format("%0" + digits + "d", otp);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Returns the matched time step, or -1 when the code is wrong. Accepts one step either side of now (clock drift)
     * and never a step at or before lastStep, so a code cannot be used twice.
     */
    public static long verify(String base32Secret, String code, long nowMs, long lastStep) {
        if (code == null || !code.matches("\\d{6}")) return -1;
        byte[] key = decode(base32Secret);
        long current = nowMs / STEP_MS;
        for (long s = current - 1; s <= current + 1; s++) {
            if (s > lastStep && MessageDigest.isEqual(code(key, s, 6).getBytes(StandardCharsets.UTF_8), code.getBytes(StandardCharsets.UTF_8))) return s;
        }
        return -1;
    }

    public static String otpauthUri(String issuer, String account, String secret) {
        String i = URLEncoder.encode(issuer, StandardCharsets.UTF_8).replace("+", "%20");
        String a = URLEncoder.encode(account, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + i + ":" + a + "?secret=" + secret + "&issuer=" + i + "&algorithm=SHA1&digits=6&period=30";
    }
}
