package com.onescale.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.onescale.auth.dto.*;
import com.onescale.auth.entity.RefreshToken;
import com.onescale.auth.entity.User;
import com.onescale.auth.exception.AuthException;
import com.onescale.auth.exception.InvalidTokenException;
import com.onescale.auth.exception.OtpException;
import com.onescale.auth.repository.RefreshTokenRepository;
import com.onescale.auth.repository.UserRepository;
import com.onescale.auth.util.JwtUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OtpService otpService;
    private final GoogleOAuthService googleOAuthService;
    private final JwtUtil jwtUtil;

    @Transactional
    public void sendEmailOtp(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new OtpException("Email is required");
        }

        otpService.sendEmailOtp(email);
        log.info("OTP sent to email: {}", email);
    }

    @Transactional
    public void sendMobileOtp(String mobileNumber) {
        if (mobileNumber == null || mobileNumber.trim().isEmpty()) {
            throw new OtpException("Mobile number is required");
        }

        otpService.sendMobileOtp(mobileNumber);
        log.info("OTP sent to mobile: {}", mobileNumber);
    }

    @Transactional
    public AuthResponseDto verifyEmailOtp(String email, String otpCode) {
        if (email == null || email.trim().isEmpty()) {
            throw new OtpException("Email is required");
        }

        if (otpCode == null || otpCode.trim().isEmpty()) {
            throw new OtpException("OTP code is required");
        }

        boolean isValid = otpService.verifyEmailOtp(email, otpCode);

        if (!isValid) {
            throw new OtpException("Invalid or expired OTP code");
        }

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User newUser = User.builder()
                    .email(email)
                    .isEmailVerified(true)
                    .isActive(true)
                    .build();
            return userRepository.save(newUser);
        });

        if (!user.getIsEmailVerified()) {
            user.setIsEmailVerified(true);
            userRepository.save(user);
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        return generateAuthResponse(user);
    }

    @Transactional
    public AuthResponseDto verifyMobileOtp(String mobileNumber, String otpCode) {
        if (mobileNumber == null || mobileNumber.trim().isEmpty()) {
            throw new OtpException("Mobile number is required");
        }

        if (otpCode == null || otpCode.trim().isEmpty()) {
            throw new OtpException("OTP code is required");
        }

        boolean isValid = otpService.verifyMobileOtp(mobileNumber, otpCode);

        if (!isValid) {
            throw new OtpException("Invalid or expired OTP code");
        }

        User user = userRepository.findByMobileNumber(mobileNumber).orElseGet(() -> {
            User newUser = User.builder()
                    .mobileNumber(mobileNumber)
                    .isMobileVerified(true)
                    .isActive(true)
                    .build();
            return userRepository.save(newUser);
        });

        if (!user.getIsMobileVerified()) {
            user.setIsMobileVerified(true);
            userRepository.save(user);
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        return generateAuthResponse(user);
    }

    @Transactional
    public AuthResponseDto authenticateWithGoogle(String idTokenString) {
        GoogleIdToken.Payload payload = googleOAuthService.verifyIdToken(idTokenString);

        String googleId = googleOAuthService.getGoogleId(payload);
        String email = googleOAuthService.getEmail(payload);
        String fullName = googleOAuthService.getFullName(payload);
        String profilePictureUrl = googleOAuthService.getProfilePictureUrl(payload);
        Boolean isEmailVerified = googleOAuthService.isEmailVerified(payload);

        User user = userRepository.findByGoogleId(googleId).orElseGet(() -> {
            User existingUser = userRepository.findByEmail(email).orElse(null);

            if (existingUser != null) {
                existingUser.setGoogleId(googleId);
                existingUser.setFullName(fullName);
                existingUser.setProfilePictureUrl(profilePictureUrl);
                existingUser.setIsEmailVerified(isEmailVerified);
                return userRepository.save(existingUser);
            } else {
                User newUser = User.builder()
                        .googleId(googleId)
                        .email(email)
                        .fullName(fullName)
                        .profilePictureUrl(profilePictureUrl)
                        .isEmailVerified(isEmailVerified)
                        .isActive(true)
                        .build();
                return userRepository.save(newUser);
            }
        });

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("User authenticated with Google: {}", email);
        return generateAuthResponse(user);
    }

    @Transactional
    public AuthResponseDto refreshAccessToken(String refreshTokenString) {
        if (!jwtUtil.isRefreshToken(refreshTokenString)) {
            throw new InvalidTokenException("Invalid refresh token type");
        }

        Long userId = jwtUtil.getUserIdFromToken(refreshTokenString);

        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenString)
                .orElseThrow(() -> new InvalidTokenException("Refresh token not found"));

        if (!refreshToken.isValid()) {
            throw new InvalidTokenException("Refresh token is expired or revoked");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("User not found"));

        String newAccessToken = jwtUtil.generateAccessToken(user);

        log.info("Access token refreshed for user: {}", userId);

        return AuthResponseDto.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshTokenString)
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getAccessTokenExpirationInSeconds())
                .user(mapUserToDto(user))
                .build();
    }

    @Transactional
    public void revokeRefreshToken(String refreshTokenString) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenString)
                .orElseThrow(() -> new InvalidTokenException("Refresh token not found"));

        refreshToken.setIsRevoked(true);
        refreshToken.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(refreshToken);

        log.info("Refresh token revoked for user: {}", refreshToken.getUser().getId());
    }

    @Transactional
    public void revokeAllUserTokens(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("User not found"));

        refreshTokenRepository.revokeAllUserTokens(user, LocalDateTime.now());
        log.info("All refresh tokens revoked for user: {}", userId);
    }

    public TokenValidationDto validateAccessToken(String accessToken) {
        try {
            // Validate token signature and expiration
            io.jsonwebtoken.Claims claims = jwtUtil.validateToken(accessToken);

            // Verify it's an access token
            if (!"access".equals(claims.get("token_type"))) {
                throw new InvalidTokenException("Token is not an access token");
            }

            Long userId = Long.parseLong(claims.getSubject());

            // Verify user exists and is active
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new InvalidTokenException("User not found"));

            if (!user.getIsActive()) {
                throw new InvalidTokenException("User account is not active");
            }

            // Return validation result with user info
            return TokenValidationDto.builder()
                    .isValid(true)
                    .userId(userId)
                    .email((String) claims.get("email"))
                    .mobileNumber((String) claims.get("mobile_number"))
                    .tokenType((String) claims.get("token_type"))
                    .issuedAt(claims.getIssuedAt().getTime() / 1000)
                    .expiresAt(claims.getExpiration().getTime() / 1000)
                    .build();

        } catch (InvalidTokenException e) {
            log.error("Token validation failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during token validation: {}", e.getMessage());
            throw new InvalidTokenException("Token validation failed: " + e.getMessage());
        }
    }

    private AuthResponseDto generateAuthResponse(User user) {
        String accessToken = jwtUtil.generateAccessToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user);

        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .token(refreshToken)
                .user(user)
                .expiresAt(jwtUtil.getRefreshTokenExpirationDate()
                        .toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime())
                .isRevoked(false)
                .build();

        refreshTokenRepository.save(refreshTokenEntity);

        return AuthResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getAccessTokenExpirationInSeconds())
                .user(mapUserToDto(user))
                .build();
    }

    private UserDto mapUserToDto(User user) {
        return UserDto.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .mobileNumber(user.getMobileNumber())
                .fullName(user.getFullName())
                .profilePictureUrl(user.getProfilePictureUrl())
                .isEmailVerified(user.getIsEmailVerified())
                .isMobileVerified(user.getIsMobileVerified())
                .build();
    }
}
