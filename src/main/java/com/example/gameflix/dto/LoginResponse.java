package com.example.gameflix.dto;

public record LoginResponse(String token, String tokenType, long expiresInSeconds) {
}
