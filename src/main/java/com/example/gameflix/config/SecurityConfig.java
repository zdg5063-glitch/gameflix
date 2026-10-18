package com.example.gameflix.config;

// Annotations: read property values, mark beans, mark a configuration class
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
// Tools for defining which requests need a token and how sessions behave
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
// Tools for the CORS rules (which websites may call this API from a browser)
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
// Tools for creating (signing) and checking (decoding) JWTs
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
// Tools for turning the secret text into a cryptographic key
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
// Plain Java collections
import java.util.HashMap;
import java.util.Map;
// Password hashing
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;

import java.util.List;

// Spring reads this class at startup to set up security
@Configuration
public class SecurityConfig {

    // The one website allowed to call this API from a browser (from CORS_ORIGIN, default http://localhost:3000)
    @Value("${app.cors.allowed-origin}")
    private String allowedOrigin;

    // The secret used to sign and verify tokens, read from the JWT_SECRET environment variable
    @Value("${app.jwt.secret}")
    private String jwtSecret;

    // Turns the secret text into a key object that can sign and verify tokens
    private SecretKey signingKey() {
        // Cryptography works on bytes, so convert the secret text to bytes
        byte[] bytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        // HS256 needs at least 32 bytes (256 bits); crash at startup rather than run with a weak secret
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
        }
        // Wrap the bytes as a key labeled for the HMAC-SHA256 algorithm (the one HS256 uses)
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    // @Bean: Spring keeps the returned object and gives it to any class that asks for it
    // This one signs new tokens (JwtService uses it at login)
    @Bean
    public JwtEncoder jwtEncoder() {
        // ImmutableSecret tells the Nimbus library to always use this one fixed key
        return new NimbusJwtEncoder(new ImmutableSecret<>(signingKey()));
    }

    // Checks incoming tokens: rejects them if the signature is wrong or exp has passed
    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(signingKey())
                .macAlgorithm(MacAlgorithm.HS256) // the algorithm tokens must be signed with
                .build();
    }

    // Hashes passwords with bcrypt at work factor 12
    @Bean
    public PasswordEncoder passwordEncoder() {
        // Map of algorithm name -> encoder
        Map<String, PasswordEncoder> encoders = new HashMap<>();
        // The "bcrypt" entry uses strength 12 (chosen after measuring 10, 12 and 14)
        encoders.put("bcrypt", new BCryptPasswordEncoder(12));
        // "bcrypt" is the default for new hashes; the {bcrypt} prefix is stored so the
        // algorithm is recorded and old hashes still verify if the settings change later
        return new DelegatingPasswordEncoder("bcrypt", encoders);
    }

    // Defines the security rules every request passes through before reaching a controller
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Turn on CORS using the corsConfigurationSource bean below
                .cors(Customizer.withDefaults())
                // CSRF attacks rely on browsers sending cookies automatically; this API uses a token header, no cookies
                .csrf(AbstractHttpConfigurer::disable) // fine for a stateless token API
                // No sessions: the server remembers nothing between requests, every request carries its own token
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Access rules, checked in order (first match wins)
                .authorizeHttpRequests(auth -> auth
                        // Register and login are open to everyone (the user has no token yet)
                        .requestMatchers("/api/auth/**").permitAll()
                        // Everything else (like /api/users/me) needs a valid token
                        .anyRequest().authenticated());

        // Read "Authorization: Bearer <token>", check it with the JwtDecoder bean,
        // and answer 401 if the token is missing, tampered with or expired
        http.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        // Finish building and hand the rules to Spring
        return http.build();
    }

    // The CORS rules: which browser requests from other websites are allowed
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // Start with an empty set of rules
        CorsConfiguration config = new CorsConfiguration();
        // Allow exactly one website (the origin read from CORS_ORIGIN)
        config.setAllowedOrigins(List.of(allowedOrigin)); // one named origin, never "*"
        // Only these HTTP methods (OPTIONS is the browser's "may I?" preflight check)
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        // Only these request headers: the token and the JSON content type
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // Holds the CORS rules per URL path
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // "/**" means apply these rules to every path
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}