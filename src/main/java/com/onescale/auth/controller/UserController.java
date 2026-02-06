package com.onescale.auth.controller;

import com.onescale.auth.dto.ApiResponseDto;
import com.onescale.auth.dto.UserDto;
import com.onescale.auth.dto.UserUpdateDto;
import com.onescale.auth.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Get current user profile
     * Requires JWT authentication
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponseDto> getCurrentUser(Authentication authentication) {
        String clientId = (String) authentication.getPrincipal();
        UserDto user = userService.getUserByClientId(clientId);
        return ResponseEntity.ok(ApiResponseDto.success("User profile retrieved successfully", user));
    }

    /**
     * Update current user profile
     * Requires JWT authentication
     */
    @PutMapping("/me")
    public ResponseEntity<ApiResponseDto> updateCurrentUser(
            Authentication authentication,
            @Valid @RequestBody UserUpdateDto updateDto) {
        String clientId = (String) authentication.getPrincipal();
        UserDto updatedUser = userService.updateUser(clientId, updateDto);
        return ResponseEntity.ok(ApiResponseDto.success("User profile updated successfully", updatedUser));
    }

    /**
     * Delete current user account (soft delete)
     * Requires JWT authentication
     */
    @DeleteMapping("/me")
    public ResponseEntity<ApiResponseDto> deleteCurrentUser(Authentication authentication) {
        String clientId = (String) authentication.getPrincipal();
        userService.deleteUser(clientId);
        return ResponseEntity.ok(ApiResponseDto.success("User account deleted successfully"));
    }
}
