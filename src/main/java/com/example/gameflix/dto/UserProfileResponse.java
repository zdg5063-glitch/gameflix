package com.example.gameflix.dto;

import java.time.Instant;

// Never add the password or the hash to this class.
public record UserProfileResponse(long id, String email, String displayName, Instant createdAt) {
}
