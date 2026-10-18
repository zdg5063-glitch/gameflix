package com.example.gameflix.controller;

// Data shapes coming in (requests) and going out (responses)
import com.example.gameflix.dto.LoginRequest;
import com.example.gameflix.dto.LoginResponse;
import com.example.gameflix.dto.RegisterRequest;
import com.example.gameflix.dto.UserProfileResponse;
// The class that does the real work
import com.example.gameflix.service.AuthService;
// Makes Spring check the validation rules on the request (@NotBlank, @Email, @Size...)
import jakarta.validation.Valid;
// HTTP status codes and the wrapper that lets us choose the status of a response
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
// Annotations that connect URLs and request bodies to this class's methods
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// @RestController: this class handles web requests, and whatever its methods return is sent
// back as JSON (not as a web page)
@RestController
// Every URL in this class starts with /api/auth (SecurityConfig leaves this path open: no token needed)
@RequestMapping("/api/auth")
public class AuthController {

    // The class that does the real work; the controller only handles the web side
    private final AuthService authService;

    // Spring passes in the AuthService when it creates this controller
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // 201 created | 400 invalid input | 409 email already registered
    // Handles POST /api/auth/register
    @PostMapping("/register")
    // @RequestBody: turn the JSON the client sent into a RegisterRequest object
    // @Valid: check its rules first; if they fail, the method never runs and the error handler returns 400
    public ResponseEntity<UserProfileResponse> register(@Valid @RequestBody RegisterRequest request) {
        // Call the service, then send back its result with status 201 Created
        // (a duplicate email throws inside the service and becomes a 409 in GlobalExceptionHandler)
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    // 200 with token | 401 on any credential failure
    // Handles POST /api/auth/login
    @PostMapping("/login")
    // Same pattern: JSON in, validated first (blank fields give 400)
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        // Success: send the token with status 200 OK
        // (any credential failure throws in the service and becomes a 401 in GlobalExceptionHandler)
        return ResponseEntity.ok(authService.login(request));
    }
}