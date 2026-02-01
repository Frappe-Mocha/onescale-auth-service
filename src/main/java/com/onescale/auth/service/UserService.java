package com.onescale.auth.service;

import com.onescale.auth.dto.UserDto;
import com.onescale.auth.dto.UserUpdateDto;
import com.onescale.auth.entity.User;
import com.onescale.auth.exception.AuthException;
import com.onescale.auth.repository.RefreshTokenRepository;
import com.onescale.auth.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * Get user by ID
     */
    public UserDto getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("User not found"));

        return mapUserToDto(user);
    }

    /**
     * Update user profile
     */
    @Transactional
    public UserDto updateUser(Long userId, UserUpdateDto updateDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("User not found"));

        if (updateDto.getFullName() != null) {
            user.setFullName(updateDto.getFullName());
        }

        if (updateDto.getProfilePictureUrl() != null) {
            user.setProfilePictureUrl(updateDto.getProfilePictureUrl());
        }

        User updatedUser = userRepository.save(user);
        log.info("User profile updated for user ID: {}", userId);

        return mapUserToDto(updatedUser);
    }

    /**
     * Delete user account (soft delete)
     */
    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("User not found"));

        // Soft delete: mark as inactive
        user.setIsActive(false);
        userRepository.save(user);

        // Revoke all refresh tokens
        refreshTokenRepository.revokeAllUserTokens(user, LocalDateTime.now());

        log.info("User account deleted (soft) for user ID: {}", userId);
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
