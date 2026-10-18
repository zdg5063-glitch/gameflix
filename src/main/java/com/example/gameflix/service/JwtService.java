package com.example.gameflix.service;

import com.example.gameflix.model.User;
import org.springframework.beans.factory.annotation.Value;
// The algorithm the token is signed with (HS256)
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
// The pieces a token is built from: header, claims (payload), and the encoder that signs them
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

// Java time classes used to set when the token was issued and when it expires
import java.time.Instant;
import java.time.temporal.ChronoUnit;

// @Service: Spring creates one of these at startup and gives it to any class that asks (AuthService uses it)
@Service
public class JwtService {

    // The object that signs tokens (built in SecurityConfig with our secret key)
    private final JwtEncoder jwtEncoder;
    // How many minutes a token lasts (app.jwt.expiration-minutes, currently 30)
    private final long expirationMinutes;

    // Spring passes in the JwtEncoder bean and the expiration setting when it creates this class
    public JwtService(JwtEncoder jwtEncoder,
                      @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this.jwtEncoder = jwtEncoder;
        this.expirationMinutes = expirationMinutes;
    }

    // Builds and signs a token for a user who just logged in successfully
    public String createToken(User user) {
        // The current moment; used for both issued-at and expiry
        Instant now = Instant.now();
        // Claims = the payload of the token. Only the three things needed, nothing confidential
        JwtClaimsSet claims = JwtClaimsSet.builder()
                // sub: who the token is for (the user id, as text)
                .subject(String.valueOf(user.getId()))
                // iat: when the token was issued
                .issuedAt(now)
                // exp: when the token stops being valid (now + 30 minutes); checked on every request
                .expiresAt(now.plus(expirationMinutes, ChronoUnit.MINUTES))
                .build();
        // Header says which algorithm signed the token; must be set explicitly because
        // the encoder's default assumes a different algorithm than the HS256 key we use
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        // Sign it, then return the finished token as text in the form header.payload.signature
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    // Token lifetime in seconds; the login response sends this back to the client as expiresInSeconds
    public long getExpirationSeconds() {
        return expirationMinutes * 60;
    }
}