package com.intervyox.dto;

import com.intervyox.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class AuthDtos {

    public record RegisterRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Invalid email format")
            String email,

            @NotBlank(message = "Password is required")
            @Size(min = 6, message = "Password must be at least 6 characters")
            String password,

            String fullName,
            String targetRole) {}

    public record LoginRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Invalid email format")
            String email,

            @NotBlank(message = "Password is required")
            String password) {}

    public record AuthResponse(
            String token,
            UserResponse user) {}

    public record UserResponse(
            Long id,
            String email,
            String fullName,
            Role role,
            String targetRole,
            LocalDateTime createdAt) {}

    public record UpdateProfileRequest(
            String fullName,
            String targetRole) {}
}
