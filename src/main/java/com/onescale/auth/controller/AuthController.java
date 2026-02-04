package com.onescale.auth.controller;

import com.onescale.auth.dto.*;
import com.onescale.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication endpoints.
 *
 * All sign-up / sign-in validation happens on the frontend (Firebase / Google /
 * Facebook).  The backend never contacts an OAuth provider.  It only:
 *   - Stores user data and hands back a user_id + client_id   (register)
 *   - Looks up an existing user by email / mobile              (login)
 *   - Issues a signed JWT pair given a valid (user_id, client_id, device_id)  (token)
 *   - Refreshes / revokes tokens and validates them for other microservices
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
     * Frontend has already validated the user (email+OTP, mobile+OTP, Google, Facebook …).
     * It extracts fullName, email, mobileNumber, deviceId and provider, then POSTs here.
     *
     * Response includes the backend-generated user_id and client_id (UUID).
     * The frontend should immediately call /token with those values.
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
     * Frontend has re-authenticated the user.  Sends email or mobileNumber
     * (whichever was used at sign-up) plus the current deviceId.
     *
     * Response contains the full user record (including client_id) so
     * the frontend can call /token without extra lookups.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponseDto> login(@Valid @RequestBody LoginUserDto request) {
        UserDto user = authService.loginUser(request);
        return ResponseEntity.ok(ApiResponseDto.success("Login successful", user));
    }

    // ---------------------------------------------------------------
    // TOKEN  — issue JWT pair
    // ---------------------------------------------------------------

    /**
     * POST /api/v1/auth/token
     *
     * The frontend supplies (userId, clientId, deviceId).  The backend
     * verifies all three against the stored row before signing the JWTs.
     *
     * JWT access-token claims: userId, email, mobileNumber, fullName, clientId, tokenType
     */
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

    @PostMapping("/validate")
    public ResponseEntity<ApiResponseDto> validateAccessToken(@Valid @RequestBody AccessTokenDto request) {
        TokenValidationDto result = authService.validateAccessToken(request.getAccessToken());
        return ResponseEntity.ok(ApiResponseDto.success("Token is valid", result));
    }

    // ---------------------------------------------------------------
    // HEALTH
    // ---------------------------------------------------------------

    @GetMapping("/health")
    public ResponseEntity<ApiResponseDto> health() {
        return ResponseEntity.ok(ApiResponseDto.success("Auth service is running"));
    }
}
