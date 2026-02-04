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
 * The frontend authenticates the user (Firebase / Google / Facebook / email+OTP / mobile+OTP)
 * and then extracts these fields itself before hitting this endpoint.
 * The backend does NOT validate the identity — it trusts the frontend.
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

    // Auth source: GOOGLE | FACEBOOK | EMAIL | MOBILE
    @NotBlank(message = "Provider is required")
    @Size(max = 20, message = "Provider must not exceed 20 characters")
    private String provider;

    // Optional — set by frontend if available from the OAuth profile
    @Size(max = 500, message = "Profile picture URL must not exceed 500 characters")
    private String profilePictureUrl;
}
