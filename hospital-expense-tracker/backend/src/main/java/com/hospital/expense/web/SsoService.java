package com.hospital.expense.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.expense.security.JwtUtil;
import com.hospital.expense.security.Pkce;
import io.jsonwebtoken.Claims;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.stereotype.Service;

/**
 * Single sign-on with OpenID Connect (authorization-code flow with PKCE, server-side code exchange).
 * Works with Microsoft Entra ID, Google Workspace, Keycloak, Okta and other standard providers.
 * A user is matched to an EXISTING active account by verified email; accounts are never created automatically.
 */
@Service
public class SsoService {
    public record Start(String redirect, String stateToken) {}

    private final boolean enabled;
    private final String issuer, clientId, clientSecret, scopes, redirectUri;
    private final JwtUtil jwt;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private volatile JsonNode metadata;
    private volatile JwtDecoder decoder;

    public SsoService(@Value("${app.sso.enabled}") boolean enabled, @Value("${app.sso.issuer}") String issuer,
                      @Value("${app.sso.client-id}") String clientId, @Value("${app.sso.client-secret}") String clientSecret,
                      @Value("${app.sso.scopes}") String scopes, @Value("${app.public-url}") String publicUrl, JwtUtil jwt, ObjectMapper mapper) {
        if (enabled && (issuer.isBlank() || clientId.isBlank() || clientSecret.isBlank())) {
            throw new IllegalStateException("SSO_ENABLED=true requires OIDC_ISSUER, OIDC_CLIENT_ID and OIDC_CLIENT_SECRET");
        }
        this.enabled = enabled; this.issuer = issuer; this.clientId = clientId; this.clientSecret = clientSecret; this.scopes = scopes;
        this.redirectUri = publicUrl.replaceAll("/+$", "") + "/api/auth/sso/callback";
        this.jwt = jwt; this.mapper = mapper;
    }

    public boolean enabled() { return enabled; }

    private JsonNode metadata() throws Exception {
        JsonNode m = metadata;
        if (m == null) {
            HttpRequest req = HttpRequest.newBuilder(URI.create(issuer.replaceAll("/+$", "") + "/.well-known/openid-configuration")).timeout(Duration.ofSeconds(8)).GET().build();
            HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) throw new IllegalStateException("OIDC discovery failed: HTTP " + r.statusCode());
            m = mapper.readTree(r.body());
            metadata = m;
        }
        return m;
    }

    private JwtDecoder decoder() {
        JwtDecoder d = decoder;
        if (d == null) {
            d = JwtDecoders.fromIssuerLocation(issuer);
            decoder = d;
        }
        return d;
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    public Start start() throws Exception {
        String state = Pkce.random(24), nonce = Pkce.random(24), verifier = Pkce.random(48);
        String endpoint = metadata().get("authorization_endpoint").asText();
        String url = endpoint + (endpoint.contains("?") ? "&" : "?") + "response_type=code&client_id=" + enc(clientId) + "&redirect_uri=" + enc(redirectUri)
                + "&scope=" + enc(scopes) + "&state=" + state + "&nonce=" + nonce + "&code_challenge=" + Pkce.challenge(verifier) + "&code_challenge_method=S256";
        String token = jwt.createScoped("sso", "sso", Map.of("state", state, "nonce", nonce, "verifier", verifier), 10 * 60_000L);
        return new Start(url, token);
    }

    /** Completes the flow and returns the verified email address of the person who signed in. */
    public String finish(String code, String state, String stateToken) throws Exception {
        Claims c = jwt.parseScoped(stateToken, "sso");
        if (!MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8), c.get("state", String.class).getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalStateException("state mismatch");
        }
        String body = "grant_type=authorization_code&code=" + enc(code) + "&redirect_uri=" + enc(redirectUri) + "&client_id=" + enc(clientId)
                + "&client_secret=" + enc(clientSecret) + "&code_verifier=" + enc(c.get("verifier", String.class));
        HttpRequest req = HttpRequest.newBuilder(URI.create(metadata().get("token_endpoint").asText())).timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/x-www-form-urlencoded").header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) throw new IllegalStateException("token endpoint returned HTTP " + r.statusCode());
        String idToken = mapper.readTree(r.body()).path("id_token").asText("");
        if (idToken.isEmpty()) throw new IllegalStateException("no id_token in response");
        Jwt j = decoder().decode(idToken); // checks signature, issuer and expiry
        if (j.getAudience() == null || !j.getAudience().contains(clientId)) throw new IllegalStateException("wrong audience");
        if (!c.get("nonce", String.class).equals(j.getClaimAsString("nonce"))) throw new IllegalStateException("nonce mismatch");
        String email = emailFrom(j.getClaims());
        if (email == null) throw new IllegalStateException("no verified email address in the ID token");
        return email;
    }

    static String emailFrom(Map<String, Object> claims) {
        Object verified = claims.get("email_verified");
        if (Boolean.FALSE.equals(verified) || "false".equals(String.valueOf(verified))) return null;
        for (String k : List.of("email", "preferred_username", "upn")) {
            Object v = claims.get(k);
            if (v instanceof String s && s.contains("@")) return s.trim().toLowerCase();
        }
        return null;
    }
}
