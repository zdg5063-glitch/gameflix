package com.example.gameflix;

import com.example.gameflix.model.User;
import com.example.gameflix.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoint tests against the real MySQL database.
 * Run configuration needs MYSQL_USER, MYSQL_PASSWORD and JWT_SECRET set.
 * Each test registers its own random email and removes it afterward.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthEndpointsTests {

    private static final String PASSWORD = "Throwaway123";
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtEncoder jwtEncoder;

    private final List<String> createdEmails = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        createdEmails.forEach(email -> userRepository.findByEmail(email).ifPresent(userRepository::delete));
        createdEmails.clear();
    }

    // ---------- helpers ----------

    private String newEmail() {
        String email = "test-" + UUID.randomUUID() + "@example.com";
        createdEmails.add(email);
        return email;
    }

    private MvcResult register(String email, String password) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + password
                + "\",\"displayName\":\"Test User\"}";
        return mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn();
    }

    private MvcResult login(String email, String password) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn();
    }

    private String extractToken(String responseBody) {
        Matcher matcher = TOKEN_PATTERN.matcher(responseBody);
        assertThat(matcher.find()).as("login response should contain a token").isTrue();
        return matcher.group(1);
    }

    /** Registers a new user, logs in, and returns the token. */
    private String registerAndLogin(String email) throws Exception {
        assertThat(register(email, PASSWORD).getResponse().getStatus()).isEqualTo(201);
        MvcResult loginResult = login(email, PASSWORD);
        assertThat(loginResult.getResponse().getStatus()).isEqualTo(200);
        return extractToken(loginResult.getResponse().getContentAsString());
    }

    // ---------- the six required cases ----------

    // 1. Successful registration
    @Test
    void registerSucceeds_returns201_andStoresOnlyAHash() throws Exception {
        String email = newEmail();

        MvcResult result = register(email, PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains(email);
        assertThat(body).doesNotContainIgnoringCase("password");

        User stored = userRepository.findByEmail(email).orElseThrow();
        assertThat(stored.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(stored.getPasswordHash()).startsWith("{bcrypt}");
    }

    // 2. Duplicate-email rejection
    @Test
    void registerDuplicateEmail_returns409() throws Exception {
        String email = newEmail();
        assertThat(register(email, PASSWORD).getResponse().getStatus()).isEqualTo(201);

        MvcResult second = register(email, PASSWORD);

        assertThat(second.getResponse().getStatus()).isEqualTo(409);
    }

    // 3. Successful login returning a token
    @Test
    void loginSucceeds_returns200_withThreePartToken() throws Exception {
        String email = newEmail();
        register(email, PASSWORD);

        MvcResult result = login(email, PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String token = extractToken(result.getResponse().getContentAsString());
        assertThat(token.split("\\.")).hasSize(3);
    }

    // 4. Login with a wrong password (and the generic-message rule)
    @Test
    void loginWrongPassword_returns401_withSameMessageAsUnknownEmail() throws Exception {
        String email = newEmail();
        register(email, PASSWORD);

        MvcResult wrongPassword = login(email, "WrongPassword1");
        MvcResult unknownEmail = login("nobody-" + UUID.randomUUID() + "@example.com", PASSWORD);

        assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknownEmail.getResponse().getStatus()).isEqualTo(401);
        assertThat(wrongPassword.getResponse().getContentAsString())
                .isEqualTo(unknownEmail.getResponse().getContentAsString());
    }

    // 5. Protected call with a valid token
    @Test
    void protectedRoute_withValidToken_returns200_andOwnProfile() throws Exception {
        String email = newEmail();
        String token = registerAndLogin(email);

        MvcResult result = mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains(email);
        assertThat(body).doesNotContainIgnoringCase("password");
    }

    // 6a. Protected call with a missing token
    @Test
    void protectedRoute_withMissingToken_returns401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    // 6b. Protected call with a tampered token (one signature character changed)
    @Test
    void protectedRoute_withTamperedToken_returns401() throws Exception {
        String token = registerAndLogin(newEmail());

        int signatureStart = token.lastIndexOf('.') + 1;
        char original = token.charAt(signatureStart);
        char replacement = (original == 'A') ? 'B' : 'A';
        String tampered = token.substring(0, signatureStart) + replacement
                + token.substring(signatureStart + 1);

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    // ---------- extra: expired token ----------

    @Test
    void protectedRoute_withExpiredToken_returns401() throws Exception {
        String email = newEmail();
        register(email, PASSWORD);
        User user = userRepository.findByEmail(email).orElseThrow();

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(user.getId()))
                .issuedAt(now.minus(3, ChronoUnit.HOURS))
                .expiresAt(now.minus(2, ChronoUnit.HOURS))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String expiredToken = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }
}