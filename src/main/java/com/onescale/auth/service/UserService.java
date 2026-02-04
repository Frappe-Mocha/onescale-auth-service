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

    public UserDto getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("User not found"));
        return mapUserToDto(user);
    }

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

    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("User not found"));

        user.setIsActive(false);
        userRepository.save(user);

        refreshTokenRepository.revokeAllUserTokens(user, LocalDateTime.now());
        log.info("User account deleted (soft) for user ID: {}", userId);
    }

    private UserDto mapUserToDto(User user) {
        return UserDto.builder()
                .userId(user.getId())
                .clientId(user.getClientId())
                .deviceId(user.getDeviceId())
                .provider(user.getProvider())
                .email(user.getEmail())
                .mobileNumber(user.getMobileNumber())
                .fullName(user.getFullName())
                .profilePictureUrl(user.getProfilePictureUrl())
                .isEmailVerified(user.getIsEmailVerified())
                .isMobileVerified(user.getIsMobileVerified())
                .build();
    }
}
