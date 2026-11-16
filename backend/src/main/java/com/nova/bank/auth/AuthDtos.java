package com.nova.bank.auth;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nova.bank.common.StrictJsonTypes;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
/** Define the public authentication contract. */
public final class AuthDtos {
    private AuthDtos() {}
    public record LoginRequest(@JsonDeserialize(using=StrictJsonTypes.Text.class) @NotBlank @Size(max=64) String username, @JsonDeserialize(using=StrictJsonTypes.Text.class) @NotBlank @Size(max=72) String password) {}
    public record UserView(UUID id, String username, String fullName, String role, Instant lastLogin) {}
    public record LoginResponse(String token, String tokenType, long expiresIn, UserView user) {}
    public record Message(String message) {}
}
