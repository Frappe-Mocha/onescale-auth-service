package com.onescale.auth.exception;

import com.onescale.auth.dto.ApiResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Global Exception Handler for consistent error responses
 *
 * Handles all exceptions thrown by controllers and returns standardized error responses.
 * All responses use ApiResponseDto format for consistency.
 *
 * Token Expiration Error Handling:
 * - InvalidTokenException (401): Token expired, invalid signature, or malformed
 * - AuthException (401): General authentication failures (user not found, inactive account)
 *
 * Client should handle 401 errors with this flow:
 * 1. Check if error is "Token has expired"
 * 2. Try to refresh access token using refresh token (POST /api/v1/auth/refresh)
 * 3. If refresh succeeds: Retry original request with new access token
 * 4. If refresh fails with 401: Refresh token also expired, re-authenticate on the frontend
 * 5. If frontend session expired: Redirect to login screen
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handle validation errors from @Valid annotations
     *
     * Returns 400 Bad Request with field-specific error messages
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponseDto> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.warn("Validation failed: {}", errors);

        return ResponseEntity
                .badRequest()
                .body(ApiResponseDto.builder()
                        .success(false)
                        .message("Validation failed")
                        .data(errors)
                        .build());
    }

    /**
     * Handle invalid token errors
     *
     * Scenarios:
     * - Token expired (access token > 1 hour old, refresh token > 7 days old)
     * - Invalid signature (token was tampered with or wrong secret key)
     * - Malformed token (not a valid JWT format)
     * - Token type mismatch (using refresh token for protected endpoints)
     *
     * Returns 401 Unauthorized - Client should try to refresh or re-authenticate
     */
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiResponseDto> handleInvalidTokenException(InvalidTokenException ex) {
        log.error("Invalid token: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponseDto.error(ex.getMessage()));
    }

    /**
     * Handle general authentication errors
     *
     * Scenarios:
     * - User not found in database
     * - User account inactive (soft deleted)
     * - User account suspended/banned
     * - Refresh token revoked (user logged out)
     * - Duplicate email or mobile on registration
     *
     * Returns 401 Unauthorized - Client should re-authenticate
     */
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiResponseDto> handleAuthException(AuthException ex) {
        log.error("Authentication exception: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponseDto.error(ex.getMessage()));
    }

    /**
     * Handle rate limiting errors
     *
     * Returns 429 Too Many Requests when client exceeds rate limit
     */
    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ApiResponseDto> handleRateLimitException(RateLimitException ex) {
        log.warn("Rate limit exceeded: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponseDto.error(ex.getMessage()));
    }

    /**
     * Handle all other unexpected exceptions
     *
     * Returns 500 Internal Server Error for unhandled exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseDto> handleGeneralException(Exception ex) {
        log.error("Unexpected error occurred", ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponseDto.error("An unexpected error occurred: " + ex.getMessage()));
    }
}
