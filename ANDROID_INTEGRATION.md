# Android Integration Guide - Token Expiration Handling

Complete guide for integrating the OneScale Authentication Service in Android apps with comprehensive token expiration handling.

## Table of Contents

- [Token Expiration Scenarios](#token-expiration-scenarios)
- [Core Components](#core-components)
- [Token Manager](#token-manager)
- [Auth Manager](#auth-manager)
- [Retrofit Setup](#retrofit-setup)
- [Usage Examples](#usage-examples)

---

## Token Expiration Scenarios

### Scenario Matrix

| Scenario | Access Token | Refresh Token | Firebase Session | Solution |
|----------|-------------|---------------|------------------|----------|
| **Normal** | Valid (< 1h) | Valid | Valid | Use access token |
| **After 1 Hour** | Expired | Valid (< 7 days) | Valid | Auto-refresh access token |
| **After 7 Days** | Expired | Expired | Valid | Re-auth with Firebase |
| **After Logout** | Valid but revoked | Revoked | Valid | Re-login required |
| **Long Absence** | Expired | Expired | Expired | Full re-login |

### Flow Decision Tree

```
Request API
    |
    ├─> Has access token?
    |   ├─> NO → Redirect to login
    |   └─> YES → Continue
    |
    ├─> Is access token expired?
    |   ├─> NO → Use it
    |   └─> YES → Try refresh
    |
    ├─> Has refresh token?
    |   ├─> NO → Redirect to login
    |   └─> YES → Call /refresh
    |
    ├─> Refresh successful?
    |   ├─> YES → Use new access token
    |   └─> NO (401) → Try Firebase re-auth
    |
    ├─> Firebase session valid?
    |   ├─> YES → Exchange for new JWT
    |   └─> NO → Redirect to login
```

---

## Core Components

### 1. Dependencies (app/build.gradle)

```gradle
dependencies {
    // Firebase BOM
    implementation platform('com.google.firebase:firebase-bom:32.7.1')
    implementation 'com.google.firebase:firebase-auth'
    implementation 'com.google.firebase:firebase-auth-ktx'

    // Retrofit for API calls
    implementation 'com.squareup.retrofit2:retrofit:2.9.0'
    implementation 'com.squareup.retrofit2:converter-gson:2.9.0'
    implementation 'com.squareup.okhttp3:logging-interceptor:4.12.0'

    // Coroutines
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'

    // Encrypted SharedPreferences
    implementation "androidx.security:security-crypto:1.1.0-alpha06"

    // Lifecycle
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.7.0'
}
```

---

## Token Manager

Secure token storage with expiration checking.

```kotlin
// TokenManager.kt
package com.yourapp.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject
import android.util.Base64

/**
 * Manages JWT token storage and expiration checking
 *
 * Tokens are stored in EncryptedSharedPreferences for security.
 * Provides methods to check token expiration before making API calls.
 */
object TokenManager {

    private const val PREFS_NAME = "auth_prefs"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_USER_DATA = "user_data"

    private lateinit var encryptedPrefs: SharedPreferences

    /**
     * Initialize TokenManager - Call this in Application.onCreate()
     */
    fun init(context: Context) {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        encryptedPrefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Save authentication tokens
     */
    fun saveTokens(accessToken: String, refreshToken: String) {
        encryptedPrefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            apply()
        }
    }

    /**
     * Get access token
     */
    fun getAccessToken(): String? {
        return encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)
    }

    /**
     * Get refresh token
     */
    fun getRefreshToken(): String? {
        return encryptedPrefs.getString(KEY_REFRESH_TOKEN, null)
    }

    /**
     * Clear all tokens (on logout or auth failure)
     */
    fun clearTokens() {
        encryptedPrefs.edit().clear().apply()
    }

    /**
     * Check if we have valid tokens stored
     */
    fun hasTokens(): Boolean {
        return getAccessToken() != null && getRefreshToken() != null
    }

    /**
     * Check if access token is expired or about to expire
     *
     * Returns true if:
     * - Token is null
     * - Token cannot be decoded
     * - Token has expired
     * - Token will expire in next 5 minutes (buffer for clock skew)
     *
     * @param token JWT access token
     * @return true if expired or about to expire
     */
    fun isAccessTokenExpired(token: String?): Boolean {
        if (token == null) return true

        try {
            // JWT format: header.payload.signature
            val parts = token.split(".")
            if (parts.size != 3) return true

            // Decode payload (middle part)
            val payload = String(
                Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP)
            )

            // Parse JSON to get expiration
            val json = JSONObject(payload)
            val exp = json.getLong("exp") // Expiration in seconds since epoch
            val now = System.currentTimeMillis() / 1000 // Current time in seconds

            // Consider expired if less than 5 minutes (300 seconds) remaining
            // This buffer handles clock skew and gives time for refresh
            return (exp - now) < 300

        } catch (e: Exception) {
            // If we can't decode, consider it expired
            return true
        }
    }

    /**
     * Save user data from auth response
     */
    fun saveUserData(userJson: String) {
        encryptedPrefs.edit().putString(KEY_USER_DATA, userJson).apply()
    }

    /**
     * Get cached user data
     */
    fun getUserData(): String? {
        return encryptedPrefs.getString(KEY_USER_DATA, null)
    }
}
```

---

## Auth Manager

Handles all authentication logic including token refresh and Firebase re-authentication.

```kotlin
// AuthManager.kt
package com.yourapp.auth

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await

/**
 * Centralized authentication manager
 *
 * Handles:
 * - Token expiration detection
 * - Automatic token refresh
 * - Firebase re-authentication when both tokens expired
 * - Logout flow
 *
 * All API calls should go through this manager to ensure valid tokens.
 */
class AuthManager(private val context: Context) {

    companion object {
        private const val TAG = "AuthManager"
    }

    private val apiService = RetrofitClient.getApiService(context)
    private val firebaseAuth = FirebaseAuth.getInstance()

    /**
     * Get valid access token for API calls
     *
     * This method handles all token expiration scenarios:
     * 1. If access token valid → return it
     * 2. If access token expired but refresh token valid → refresh and return new token
     * 3. If both tokens expired but Firebase session valid → re-auth with Firebase
     * 4. If Firebase session expired → return null (caller should redirect to login)
     *
     * Usage:
     * ```
     * val token = authManager.getValidAccessToken()
     * if (token != null) {
     *     // Make API call with token
     *     apiService.getProfile("Bearer $token")
     * } else {
     *     // Redirect to login screen
     *     navigateToLogin()
     * }
     * ```
     *
     * @return Valid access token or null if re-authentication required
     */
    suspend fun getValidAccessToken(): String? {
        val accessToken = TokenManager.getAccessToken()
        val refreshToken = TokenManager.getRefreshToken()

        // No tokens stored - need to login
        if (accessToken == null || refreshToken == null) {
            Log.w(TAG, "No tokens found - login required")
            return null
        }

        // Access token still valid - use it
        if (!TokenManager.isAccessTokenExpired(accessToken)) {
            Log.d(TAG, "Access token is valid")
            return accessToken
        }

        // Access token expired - try to refresh
        Log.d(TAG, "Access token expired - attempting refresh")
        val newAccessToken = refreshAccessToken(refreshToken)

        if (newAccessToken != null) {
            Log.d(TAG, "Token refresh successful")
            return newAccessToken
        }

        // Refresh failed - try Firebase re-auth
        Log.d(TAG, "Token refresh failed - attempting Firebase re-auth")
        return reAuthenticateWithFirebase()
    }

    /**
     * Refresh access token using refresh token
     *
     * @param refreshToken The refresh token
     * @return New access token or null if refresh failed
     */
    private suspend fun refreshAccessToken(refreshToken: String): String? {
        return try {
            val request = RefreshTokenRequest(refreshToken)
            val response = apiService.refreshToken(request)

            if (response.isSuccessful && response.body()?.success == true) {
                val authResponse = response.body()?.data

                if (authResponse != null) {
                    // Save new tokens
                    TokenManager.saveTokens(
                        authResponse.accessToken,
                        authResponse.refreshToken
                    )

                    Log.d(TAG, "Tokens refreshed successfully")
                    authResponse.accessToken
                } else {
                    Log.e(TAG, "Refresh response has no data")
                    null
                }
            } else {
                Log.e(TAG, "Token refresh failed: ${response.code()}")

                // If 401, refresh token expired - clear tokens
                if (response.code() == 401) {
                    Log.w(TAG, "Refresh token expired - clearing tokens")
                    TokenManager.clearTokens()
                }

                null
            }

        } catch (e: Exception) {
            Log.e(TAG, "Token refresh error", e)
            null
        }
    }

    /**
     * Re-authenticate using existing Firebase session
     *
     * This is used when both access and refresh tokens have expired,
     * but the user still has an active Firebase session.
     *
     * @return New access token or null if Firebase session expired
     */
    private suspend fun reAuthenticateWithFirebase(): String? {
        val firebaseUser = firebaseAuth.currentUser

        if (firebaseUser == null) {
            Log.w(TAG, "No Firebase user - full login required")
            return null
        }

        return try {
            // Get fresh Firebase ID token
            val idToken = firebaseUser.getIdToken(true).await().token

            if (idToken == null) {
                Log.e(TAG, "Failed to get Firebase ID token")
                return null
            }

            // Exchange Firebase token for custom JWT
            val request = FirebaseAuthRequest(idToken)
            val response = apiService.authenticateWithFirebase(request)

            if (response.isSuccessful && response.body()?.success == true) {
                val authResponse = response.body()?.data

                if (authResponse != null) {
                    // Save new tokens
                    TokenManager.saveTokens(
                        authResponse.accessToken,
                        authResponse.refreshToken
                    )

                    Log.d(TAG, "Firebase re-authentication successful")
                    authResponse.accessToken
                } else {
                    Log.e(TAG, "Firebase auth response has no data")
                    null
                }
            } else {
                Log.e(TAG, "Firebase authentication failed: ${response.code()}")
                null
            }

        } catch (e: Exception) {
            Log.e(TAG, "Firebase re-authentication error", e)
            null
        }
    }

    /**
     * Make authenticated API call with automatic token handling
     *
     * Example usage:
     * ```
     * authManager.makeAuthenticatedCall { bearerToken ->
     *     apiService.getProfile(bearerToken)
     * }
     * ```
     *
     * @param apiCall Lambda that takes bearer token and makes API call
     * @return API response or null if authentication failed
     */
    suspend fun <T> makeAuthenticatedCall(
        apiCall: suspend (String) -> retrofit2.Response<T>
    ): retrofit2.Response<T>? {

        val accessToken = getValidAccessToken()

        if (accessToken == null) {
            Log.w(TAG, "Cannot make API call - authentication required")
            return null
        }

        return try {
            apiCall("Bearer $accessToken")
        } catch (e: Exception) {
            Log.e(TAG, "API call failed", e)
            null
        }
    }

    /**
     * Logout user
     *
     * Steps:
     * 1. Revoke refresh token on backend
     * 2. Clear local tokens
     * 3. Sign out from Firebase
     */
    suspend fun logout(): Boolean {
        return try {
            val accessToken = TokenManager.getAccessToken()
            val refreshToken = TokenManager.getRefreshToken()

            // Try to revoke token on backend
            if (accessToken != null && refreshToken != null) {
                try {
                    val request = RefreshTokenRequest(refreshToken)
                    apiService.logout("Bearer $accessToken", request)
                    Log.d(TAG, "Refresh token revoked on backend")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to revoke token on backend", e)
                    // Continue with local cleanup even if backend call fails
                }
            }

            // Clear local tokens
            TokenManager.clearTokens()

            // Sign out from Firebase
            firebaseAuth.signOut()

            Log.d(TAG, "Logout successful")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Logout error", e)

            // Even on error, clear local data
            TokenManager.clearTokens()
            firebaseAuth.signOut()

            false
        }
    }
}
```

---

## Retrofit Setup

Retrofit client with automatic token handling.

```kotlin
// RetrofitClient.kt
package com.yourapp.network

import android.content.Context
import com.yourapp.auth.TokenManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val BASE_URL = "http://10.0.2.2:8080" // For Android emulator
    // private const val BASE_URL = "https://api.yourdomain.com" // For production

    /**
     * Get API service instance
     */
    fun getApiService(context: Context): ApiService {
        return getInstance(context).create(ApiService::class.java)
    }

    /**
     * Create Retrofit instance with interceptors
     */
    private fun getInstance(context: Context): Retrofit {
        // Logging interceptor for debugging
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        // Token interceptor to add JWT to requests
        val tokenInterceptor = createTokenInterceptor(context)

        // OkHttp client with interceptors
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(tokenInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * Create interceptor that adds JWT token to requests
     *
     * Note: This does NOT handle token refresh automatically.
     * Use AuthManager.makeAuthenticatedCall() for automatic token handling.
     * This interceptor only adds the token if it exists.
     */
    private fun createTokenInterceptor(context: Context): Interceptor {
        return Interceptor { chain ->
            val originalRequest = chain.request()

            // Skip adding token for public endpoints
            val path = originalRequest.url.encodedPath
            if (path.contains("/auth/firebase/") ||
                path.contains("/auth/refresh") ||
                path.contains("/auth/validate") ||
                path.contains("/auth/health")
            ) {
                return@Interceptor chain.proceed(originalRequest)
            }

            // Get access token
            val accessToken = TokenManager.getAccessToken()

            // Add token to request if available
            val authenticatedRequest = if (accessToken != null) {
                originalRequest.newBuilder()
                    .header("Authorization", "Bearer $accessToken")
                    .build()
            } else {
                originalRequest
            }

            chain.proceed(authenticatedRequest)
        }
    }
}

// ApiService.kt
interface ApiService {

    // Authentication endpoints
    @POST("/api/v1/auth/firebase/authenticate")
    suspend fun authenticateWithFirebase(
        @Body request: FirebaseAuthRequest
    ): retrofit2.Response<ApiResponse<AuthResponse>>

    @POST("/api/v1/auth/refresh")
    suspend fun refreshToken(
        @Body request: RefreshTokenRequest
    ): retrofit2.Response<ApiResponse<AuthResponse>>

    @POST("/api/v1/auth/logout")
    suspend fun logout(
        @Header("Authorization") token: String,
        @Body request: RefreshTokenRequest
    ): retrofit2.Response<ApiResponse<Unit>>

    // User endpoints
    @GET("/api/v1/users/me")
    suspend fun getCurrentUser(
        @Header("Authorization") token: String
    ): retrofit2.Response<ApiResponse<UserDto>>

    @PUT("/api/v1/users/me")
    suspend fun updateCurrentUser(
        @Header("Authorization") token: String,
        @Body request: UserUpdateRequest
    ): retrofit2.Response<ApiResponse<UserDto>>

    @DELETE("/api/v1/users/me")
    suspend fun deleteCurrentUser(
        @Header("Authorization") token: String
    ): retrofit2.Response<ApiResponse<Unit>>
}

// DTOs
data class FirebaseAuthRequest(val idToken: String)
data class RefreshTokenRequest(val refreshToken: String)
data class UserUpdateRequest(val fullName: String?, val profilePictureUrl: String?)

data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T?
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val user: UserDto
)

data class UserDto(
    val userId: Long,
    val email: String?,
    val mobileNumber: String?,
    val fullName: String?,
    val profilePictureUrl: String?,
    val isEmailVerified: Boolean,
    val isMobileVerified: Boolean
)
```

---

## Usage Examples

### 1. Application Setup

```kotlin
// MyApplication.kt
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TokenManager.init(this)
    }
}

// Don't forget to register in AndroidManifest.xml:
// <application android:name=".MyApplication" ...>
```

### 2. Login Activity

```kotlin
// LoginActivity.kt
class LoginActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val authManager by lazy { AuthManager(this) }
    private val apiService by lazy { RetrofitClient.getApiService(this) }

    private fun loginWithEmail(email: String, password: String) {
        lifecycleScope.launch {
            try {
                // Step 1: Sign in with Firebase
                val authResult = auth.signInWithEmailAndPassword(email, password).await()
                val firebaseUser = authResult.user

                if (firebaseUser == null) {
                    showError("Login failed")
                    return@launch
                }

                // Step 2: Check email verification
                if (!firebaseUser.isEmailVerified) {
                    showError("Please verify your email first")
                    firebaseUser.sendEmailVerification()
                    auth.signOut()
                    return@launch
                }

                // Step 3: Get Firebase ID token
                val idToken = firebaseUser.getIdToken(true).await().token

                if (idToken == null) {
                    showError("Failed to get authentication token")
                    return@launch
                }

                // Step 4: Exchange for custom JWT
                val request = FirebaseAuthRequest(idToken)
                val response = apiService.authenticateWithFirebase(request)

                if (response.isSuccessful && response.body()?.success == true) {
                    val authResponse = response.body()?.data

                    if (authResponse != null) {
                        // Save tokens
                        TokenManager.saveTokens(
                            authResponse.accessToken,
                            authResponse.refreshToken
                        )

                        // Navigate to main app
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    }
                } else {
                    showError("Backend authentication failed")
                }

            } catch (e: Exception) {
                showError("Login error: ${e.message}")
            }
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
```

### 3. Main Activity - Using AuthManager

```kotlin
// MainActivity.kt
class MainActivity : AppCompatActivity() {

    private val authManager by lazy { AuthManager(this) }
    private val apiService by lazy { RetrofitClient.getApiService(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loadUserProfile()
    }

    /**
     * Load user profile with automatic token handling
     */
    private fun loadUserProfile() {
        lifecycleScope.launch {
            // AuthManager handles all token expiration scenarios automatically
            val response = authManager.makeAuthenticatedCall { bearerToken ->
                apiService.getCurrentUser(bearerToken)
            }

            if (response == null) {
                // Authentication failed - redirect to login
                navigateToLogin()
                return@launch
            }

            if (response.isSuccessful && response.body()?.success == true) {
                val user = response.body()?.data
                if (user != null) {
                    displayUserProfile(user)
                }
            } else {
                Toast.makeText(
                    this@MainActivity,
                    "Failed to load profile: ${response.message()}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Update user profile
     */
    private fun updateProfile(fullName: String, profilePictureUrl: String) {
        lifecycleScope.launch {
            val response = authManager.makeAuthenticatedCall { bearerToken ->
                val request = UserUpdateRequest(fullName, profilePictureUrl)
                apiService.updateCurrentUser(bearerToken, request)
            }

            if (response == null) {
                navigateToLogin()
                return@launch
            }

            if (response.isSuccessful) {
                Toast.makeText(
                    this@MainActivity,
                    "Profile updated successfully",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Logout
     */
    private fun performLogout() {
        lifecycleScope.launch {
            val success = authManager.logout()

            // Navigate to login regardless of backend call result
            navigateToLogin()
        }
    }

    private fun displayUserProfile(user: UserDto) {
        // Update UI with user data
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
```

### 4. Manual Token Check (Alternative Approach)

If you don't want to use AuthManager for every call:

```kotlin
class ProfileActivity : AppCompatActivity() {

    private fun loadProfile() {
        lifecycleScope.launch {
            val accessToken = TokenManager.getAccessToken()

            // Check if token exists
            if (accessToken == null) {
                navigateToLogin()
                return@launch
            }

            // Check if token expired
            if (TokenManager.isAccessTokenExpired(accessToken)) {
                // Try to refresh
                val newToken = tryRefreshToken()

                if (newToken == null) {
                    // Refresh failed - need to login
                    navigateToLogin()
                    return@launch
                }

                // Use new token
                makeApiCall("Bearer $newToken")
            } else {
                // Token still valid
                makeApiCall("Bearer $accessToken")
            }
        }
    }

    private suspend fun tryRefreshToken(): String? {
        val refreshToken = TokenManager.getRefreshToken() ?: return null

        return try {
            val apiService = RetrofitClient.getApiService(this)
            val request = RefreshTokenRequest(refreshToken)
            val response = apiService.refreshToken(request)

            if (response.isSuccessful && response.body()?.success == true) {
                val authResponse = response.body()?.data

                if (authResponse != null) {
                    TokenManager.saveTokens(
                        authResponse.accessToken,
                        authResponse.refreshToken
                    )
                    authResponse.accessToken
                } else null
            } else {
                // Refresh failed
                if (response.code() == 401) {
                    TokenManager.clearTokens()
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun makeApiCall(bearerToken: String) {
        // Your API call here
    }
}
```

---

## Key Takeaways

### ✅ DO

- **Always use AuthManager** for API calls to handle token expiration automatically
- **Initialize TokenManager** in Application.onCreate()
- **Check token expiration** before making API calls (or let AuthManager handle it)
- **Clear tokens** on 401 errors from refresh endpoint
- **Handle all expiration scenarios**: access token, refresh token, Firebase session

### ❌ DON'T

- **Don't assume tokens are valid** - always check or use AuthManager
- **Don't ignore 401 errors** - they mean re-authentication is required
- **Don't store tokens in plain SharedPreferences** - use EncryptedSharedPreferences
- **Don't retry failed auth requests infinitely** - redirect to login after refresh fails

### 🔒 Security Best Practices

- Use EncryptedSharedPreferences for token storage
- Clear tokens immediately on logout or auth failure
- Don't log tokens in production builds
- Implement certificate pinning for production API calls
- Use ProGuard/R8 to obfuscate token management code

---

## Testing Token Expiration

### Test Scenario 1: Access Token Expired

```kotlin
// Manually expire access token for testing
fun testAccessTokenExpiration() {
    // Save tokens with expired access token
    val expiredAccessToken = "eyJhbGciOiJIUzUxMiJ9..." // Expired token
    val validRefreshToken = TokenManager.getRefreshToken()

    TokenManager.saveTokens(expiredAccessToken, validRefreshToken!!)

    // Try to make API call - should trigger automatic refresh
    lifecycleScope.launch {
        val response = authManager.makeAuthenticatedCall { token ->
            apiService.getCurrentUser(token)
        }

        // Should succeed with refreshed token
        assert(response != null && response.isSuccessful)
    }
}
```

### Test Scenario 2: Both Tokens Expired

```kotlin
fun testBothTokensExpired() {
    // Save expired tokens
    TokenManager.saveTokens("expired_access_token", "expired_refresh_token")

    // Try to make API call - should trigger Firebase re-auth
    lifecycleScope.launch {
        val response = authManager.makeAuthenticatedCall { token ->
            apiService.getCurrentUser(token)
        }

        // Should succeed if Firebase session valid, null if Firebase expired
        if (FirebaseAuth.getInstance().currentUser != null) {
            assert(response != null)
        } else {
            assert(response == null) // Should redirect to login
        }
    }
}
```

---

## Troubleshooting

### Issue: "401 Unauthorized" on all requests

**Causes:**
- Access token expired and refresh token also expired
- User account deleted or deactivated on backend
- Tokens were revoked (user logged out from another device)

**Solution:**
- Check logs to see which token validation failed
- Clear tokens and redirect to login
- Ensure FirebaseAuth.currentUser is not null before re-auth

### Issue: Token refresh returns 401

**Causes:**
- Refresh token expired (> 7 days old)
- Refresh token was revoked on backend (user logged out)
- User account deleted

**Solution:**
- Clear tokens: `TokenManager.clearTokens()`
- Try Firebase re-auth before redirecting to login
- If Firebase re-auth succeeds, continue; otherwise redirect to login

### Issue: Infinite refresh loop

**Causes:**
- Not clearing tokens on 401 from refresh endpoint
- Not checking token expiration before refresh

**Solution:**
- Always clear tokens when refresh returns 401
- Use `isAccessTokenExpired()` to avoid unnecessary refreshes

---

## Summary

This comprehensive integration handles all token expiration scenarios:

1. **Normal usage** (< 1 hour): Uses access token directly
2. **After 1 hour**: Auto-refreshes access token
3. **After 7 days**: Re-authenticates with Firebase
4. **After logout**: Redirects to login
5. **Long absence**: Full re-login required

The `AuthManager` class handles all of this automatically, making API calls simple and error-free.
