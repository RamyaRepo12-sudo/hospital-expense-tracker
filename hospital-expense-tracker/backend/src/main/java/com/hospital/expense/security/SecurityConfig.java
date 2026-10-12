package com.hospital.expense.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * One origin serves both the web app and the API, so there is no CORS at all.
 * Defences: HttpOnly session cookie, optional 2FA and SSO, CSRF header check, per-IP rate limit, strict security headers (CSP, HSTS, no framing).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
            + "font-src 'self' https://fonts.gstatic.com; img-src 'self' data: blob:; connect-src 'self'; object-src 'none'; "
            + "base-uri 'self'; form-action 'self'; frame-ancestors 'none'";

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtFilter jwt, CsrfHeaderFilter csrf, RateLimitFilter rate) throws Exception {
        http.csrf(c -> c.disable()) // replaced by CsrfHeaderFilter + SameSite=Strict cookie
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(h -> h
                .contentSecurityPolicy(c -> c.policyDirectives(CSP))
                .frameOptions(f -> f.deny())
                .httpStrictTransportSecurity(s -> s.includeSubDomains(true).maxAgeInSeconds(31_536_000))
                .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .permissionsPolicy(p -> p.policy("camera=(), microphone=(), geolocation=(), payment=()")))
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/mfa", "/api/auth/logout").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/config", "/api/auth/sso/login", "/api/auth/sso/callback").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()) // static web app files; they contain no data
            .addFilterBefore(rate, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(csrf, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
}
