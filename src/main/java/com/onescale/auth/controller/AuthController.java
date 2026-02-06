package com.onescale.auth.controller;

import com.onescale.auth.dto.*;
import com.onescale.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Standard authentication service endpoints.
 *
 * Supports two auth flows:
 *   1. PASSWORD provider: Backend validates credentials, issues JWT
 *   2. OAuth providers: Frontend authenticates, backend registers/identifies user
 *
 * Endpoints:
 *   - POST /register: Create new user account
 *   - POST /login: Authenticate and receive JWT tokens
 *   - POST /refresh: Exchange refresh token for new access token
 *   - POST /logout: Revoke refresh token
 *   - GET /validate: Validate token from Authorization header (for API gateway)
 *   - POST /validate: Validate token from request body (legacy)
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // ---------------------------------------------------------------
    // REGISTER  — first time a user hits the backend
    // ---------------------------------------------------------------

    /**
     * POST /api/v1/auth/register
     *
     * For PASSWORD provider: Email/mobile + password required
     * For OAuth providers: Frontend authenticates, sends profile data only
     *
     * Response includes the backend-generated client_id (UUID).
     * For PASSWORD users: Call /login with credentials to get JWT.
     * For OAuth users: Tokens may be issued separately based on OAuth flow.
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponseDto> register(@Valid @RequestBody RegisterUserDto request) {
        UserDto user = authService.registerUser(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponseDto.success("User registered successfully", user));
    }

    // ---------------------------------------------------------------
    // LOGIN  — returning user
    // ---------------------------------------------------------------

    /**
     * POST /api/v1/auth/login
     *
     * Authenticate user and issue JWT tokens.
     *
     * For PASSWORD provider: Validates email/mobile + password
     * Returns: JWT access token + refresh token on success
     *
     * This is the standard authentication endpoint - credentials in, tokens out.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponseDto> login(@Valid @RequestBody LoginUserDto request) {
        AuthResponseDto authResponse = authService.loginUser(request);
        return ResponseEntity.ok(ApiResponseDto.success("Login successful", authResponse));
    }

    // ---------------------------------------------------------------
    // TOKEN  — DEPRECATED - use /login instead
    // ---------------------------------------------------------------

    /**
     * POST /api/v1/auth/token
     *
     * @deprecated Use /login endpoint instead which validates credentials and returns JWT directly.
     * This endpoint kept for backward compatibility only.
     *
     * Validates (clientId, deviceId) and issues JWT tokens.
     */
    @Deprecated
    @PostMapping("/token")
    public ResponseEntity<ApiResponseDto> generateToken(@Valid @RequestBody TokenRequestDto request) {
        AuthResponseDto authResponse = authService.generateToken(request);
        return ResponseEntity.ok(ApiResponseDto.success("Token generated successfully", authResponse));
    }

    // ---------------------------------------------------------------
    // REFRESH
    // ---------------------------------------------------------------

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponseDto> refreshToken(@Valid @RequestBody RefreshTokenDto request) {
        AuthResponseDto authResponse = authService.refreshAccessToken(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponseDto.success("Token refreshed successfully", authResponse));
    }

    // ---------------------------------------------------------------
    // LOGOUT
    // ---------------------------------------------------------------

    @PostMapping("/logout")
    public ResponseEntity<ApiResponseDto> logout(@Valid @RequestBody RefreshTokenDto request) {
        authService.revokeRefreshToken(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponseDto.success("Logged out successfully"));
    }

    // ---------------------------------------------------------------
    // VALIDATE  (inter-service token check)
    // ---------------------------------------------------------------

    /**
     * GET /api/v1/auth/validate
     *
     * Validate token from Authorization header - standard for API gateways.
     * Extracts token from "Authorization: Bearer <token>" header.
     *
     * Returns client_id, email, mobile, expiration if valid.
     * Returns 401 if token is invalid or expired.
     */
    @GetMapping("/validate")
    public ResponseEntity<ApiResponseDto> validateTokenFromHeader(@RequestHeader("Authorization") String authHeader) {
        String token = extractTokenFromHeader(authHeader);
        TokenValidationDto result = authService.validateAccessToken(token);
        return ResponseEntity.ok(ApiResponseDto.success("Token is valid", result));
    }

    /**
     * POST /api/v1/auth/validate
     *
     * Validate token from request body - legacy endpoint.
     * Prefer GET /validate with Authorization header for new integrations.
     */
    @PostMapping("/validate")
    public ResponseEntity<ApiResponseDto> validateAccessToken(@Valid @RequestBody AccessTokenDto request) {
        TokenValidationDto result = authService.validateAccessToken(request.getAccessToken());
        return ResponseEntity.ok(ApiResponseDto.success("Token is valid", result));
    }

    private String extractTokenFromHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new com.onescale.auth.exception.AuthException("Missing or invalid Authorization header");
    }

    // ---------------------------------------------------------------
    // HEALTH
    // ---------------------------------------------------------------

    @GetMapping("/health")
    public ResponseEntity<ApiResponseDto> health() {
        return ResponseEntity.ok(ApiResponseDto.success("Auth service is running"));
    }
}
