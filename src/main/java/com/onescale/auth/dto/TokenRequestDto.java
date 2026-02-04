package com.onescale.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /api/v1/auth/token
 *
 * After a user is registered (or logged in) and the frontend holds the
 * user_id and client_id returned by the backend, it calls this endpoint
 * to obtain a signed JWT access + refresh token pair.
 *
 * device_id is included so the backend can verify that the requesting
 * device matches the one on file for this user.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenRequestDto {

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotBlank(message = "Device ID is required")
    private String deviceId;

    @NotBlank(message = "Client ID is required")
    private String clientId;
}
