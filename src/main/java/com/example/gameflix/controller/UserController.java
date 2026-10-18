package com.example.gameflix.controller;

import com.example.gameflix.dto.UserProfileResponse;
// The class that does the real work
import com.example.gameflix.service.AuthService;
import org.springframework.http.ResponseEntity;
// Holds who is making the current request (filled in by Spring Security after the token is verified)
import org.springframework.security.core.Authentication;
// Annotations that connect URLs to this class's methods
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// @RestController: this class handles web requests, and its return values are sent back as JSON
@RestController
// Every URL in this class starts with /api/users (SecurityConfig requires a valid token for this path)
@RequestMapping("/api/users")
public class UserController {

    // The class that does the real work (looks the user up in the database)
    private final AuthService authService;

    // Spring passes in the AuthService when it creates this controller
    public UserController(AuthService authService) {
        this.authService = authService;
    }

    // Protected. 200 with profile | 401 missing, invalid, or expired token
    // Handles GET /api/users/me
    @GetMapping("/me")
    // Spring fills in "authentication" itself: it describes the logged-in user taken from the token
    public ResponseEntity<UserProfileResponse> me(Authentication authentication) {
        // This method only runs if the token was already verified (signature and expiry);
        // otherwise Spring Security answered 401 before reaching here.
        // getName() returns the token's "sub" claim, which is the user id; use it to load the profile
        // and send it back with status 200 OK
        return ResponseEntity.ok(authService.getProfile(authentication.getName()));
    }
}