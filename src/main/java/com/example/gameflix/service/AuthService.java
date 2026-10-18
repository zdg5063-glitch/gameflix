package com.example.gameflix.service;

// Data shapes coming in (requests) and going out (responses)
import com.example.gameflix.dto.LoginRequest;
import com.example.gameflix.dto.LoginResponse;
import com.example.gameflix.dto.RegisterRequest;
import com.example.gameflix.dto.UserProfileResponse;
// Database access for users
import com.example.gameflix.repository.UserRepository;
// Password hashing and checking (the bcrypt encoder built in SecurityConfig)
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
// Our own exception for "email already registered"
import com.example.gameflix.exception.DuplicateEmailException;
import com.example.gameflix.model.User;
// Spring's exception for a database rule being broken (such as the unique email constraint)
import org.springframework.dao.DataIntegrityViolationException;
import java.util.Locale;
// Exception that Spring Security turns into a 401
import org.springframework.security.authentication.BadCredentialsException;

// @Service: Spring creates one of these at startup and gives it to the controllers that need it
@Service
public class AuthService {

    // Reads and writes rows in the users table
    private final UserRepository userRepository;
    // Hashes passwords at registration and checks them at login
    private final PasswordEncoder passwordEncoder;
    // Builds the signed token after a successful login
    private final JwtService jwtService;

    // Spring passes in the three objects this class needs when it creates it
    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    // Creates a new account; returns the new user's profile (never the password or hash)
    public UserProfileResponse register(RegisterRequest request) {
        // Remove stray spaces and lowercase, so "A@x.com" and "a@x.com" are the same account
        // (Locale.ROOT keeps lowercasing the same on every computer)
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        // Build the new user object to save
        User user = new User();
        user.setEmail(email);
        // Hash the password with bcrypt; only the hash is stored, never the plain password
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName().trim());

        try {
            // saveAndFlush sends the INSERT to MySQL immediately, so a duplicate email fails
            // here inside the try block (plain save might wait and fail later, outside it)
            User saved = userRepository.saveAndFlush(user);
            return toProfile(saved);
        } catch (DataIntegrityViolationException e) {
            // The database's unique constraint (uk_users_email) rejected it: email already exists.
            // Turned into a 409 by GlobalExceptionHandler. We don't check first and then insert,
            // because two simultaneous requests could both pass that check
            throw new DuplicateEmailException();
        }
    }

    // Converts a User entity into the response sent to the client; leaves out the password hash
    private UserProfileResponse toProfile(User user) {
        return new UserProfileResponse(
                user.getId(), user.getEmail(), user.getDisplayName(), user.getCreatedAt());
    }

    // Checks credentials; returns a token if they are correct, otherwise throws (becomes a 401)
    public LoginResponse login(LoginRequest request) {
        // Same cleanup as registration so the email matches what was stored
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        // Passwords over 64 characters could never have been registered (and bcrypt only uses
        // 72 bytes), so reject them early with the same generic message
        if (request.password().length() > 64) {
            throw new BadCredentialsException("Invalid email or password");
        }

        // Look the user up by email; if there is none, fail with the same generic message
        // (so an attacker can't tell which emails have accounts)
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        // Check the password against the stored hash: bcrypt reads the {bcrypt} prefix and the
        // cost from the hash, hashes the typed password the same way, and compares
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            // Wrong password: identical message and status as an unknown email
            throw new BadCredentialsException("Invalid email or password");
        }

        // Credentials are correct: create a signed token and return it with its type and lifetime
        String token = jwtService.createToken(user);
        return new LoginResponse(token, "Bearer", jwtService.getExpirationSeconds());
    }

    // Returns the profile for /api/users/me; subject is the "sub" claim from the verified token
    public UserProfileResponse getProfile(String subject) {
        long id;
        try {
            // The token stores the user id as text, so convert it back to a number
            id = Long.parseLong(subject);
        } catch (NumberFormatException e) {
            // The id wasn't a number (shouldn't happen with a token we signed)
            throw new BadCredentialsException("Invalid token");
        }
        // Load the user from the database; fail if the account no longer exists
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BadCredentialsException("Invalid token"));
        return toProfile(user);
    }
}