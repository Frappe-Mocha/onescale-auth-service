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
 * The frontend has already authenticated the user (Firebase password flow,
 * Google sign-in, etc.).  It sends the same contact details that were used
 * at registration so the backend can locate the existing user row.
 *
 * At least one of email / mobileNumber must be present.
 * device_id is mandatory — it is persisted / updated on every login.
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
}
