package com.nova.bank.auth;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
/** Define the public authentication contract. */
public final class AuthDtos {
    private AuthDtos() {}
    public record LoginRequest(@NotBlank @Size(max=64) String username, @NotBlank @Size(max=72) String password) {}
    public record UserView(UUID id, String username, String fullName, String role, Instant lastLogin) {}
    public record LoginResponse(String token, String tokenType, long expiresIn, UserView user) {}
    public record Message(String message) {}
}
