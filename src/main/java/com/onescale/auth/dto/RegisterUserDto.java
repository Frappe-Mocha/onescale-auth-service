package com.onescale.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /api/v1/auth/register
 *
 * Two registration flows supported:
 * 1. PASSWORD provider: User provides email/mobile + password, backend validates and stores
 * 2. OAuth providers (GOOGLE, FACEBOOK): Frontend authenticates, sends profile data only
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterUserDto {

    @NotBlank(message = "Full name is required")
    @Size(max = 255, message = "Full name must not exceed 255 characters")
    private String fullName;

    // At least one of email / mobileNumber must be present (validated in service layer).
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    @Size(max = 20, message = "Mobile number must not exceed 20 characters")
    private String mobileNumber;

    @NotBlank(message = "Device ID is required")
    @Size(max = 255, message = "Device ID must not exceed 255 characters")
    private String deviceId;

    // Auth source: PASSWORD (backend-validated) | GOOGLE | FACEBOOK | EMAIL | MOBILE (OAuth)
    @NotBlank(message = "Provider is required")
    @Size(max = 20, message = "Provider must not exceed 20 characters")
    private String provider;

    // Password - REQUIRED for PASSWORD provider, must be null for OAuth providers
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    private String password;

    // Optional — set by frontend if available from the OAuth profile
    @Size(max = 500, message = "Profile picture URL must not exceed 500 characters")
    private String profilePictureUrl;
}
