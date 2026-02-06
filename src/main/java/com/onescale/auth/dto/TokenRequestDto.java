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
 * @deprecated Use POST /api/v1/auth/login instead which validates credentials directly.
 * This DTO kept for backward compatibility only.
 *
 * Validates (clientId, deviceId) pair before issuing JWT tokens.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Deprecated
public class TokenRequestDto {

    @NotBlank(message = "Client ID is required")
    private String clientId;

    @NotBlank(message = "Device ID is required")
    private String deviceId;
}
