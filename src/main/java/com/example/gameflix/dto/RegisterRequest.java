package com.example.gameflix.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255)
        String email,

        // Max 64 because bcrypt truncates input at 72 bytes.
        // TODO: decide on your minimum length and mention it in the README.
        @NotBlank @Size(min = 8, max = 64)
        String password,

        @NotBlank @Size(max = 50)
        String displayName
) {
}
