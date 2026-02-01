package com.onescale.auth.config;

import com.onescale.auth.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Configure Spring Security filter chain
     *
     * Security Configuration:
     * - CSRF disabled: Not needed for stateless JWT authentication
     * - CORS enabled: Allow cross-origin requests from Android/Web clients
     * - Session Management: STATELESS (no server-side sessions, JWT only)
     * - JWT Filter: Processes JWT tokens before Spring Security's authentication
     *
     * Public Endpoints (no authentication required):
     * - POST /api/v1/auth/firebase/authenticate - Exchange Firebase token for custom JWT
     * - POST /api/v1/auth/refresh - Refresh access token using refresh token
     * - POST /api/v1/auth/validate - Validate custom JWT (for microservices)
     * - GET  /api/v1/auth/health - Health check endpoint
     *
     * Protected Endpoints (require valid JWT access token):
     * - POST   /api/v1/auth/logout - Logout and revoke refresh token
     * - GET    /api/v1/users/me - Get current user profile
     * - PUT    /api/v1/users/me - Update current user profile
     * - DELETE /api/v1/users/me - Delete current user account
     * - All other /api/v1/** endpoints
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public authentication endpoints
                        .requestMatchers(
                                "/api/v1/auth/firebase/**",  // Firebase token exchange
                                "/api/v1/auth/refresh",       // Token refresh
                                "/api/v1/auth/validate",      // Token validation for microservices
                                "/api/v1/auth/health"         // Health check
                        ).permitAll()
                        // Protected endpoints (require authentication)
                        .requestMatchers("/api/v1/auth/logout").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Configure CORS (Cross-Origin Resource Sharing) for API access
     *
     * CORS allows the API to be accessed from different domains (Android app, web frontend).
     *
     * Current Configuration (DEVELOPMENT):
     * - Allowed Origins: * (all origins)
     * - Allowed Methods: GET, POST, PUT, DELETE, OPTIONS
     * - Allowed Headers: * (all headers including Authorization, Content-Type)
     * - Exposed Headers: Authorization (for clients to read token from response)
     * - Max Age: 3600 seconds (1 hour) - browser caches preflight responses
     *
     * IMPORTANT FOR PRODUCTION:
     * Replace "*" with specific allowed origins for security:
     *   configuration.setAllowedOrigins(Arrays.asList(
     *       "https://yourdomain.com",           // Production web app
     *       "https://app.yourdomain.com",       // Android app domain (if applicable)
     *       "http://localhost:3000"             // Local development
     *   ));
     *
     * Android apps making HTTP requests will include origin in request headers.
     * Ensure your Android app's domain (if using WebView) or allow all for native apps.
     *
     * Note: Using "*" for allowedOrigins is a security risk in production as it
     * allows any website to make authenticated requests to your API.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // DEVELOPMENT: Allow all origins
        // PRODUCTION: Replace with specific domains
        configuration.setAllowedOrigins(List.of("*"));

        // Allow common HTTP methods
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // Allow all headers (including Authorization, Content-Type)
        configuration.setAllowedHeaders(List.of("*"));

        // Expose Authorization header so clients can read it from responses
        configuration.setExposedHeaders(List.of("Authorization"));

        // Cache preflight requests for 1 hour (reduces OPTIONS requests)
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
