package com.onescale.auth.filter;

import com.onescale.auth.entity.User;
import com.onescale.auth.repository.UserRepository;
import com.onescale.auth.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT Authentication Filter - Processes JWT tokens for authenticated requests
 *
 * This filter is responsible for:
 * 1. Extracting JWT token from Authorization header
 * 2. Validating the token signature and expiration
 * 3. Verifying token type is "access" (not "refresh")
 * 4. Checking if the user exists and is active in database
 * 5. Setting authentication in SecurityContext for protected endpoints
 *
 * Token Expiration Scenarios Handled:
 * - Access token valid: Authentication proceeds normally
 * - Access token expired: validateToken() throws InvalidTokenException, request fails with 401
 * - Refresh token used: isAccessToken() returns false, authentication skipped, endpoint fails with 401
 * - User deleted/deactivated: Active check fails, authentication skipped, request fails with 401
 *
 * The client should handle 401 errors by:
 * 1. Trying to refresh access token using refresh token
 * 2. If refresh fails (refresh token expired), re-authenticate on the frontend
 * 3. If frontend session expired, redirect to login screen
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    /**
     * Process each HTTP request to extract and validate JWT token
     *
     * Flow:
     * 1. Extract Bearer token from Authorization header
     * 2. If no token, proceed without authentication (public endpoints allowed)
     * 3. Validate token signature and expiration
     * 4. Verify token type is "access"
     * 5. Check if user exists and is active
     * 6. Set authentication in SecurityContext
     * 7. If any validation fails, log error and proceed without authentication
     *    (Spring Security will return 401 for protected endpoints)
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        try {
            // Extract JWT token from request header
            String jwt = getJwtFromRequest(request);

            // If token exists, validate and authenticate
            if (StringUtils.hasText(jwt)) {

                // Validate token signature and expiration
                // This will throw InvalidTokenException if token is invalid or expired
                if (jwtUtil.isAccessToken(jwt)) {
                    String clientId = jwtUtil.getClientIdFromToken(jwt);

                    // CRITICAL: Check if user still exists and is active
                    // This handles scenarios where:
                    // 1. User account was deleted (soft delete with is_active = false)
                    // 2. User was banned/suspended by admin
                    // 3. User explicitly logged out (tokens revoked in database)
                    User user = userRepository.findByClientId(clientId).orElse(null);

                    if (user == null) {
                        log.warn("Token validation failed: Client {} not found", clientId);
                        // Don't set authentication - endpoint will fail with 401
                        filterChain.doFilter(request, response);
                        return;
                    }

                    if (!user.getIsActive()) {
                        log.warn("Token validation failed: Client {} is not active (account deleted or suspended)", clientId);
                        // Don't set authentication - endpoint will fail with 401
                        filterChain.doFilter(request, response);
                        return;
                    }

                    // Create authentication token with client_id as principal
                    // Controllers can access this via: Authentication.getPrincipal()
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    clientId, // Principal - client_id for easy access in controllers
                                    null,     // Credentials - not needed for JWT auth
                                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                            );

                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // Set authentication in SecurityContext
                    // This allows protected endpoints to access the authenticated user
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    log.debug("Successfully authenticated client {} via JWT access token", clientId);
                } else {
                    // Token is not an access token (probably a refresh token)
                    log.warn("Token validation failed: Received non-access token (token type: {})",
                            jwtUtil.getTokenType(jwt));
                }
            }
        } catch (Exception ex) {
            // Log error but don't throw exception
            // Let the request proceed - Spring Security will handle 401 for protected endpoints
            log.error("Could not set user authentication in security context: {}", ex.getMessage());
        }

        // Continue with filter chain
        // If authentication was set, protected endpoints will work
        // If authentication was not set, protected endpoints will return 401
        filterChain.doFilter(request, response);
    }

    /**
     * Extract JWT token from Authorization header
     *
     * Expected header format: "Authorization: Bearer <jwt-token>"
     *
     * @param request HTTP request
     * @return JWT token string or null if not found
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7); // Remove "Bearer " prefix
        }

        return null;
    }
}
