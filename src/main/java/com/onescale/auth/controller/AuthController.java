package com.onescale.auth.controller;

import com.onescale.auth.dto.*;
import com.onescale.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/otp/email/send")
    public ResponseEntity<ApiResponseDto> sendEmailOtp(@Valid @RequestBody OtpRequestDto request) {
        authService.sendEmailOtp(request.getEmail());
        return ResponseEntity.ok(ApiResponseDto.success("OTP sent to email successfully"));
    }

    @PostMapping("/otp/mobile/send")
    public ResponseEntity<ApiResponseDto> sendMobileOtp(@Valid @RequestBody OtpRequestDto request) {
        authService.sendMobileOtp(request.getMobileNumber());
        return ResponseEntity.ok(ApiResponseDto.success("OTP sent to mobile number successfully"));
    }

    @PostMapping("/otp/email/verify")
    public ResponseEntity<ApiResponseDto> verifyEmailOtp(@Valid @RequestBody OtpVerifyDto request) {
        AuthResponseDto authResponse = authService.verifyEmailOtp(request.getEmail(), request.getOtpCode());
        return ResponseEntity.ok(ApiResponseDto.success("Email verified successfully", authResponse));
    }

    @PostMapping("/otp/mobile/verify")
    public ResponseEntity<ApiResponseDto> verifyMobileOtp(@Valid @RequestBody OtpVerifyDto request) {
        AuthResponseDto authResponse = authService.verifyMobileOtp(request.getMobileNumber(), request.getOtpCode());
        return ResponseEntity.ok(ApiResponseDto.success("Mobile number verified successfully", authResponse));
    }

    @PostMapping("/google")
    public ResponseEntity<ApiResponseDto> authenticateWithGoogle(@Valid @RequestBody GoogleAuthDto request) {
        AuthResponseDto authResponse = authService.authenticateWithGoogle(request.getIdToken());
        return ResponseEntity.ok(ApiResponseDto.success("Google authentication successful", authResponse));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponseDto> refreshToken(@Valid @RequestBody RefreshTokenDto request) {
        AuthResponseDto authResponse = authService.refreshAccessToken(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponseDto.success("Token refreshed successfully", authResponse));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponseDto> logout(@Valid @RequestBody RefreshTokenDto request) {
        authService.revokeRefreshToken(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponseDto.success("Logged out successfully"));
    }

    @PostMapping("/validate")
    public ResponseEntity<ApiResponseDto> validateToken(@Valid @RequestBody ValidateTokenRequestDto request) {
        TokenValidationDto validationResult = authService.validateAccessToken(request.getAccessToken());
        return ResponseEntity.ok(ApiResponseDto.success("Token is valid", validationResult));
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponseDto> health() {
        return ResponseEntity.ok(ApiResponseDto.success("Auth service is running"));
    }
}
