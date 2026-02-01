package com.onescale.auth.service;

import com.google.firebase.auth.FirebaseToken;
import com.onescale.auth.dto.*;
import com.onescale.auth.entity.RefreshToken;
import com.onescale.auth.entity.User;
import com.onescale.auth.exception.AuthException;
import com.onescale.auth.exception.InvalidTokenException;
import com.onescale.auth.repository.RefreshTokenRepository;
import com.onescale.auth.repository.UserRepository;
import com.onescale.auth.util.FirebaseTokenUtil;
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
public class FirebaseAuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final FirebaseTokenUtil firebaseTokenUtil;
    private final JwtUtil jwtUtil;

    /**
     * Register or sign in user with Firebase ID token
     * This handles both email/password and phone authentication from Firebase
     */
    @Transactional
    public AuthResponseDto authenticateWithFirebase(String firebaseIdToken) {
        // Verify Firebase token
        FirebaseToken token = firebaseTokenUtil.verifyIdToken(firebaseIdToken);

        String firebaseUid = token.getUid();
        String email = firebaseTokenUtil.getEmail(token);
        boolean emailVerified = firebaseTokenUtil.isEmailVerified(token);
        String phoneNumber = firebaseTokenUtil.getPhoneNumber(token);
        String displayName = firebaseTokenUtil.getDisplayName(token);
        String profilePicture = firebaseTokenUtil.getProfilePicture(token);

        // Check if user already exists
        User user = userRepository.findByFirebaseUid(firebaseUid).orElseGet(() -> {
            // Create new user
            log.info("Creating new user with Firebase UID: {}", firebaseUid);

            User newUser = User.builder()
                    .firebaseUid(firebaseUid)
                    .email(email)
                    .mobileNumber(phoneNumber)
                    .fullName(displayName)
                    .profilePictureUrl(profilePicture)
                    .isEmailVerified(emailVerified)
                    .isMobileVerified(phoneNumber != null)
                    .isActive(true)
                    .build();

            return userRepository.save(newUser);
        });

        // Update existing user data from Firebase token
        boolean updated = false;
        if (email != null && !email.equals(user.getEmail())) {
            user.setEmail(email);
            updated = true;
        }
        if (emailVerified && !user.getIsEmailVerified()) {
            user.setIsEmailVerified(true);
            updated = true;
        }
        if (phoneNumber != null && !phoneNumber.equals(user.getMobileNumber())) {
            user.setMobileNumber(phoneNumber);
            user.setIsMobileVerified(true);
            updated = true;
        }
        if (displayName != null && !displayName.equals(user.getFullName())) {
            user.setFullName(displayName);
            updated = true;
        }
        if (profilePicture != null && !profilePicture.equals(user.getProfilePictureUrl())) {
            user.setProfilePictureUrl(profilePicture);
            updated = true;
        }

        // Update last login
        user.setLastLoginAt(LocalDateTime.now());
        updated = true;

        if (updated) {
            userRepository.save(user);
        }

        log.info("User authenticated with Firebase UID: {}, Email: {}, Phone: {}", firebaseUid, email, phoneNumber);

        return generateAuthResponse(user);
    }

    /**
     * Refresh access token using refresh token
     */
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

    /**
     * Revoke refresh token (logout)
     */
    @Transactional
    public void revokeRefreshToken(String refreshTokenString) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenString)
                .orElseThrow(() -> new InvalidTokenException("Refresh token not found"));

        refreshToken.setIsRevoked(true);
        refreshToken.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(refreshToken);

        log.info("Refresh token revoked for user: {}", refreshToken.getUser().getId());
    }

    /**
     * Revoke all user tokens
     */
    @Transactional
    public void revokeAllUserTokens(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("User not found"));

        refreshTokenRepository.revokeAllUserTokens(user, LocalDateTime.now());
        log.info("All refresh tokens revoked for user: {}", userId);
    }

    /**
     * Validate custom JWT access token and return user info
     * This is for other microservices to validate tokens
     */
    public TokenValidationDto validateAccessToken(String accessToken) {
        // Validate token signature and expiration
        io.jsonwebtoken.Claims claims = jwtUtil.validateToken(accessToken);

        // Verify it's an access token
        if (!"access".equals(claims.get("tokenType"))) {
            throw new InvalidTokenException("Token is not an access token");
        }

        Long userId = Long.parseLong(claims.getSubject());

        // Verify user exists and is active
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("User not found"));

        if (!user.getIsActive()) {
            throw new InvalidTokenException("User account is not active");
        }

        return TokenValidationDto.builder()
                .isValid(true)
                .userId(userId)
                .email((String) claims.get("email"))
                .mobileNumber((String) claims.get("mobileNumber"))
                .tokenType("custom_jwt")
                .issuedAt(claims.getIssuedAt().getTime() / 1000)
                .expiresAt(claims.getExpiration().getTime() / 1000)
                .build();
    }

    /**
     * Get user profile by Firebase UID
     */
    public UserDto getUserProfile(String firebaseUid) {
        User user = userRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> new AuthException("User not found"));

        return mapUserToDto(user);
    }

    /**
     * Generate JWT tokens for authenticated user
     */
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

    /**
     * Map User entity to UserDto
     */
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
