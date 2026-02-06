package com.onescale.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /api/v1/auth/login
 *
 * Authenticates user and returns JWT tokens directly.
 *
 * For PASSWORD provider users: email/mobile + password required, backend validates
 * For OAuth users: This endpoint may not be used (OAuth handled on frontend)
 *
 * Returns: JWT access token + refresh token on successful authentication
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginUserDto {

    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    @Size(max = 20, message = "Mobile number must not exceed 20 characters")
    private String mobileNumber;

    @NotBlank(message = "Device ID is required")
    @Size(max = 255, message = "Device ID must not exceed 255 characters")
    private String deviceId;

    // Password - REQUIRED for PASSWORD provider users
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    private String password;
}
