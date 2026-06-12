package com.evcharge.dto;

import com.evcharge.domain.Role;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public final class AuthDto {
    private AuthDto() {}

    public record RegisterRequest(
            @NotBlank String username,
            @NotBlank String password,
            String vehicleId,
            String phone,
            String role
    ) {}

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {}

    public record AuthResponse(
            String token,
            String userId,
            String username,
            String vehicleId,
            Role role,
            LocalDateTime expiresAt
    ) {}

    public record WhoamiResponse(
            String userId,
            String username,
            String vehicleId,
            Role role
    ) {}

    public record AuthPrincipal(
            String userId,
            String username,
            String vehicleId,
            Role role
    ) {}
}
