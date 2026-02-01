package com.onescale.auth.controller;

import com.onescale.auth.dto.*;
import com.onescale.auth.service.FirebaseAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class FirebaseAuthController {

    private final FirebaseAuthService firebaseAuthService;

    /**
     * Authenticate user with Firebase ID token (Email/Password or Phone)
     * Android app sends Firebase ID token after successful authentication
     */
    @PostMapping("/firebase/authenticate")
    public ResponseEntity<ApiResponseDto> authenticateWithFirebase(@Valid @RequestBody FirebaseAuthDto request) {
        AuthResponseDto authResponse = firebaseAuthService.authenticateWithFirebase(request.getIdToken());
        return ResponseEntity.ok(ApiResponseDto.success("Authentication successful", authResponse));
    }

    /**
     * Refresh access token using refresh token
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponseDto> refreshToken(@Valid @RequestBody RefreshTokenDto request) {
        AuthResponseDto authResponse = firebaseAuthService.refreshAccessToken(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponseDto.success("Token refreshed successfully", authResponse));
    }

    /**
     * Logout - revoke refresh token
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponseDto> logout(@Valid @RequestBody RefreshTokenDto request) {
        firebaseAuthService.revokeRefreshToken(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponseDto.success("Logged out successfully"));
    }

    /**
     * Validate Firebase token (for other microservices)
     */
    @PostMapping("/firebase/validate")
    public ResponseEntity<ApiResponseDto> validateFirebaseToken(@Valid @RequestBody FirebaseAuthDto request) {
        TokenValidationDto validationResult = firebaseAuthService.validateFirebaseToken(request.getIdToken());
        return ResponseEntity.ok(ApiResponseDto.success("Token is valid", validationResult));
    }

    /**
     * Health check
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponseDto> health() {
        return ResponseEntity.ok(ApiResponseDto.success("Auth service is running"));
    }
}
