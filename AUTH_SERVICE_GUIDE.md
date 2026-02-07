# OneScale Authentication Service - Complete Guide

**Version:** 2.0
**Last Updated:** 2024

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Authentication Flows](#authentication-flows)
4. [API Reference](#api-reference)
5. [Frontend Integration](#frontend-integration)
6. [Firebase/Google OAuth Integration](#firebase-google-oauth-integration)
7. [Security & Best Practices](#security--best-practices)
8. [Error Handling](#error-handling)
9. [Testing](#testing)

---

## Overview

OneScale Authentication Service is a production-ready, standard OAuth2-style authentication service that supports:

- ✅ **Password-based authentication** (email/mobile + password)
- ✅ **OAuth providers** (Google, Facebook via Firebase on frontend)
- ✅ **JWT access + refresh tokens**
- ✅ **API Gateway validation endpoint**
- ✅ **Device tracking and management**
- ✅ **Secure password hashing with BCrypt**
- ✅ **Non-enumerable UUID identifiers**

### Key Features

| Feature | Description |
|---------|-------------|
| **Single External ID** | `client_id` (UUID) is the only user identifier exposed to clients |
| **Standard Flow** | Credentials in → JWT tokens out (OAuth2 Password Grant) |
| **Gateway Ready** | `GET /validate` with Authorization header for microservices |
| **Dual Auth Support** | Backend password validation AND frontend OAuth in one service |
| **Secure** | BCrypt password hashing, JWT signatures, refresh token revocation |

---

## Architecture

### System Components

```
┌─────────────────┐
│   Android App   │
│   (Frontend)    │
└────────┬────────┘
         │
         ├─────── Password Auth ────────┐
         │                              │
         │                              ▼
         │                    ┌──────────────────┐
         │                    │  Auth Service    │
         │                    │  (Backend)       │
         │                    │                  │
         ├─────── OAuth ──────┤  • Registration  │
         │      (Gmail)       │  • Login         │
         │                    │  • Token Issue   │
         ▼                    │  • Validation    │
┌─────────────────┐           └────────┬─────────┘
│ Firebase Auth   │                    │
│ (Google OAuth)  │                    ▼
└─────────────────┘           ┌──────────────────┐
                              │   PostgreSQL     │
                              │   • users        │
                              │   • refresh_tokens│
                              └──────────────────┘
```

### Database Schema

#### users table
```sql
id              BIGSERIAL PRIMARY KEY        -- Internal DB id (not exposed)
client_id       VARCHAR(36) UNIQUE NOT NULL  -- External UUID identifier
device_id       VARCHAR(255) NOT NULL        -- Android device identifier
provider        VARCHAR(20) NOT NULL         -- PASSWORD | GOOGLE | FACEBOOK
password_hash   VARCHAR(255)                 -- BCrypt hash (null for OAuth)
email           VARCHAR(255) UNIQUE
mobile_number   VARCHAR(20) UNIQUE
full_name       VARCHAR(255)
profile_picture_url VARCHAR(500)
is_email_verified   BOOLEAN DEFAULT FALSE
is_mobile_verified  BOOLEAN DEFAULT FALSE
is_active           BOOLEAN DEFAULT TRUE
last_login_at       TIMESTAMP
created_at          TIMESTAMP
updated_at          TIMESTAMP
```

#### Indexes
- `idx_user_client_id` on `client_id` (fast token validation)
- `idx_user_email` on `email` (login lookup)
- `idx_user_mobile` on `mobile_number` (login lookup)
- `idx_user_device_id` on `device_id` (device tracking)

---

## Authentication Flows

### Flow 1: Password-Based Authentication (EMAIL/MOBILE + PASSWORD)

**Used for:** Standard username/password authentication

```
┌─────────┐                                      ┌─────────┐
│  User   │                                      │ Backend │
└────┬────┘                                      └────┬────┘
     │                                                │
     │  1. POST /register                             │
     │     {                                          │
     │       fullName: "John Doe",                    │
     │       email: "john@example.com",               │
     │       password: "SecurePass123",               │
     │       deviceId: "android-device-uuid",         │
     │       provider: "PASSWORD"                     │
     │     }                                          │
     ├───────────────────────────────────────────────>│
     │                                                │
     │  2. Returns user profile with client_id        │
     │     {                                          │
     │       success: true,                           │
     │       data: {                                  │
     │         client_id: "uuid-generated",           │
     │         email: "john@example.com",             │
     │         ...                                    │
     │       }                                        │
     │     }                                          │
     │<───────────────────────────────────────────────┤
     │                                                │
     │  3. POST /login                                │
     │     {                                          │
     │       email: "john@example.com",               │
     │       password: "SecurePass123",               │
     │       deviceId: "android-device-uuid"          │
     │     }                                          │
     ├───────────────────────────────────────────────>│
     │                                                │
     │  4. Returns JWT tokens                         │
     │     {                                          │
     │       success: true,                           │
     │       data: {                                  │
     │         accessToken: "eyJhbGc...",             │
     │         refreshToken: "eyJhbGc...",            │
     │         tokenType: "Bearer",                   │
     │         expiresIn: 3600,                       │
     │         user: { ... }                          │
     │       }                                        │
     │     }                                          │
     │<───────────────────────────────────────────────┤
     │                                                │
     │  5. All API calls with header:                 │
     │     Authorization: Bearer <accessToken>        │
     ├───────────────────────────────────────────────>│
     │                                                │
```

### Flow 2: OAuth Authentication (GOOGLE/FACEBOOK via Firebase)

**Used for:** Social login (Google, Facebook)

```
┌─────────┐         ┌──────────┐         ┌─────────┐
│  User   │         │ Firebase │         │ Backend │
└────┬────┘         └────┬─────┘         └────┬────┘
     │                   │                     │
     │  1. Click "Sign in with Google"        │
     ├──────────────────>│                     │
     │                   │                     │
     │  2. Google OAuth  │                     │
     │     Flow          │                     │
     │<─────────────────>│                     │
     │                   │                     │
     │  3. Firebase returns user info          │
     │     {                                   │
     │       uid: "firebase-uid",              │
     │       email: "user@gmail.com",          │
     │       displayName: "John Doe",          │
     │       photoURL: "https://..."           │
     │     }                                   │
     │<──────────────────┤                     │
     │                   │                     │
     │  4. POST /register (first time)         │
     │     {                                   │
     │       fullName: "John Doe",             │
     │       email: "user@gmail.com",          │
     │       provider: "GOOGLE",               │
     │       deviceId: "android-device-uuid",  │
     │       profilePictureUrl: "https://..."  │
     │     }                                   │
     ├─────────────────────────────────────────>│
     │                                          │
     │  5. Returns user profile + client_id    │
     │<──────────────────────────────────────────┤
     │                                          │
     │  6. POST /token (optional, for JWT)     │
     │     {                                   │
     │       clientId: "uuid-from-register",   │
     │       deviceId: "android-device-uuid"   │
     │     }                                   │
     ├─────────────────────────────────────────>│
     │                                          │
     │  7. Returns JWT tokens                  │
     │<──────────────────────────────────────────┤
     │                                          │
```

**Note:** For OAuth users, you can also implement a backend `/oauth-callback` endpoint that exchanges Firebase ID tokens for your JWT tokens if preferred.

### Flow 3: Token Refresh

```
┌─────────┐                                      ┌─────────┐
│  Client │                                      │ Backend │
└────┬────┘                                      └────┬────┘
     │                                                │
     │  1. Access token expired (401 error)          │
     │<──────────────────────────────────────────────┤
     │                                                │
     │  2. POST /refresh                              │
     │     {                                          │
     │       refreshToken: "eyJhbGc..."               │
     │     }                                          │
     ├───────────────────────────────────────────────>│
     │                                                │
     │  3. Returns new access token                   │
     │     {                                          │
     │       accessToken: "eyJhbGc...",               │
     │       refreshToken: "same-refresh-token",      │
     │       expiresIn: 3600                          │
     │     }                                          │
     │<───────────────────────────────────────────────┤
     │                                                │
     │  4. Retry original request with new token      │
     ├───────────────────────────────────────────────>│
     │                                                │
```

### Flow 4: API Gateway Token Validation

```
┌─────────┐         ┌──────────┐         ┌─────────────┐
│ Client  │         │ Gateway  │         │ Auth Service│
└────┬────┘         └────┬─────┘         └──────┬──────┘
     │                   │                       │
     │  1. API Request   │                       │
     │  Authorization: Bearer <token>            │
     ├──────────────────>│                       │
     │                   │                       │
     │                   │  2. GET /validate     │
     │                   │  Authorization: Bearer <token>
     │                   ├──────────────────────>│
     │                   │                       │
     │                   │  3. Token validation  │
     │                   │     {                 │
     │                   │       is_valid: true, │
     │                   │       client_id: "...",│
     │                   │       email: "...",   │
     │                   │       ...             │
     │                   │     }                 │
     │                   │<──────────────────────┤
     │                   │                       │
     │  4. Forward to    │                       │
     │     service       │                       │
     │<──────────────────┤                       │
     │                   │                       │
```

---

## API Reference

### Base URL
```
http://localhost:8080/api/v1/auth
```

---

### 1. POST /register

**Purpose:** Create a new user account

**Authentication:** None (Public)

**Request Body:**

```json
{
  "fullName": "John Doe",
  "email": "john@example.com",           // Required for PASSWORD provider
  "mobileNumber": "+1234567890",         // Optional (or required instead of email)
  "password": "SecurePassword123",       // REQUIRED for PASSWORD provider
  "deviceId": "android-device-12345",    // Required
  "provider": "PASSWORD",                // PASSWORD | GOOGLE | FACEBOOK
  "profilePictureUrl": "https://..."     // Optional
}
```

**Validation Rules:**
- `fullName`: Required, max 255 chars
- `email` OR `mobileNumber`: At least one required
- `password`: Required for PASSWORD provider, 8-100 chars, must be null for OAuth
- `deviceId`: Required, max 255 chars
- `provider`: Required, max 20 chars

**Response (201 Created):**

```json
{
  "success": true,
  "message": "User registered successfully",
  "data": {
    "client_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "device_id": "android-device-12345",
    "provider": "PASSWORD",
    "email": "john@example.com",
    "mobile_number": "+1234567890",
    "full_name": "John Doe",
    "profile_picture_url": "https://...",
    "is_email_verified": true,
    "is_mobile_verified": true
  }
}
```

**Error Responses:**

```json
// 400 Bad Request - Email already registered
{
  "success": false,
  "message": "Email is already registered"
}

// 400 Bad Request - Password required
{
  "success": false,
  "message": "Password is required for PASSWORD provider"
}

// 400 Bad Request - Validation error
{
  "success": false,
  "message": "Validation failed",
  "data": {
    "password": "Password must be between 8 and 100 characters"
  }
}
```

---

### 2. POST /login

**Purpose:** Authenticate user and receive JWT tokens

**Authentication:** None (Public)

**Request Body:**

```json
{
  "email": "john@example.com",        // Required (or mobile_number)
  "mobileNumber": "+1234567890",      // Alternative to email
  "password": "SecurePassword123",    // Required for PASSWORD provider
  "deviceId": "android-device-12345"  // Required
}
```

**Response (200 OK):**

```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
    "refreshToken": "eyJhbGciOiJIUzUxMiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "user": {
      "client_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "email": "john@example.com",
      "full_name": "John Doe",
      "device_id": "android-device-12345",
      "provider": "PASSWORD"
    }
  }
}
```

**Error Responses:**

```json
// 401 Unauthorized - Invalid credentials
{
  "success": false,
  "message": "Invalid credentials"
}

// 401 Unauthorized - Account inactive
{
  "success": false,
  "message": "User account is inactive"
}
```

**Usage:**
1. Call after registration (for PASSWORD users)
2. Call on app launch if user was previously logged in
3. Store `accessToken` and `refreshToken` securely (encrypted SharedPreferences)

---

### 3. POST /token ⚠️ DEPRECATED

**Purpose:** Issue JWT tokens (legacy endpoint)

**Status:** Deprecated - Use `/login` instead

**Authentication:** None (Public)

**Request Body:**

```json
{
  "clientId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "deviceId": "android-device-12345"
}
```

**Response:** Same as `/login`

**Note:** Kept for backward compatibility. New integrations should use `/login`.

---

### 4. POST /refresh

**Purpose:** Exchange refresh token for new access token

**Authentication:** None (Public, but requires valid refresh token)

**Request Body:**

```json
{
  "refreshToken": "eyJhbGciOiJIUzUxMiJ9..."
}
```

**Response (200 OK):**

```json
{
  "success": true,
  "message": "Token refreshed successfully",
  "data": {
    "accessToken": "eyJhbGciOiJIUzUxMiJ9...",  // New access token
    "refreshToken": "eyJhbGciOiJIUzUxMiJ9...",  // Same refresh token
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "user": {
      "client_id": "...",
      "email": "...",
      ...
    }
  }
}
```

**Error Responses:**

```json
// 401 Unauthorized - Refresh token expired or revoked
{
  "success": false,
  "message": "Refresh token is expired or revoked"
}
```

**When to call:**
- When you receive 401 error on API calls with expired access token
- Implement automatic retry logic in your HTTP interceptor

---

### 5. POST /logout

**Purpose:** Revoke refresh token (logout)

**Authentication:** Required (Bearer token)

**Headers:**
```
Authorization: Bearer <accessToken>
```

**Request Body:**

```json
{
  "refreshToken": "eyJhbGciOiJIUzUxMiJ9..."
}
```

**Response (200 OK):**

```json
{
  "success": true,
  "message": "Logged out successfully"
}
```

**Side Effects:**
- Refresh token is revoked in database
- Future refresh attempts will fail
- Access token remains valid until expiry (1 hour)

---

### 6. GET /validate (Gateway Validation)

**Purpose:** Validate access token from Authorization header (for API Gateway)

**Authentication:** None (Public)

**Headers:**
```
Authorization: Bearer <accessToken>
```

**Response (200 OK):**

```json
{
  "success": true,
  "message": "Token is valid",
  "data": {
    "is_valid": true,
    "client_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "email": "john@example.com",
    "mobile_number": "+1234567890",
    "token_type": "custom_jwt",
    "issued_at": 1234567890,
    "expires_at": 1234571490
  }
}
```

**Error Responses:**

```json
// 401 Unauthorized - Invalid or expired token
{
  "success": false,
  "message": "Token has expired"
}

// 401 Unauthorized - User not found or inactive
{
  "success": false,
  "message": "User account is not active"
}
```

**Use Case:** API Gateway validates incoming requests before forwarding to services

---

### 7. POST /validate (Legacy)

**Purpose:** Validate access token from request body

**Authentication:** None (Public)

**Request Body:**

```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9..."
}
```

**Response:** Same as `GET /validate`

**Note:** Prefer `GET /validate` with Authorization header for new integrations.

---

### 8. GET /health

**Purpose:** Health check / liveness probe

**Authentication:** None (Public)

**Response (200 OK):**

```json
{
  "success": true,
  "message": "Auth service is running"
}
```

---

### 9. GET /users/me

**Purpose:** Get current user profile

**Authentication:** Required (Bearer token)

**Headers:**
```
Authorization: Bearer <accessToken>
```

**Response (200 OK):**

```json
{
  "success": true,
  "message": "User profile retrieved successfully",
  "data": {
    "client_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "email": "john@example.com",
    "mobile_number": "+1234567890",
    "full_name": "John Doe",
    "profile_picture_url": "https://...",
    "device_id": "android-device-12345",
    "provider": "PASSWORD",
    "is_email_verified": true,
    "is_mobile_verified": true
  }
}
```

---

### 10. PUT /users/me

**Purpose:** Update current user profile

**Authentication:** Required (Bearer token)

**Request Body:**

```json
{
  "fullName": "John Updated Doe",
  "profilePictureUrl": "https://new-url.com/photo.jpg"
}
```

**Response (200 OK):**

```json
{
  "success": true,
  "message": "User profile updated successfully",
  "data": {
    "client_id": "...",
    "full_name": "John Updated Doe",
    "profile_picture_url": "https://new-url.com/photo.jpg",
    ...
  }
}
```

---

### 11. DELETE /users/me

**Purpose:** Delete user account (soft delete)

**Authentication:** Required (Bearer token)

**Response (200 OK):**

```json
{
  "success": true,
  "message": "User account deleted successfully"
}
```

**Side Effects:**
- User `is_active` set to `false`
- All refresh tokens revoked
- User can no longer login
- Data retained for audit purposes

---

## Frontend Integration

### Android/Kotlin Example

#### 1. Setup Dependencies

```gradle
// build.gradle.kts
dependencies {
    // Retrofit for HTTP
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")

    // Firebase Auth (for OAuth)
    implementation("com.google.firebase:firebase-auth-ktx:22.3.0")
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // Encrypted SharedPreferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
```

#### 2. API Service Interface

```kotlin
// AuthApi.kt
interface AuthApi {
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): ApiResponse<UserDto>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): ApiResponse<AuthResponse>

    @POST("auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): ApiResponse<AuthResponse>

    @POST("auth/logout")
    suspend fun logout(@Body request: LogoutRequest): ApiResponse<Unit>

    @GET("users/me")
    suspend fun getCurrentUser(): ApiResponse<UserDto>
}

// Data classes
data class RegisterRequest(
    val fullName: String,
    val email: String?,
    val mobileNumber: String?,
    val password: String?,
    val deviceId: String,
    val provider: String,
    val profilePictureUrl: String?
)

data class LoginRequest(
    val email: String?,
    val mobileNumber: String?,
    val password: String?,
    val deviceId: String
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val user: UserDto
)

data class UserDto(
    @SerializedName("client_id") val clientId: String,
    @SerializedName("device_id") val deviceId: String,
    val provider: String,
    val email: String?,
    @SerializedName("mobile_number") val mobileNumber: String?,
    @SerializedName("full_name") val fullName: String?,
    @SerializedName("profile_picture_url") val profilePictureUrl: String?,
    @SerializedName("is_email_verified") val isEmailVerified: Boolean,
    @SerializedName("is_mobile_verified") val isMobileVerified: Boolean
)
```

#### 3. Token Manager

```kotlin
// TokenManager.kt
class TokenManager(private val context: Context) {
    private val sharedPreferences = EncryptedSharedPreferences.create(
        "auth_prefs",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveTokens(accessToken: String, refreshToken: String) {
        sharedPreferences.edit {
            putString("access_token", accessToken)
            putString("refresh_token", refreshToken)
        }
    }

    fun getAccessToken(): String? {
        return sharedPreferences.getString("access_token", null)
    }

    fun getRefreshToken(): String? {
        return sharedPreferences.getString("refresh_token", null)
    }

    fun clearTokens() {
        sharedPreferences.edit {
            remove("access_token")
            remove("refresh_token")
        }
    }

    fun isLoggedIn(): Boolean {
        return getAccessToken() != null
    }
}
```

#### 4. Auth Interceptor (Auto Token Refresh)

```kotlin
// AuthInterceptor.kt
class AuthInterceptor(
    private val tokenManager: TokenManager,
    private val authApi: AuthApi
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Skip auth for public endpoints
        if (originalRequest.url.encodedPath.contains("/register") ||
            originalRequest.url.encodedPath.contains("/login")) {
            return chain.proceed(originalRequest)
        }

        // Add access token to request
        val accessToken = tokenManager.getAccessToken()
        val newRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $accessToken")
            .build()

        val response = chain.proceed(newRequest)

        // If 401, try to refresh token
        if (response.code == 401 && accessToken != null) {
            response.close()

            synchronized(this) {
                val refreshToken = tokenManager.getRefreshToken() ?: return response

                // Try to refresh
                val refreshResponse = runBlocking {
                    try {
                        authApi.refreshToken(RefreshTokenRequest(refreshToken))
                    } catch (e: Exception) {
                        null
                    }
                }

                if (refreshResponse?.success == true) {
                    // Save new tokens
                    refreshResponse.data?.let {
                        tokenManager.saveTokens(it.accessToken, it.refreshToken)
                    }

                    // Retry original request with new token
                    val retryRequest = originalRequest.newBuilder()
                        .header("Authorization", "Bearer ${refreshResponse.data?.accessToken}")
                        .build()
                    return chain.proceed(retryRequest)
                } else {
                    // Refresh failed, clear tokens and redirect to login
                    tokenManager.clearTokens()
                    return response
                }
            }
        }

        return response
    }
}
```

#### 5. AuthRepository

```kotlin
// AuthRepository.kt
class AuthRepository(
    private val authApi: AuthApi,
    private val tokenManager: TokenManager
) {

    suspend fun registerWithPassword(
        fullName: String,
        email: String,
        password: String,
        deviceId: String
    ): Result<UserDto> = withContext(Dispatchers.IO) {
        try {
            val response = authApi.register(
                RegisterRequest(
                    fullName = fullName,
                    email = email,
                    mobileNumber = null,
                    password = password,
                    deviceId = deviceId,
                    provider = "PASSWORD",
                    profilePictureUrl = null
                )
            )

            if (response.success) {
                Result.success(response.data!!)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginWithPassword(
        email: String,
        password: String,
        deviceId: String
    ): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val response = authApi.login(
                LoginRequest(
                    email = email,
                    mobileNumber = null,
                    password = password,
                    deviceId = deviceId
                )
            )

            if (response.success) {
                // Save tokens
                response.data?.let {
                    tokenManager.saveTokens(it.accessToken, it.refreshToken)
                }
                Result.success(response.data!!)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val refreshToken = tokenManager.getRefreshToken() ?: return@withContext Result.failure(Exception("Not logged in"))

            authApi.logout(LogoutRequest(refreshToken))
            tokenManager.clearTokens()

            Result.success(Unit)
        } catch (e: Exception) {
            tokenManager.clearTokens()
            Result.failure(e)
        }
    }
}
```

#### 6. Login Activity Example

```kotlin
// LoginActivity.kt
class LoginActivity : AppCompatActivity() {
    private lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize
        val tokenManager = TokenManager(this)
        val authApi = RetrofitClient.create(AuthApi::class.java)
        authRepository = AuthRepository(authApi, tokenManager)

        // Login button click
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString()
            val password = etPassword.text.toString()
            val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

            lifecycleScope.launch {
                showLoading(true)

                val result = authRepository.loginWithPassword(email, password, deviceId)

                showLoading(false)

                result.onSuccess {
                    // Navigate to main activity
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                }.onFailure {
                    Toast.makeText(this@LoginActivity, "Login failed: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Register button click
        btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Sign in with Google
        btnGoogleSignIn.setOnClickListener {
            signInWithGoogle()
        }
    }
}
```

---

## Firebase/Google OAuth Integration

### Setup Firebase

1. **Create Firebase Project**
   - Go to https://console.firebase.google.com/
   - Create new project or use existing
   - Add Android app with your package name

2. **Download google-services.json**
   - Place in `app/` directory

3. **Enable Google Sign-In**
   - Firebase Console → Authentication → Sign-in method
   - Enable "Google" provider

### Android Implementation

```kotlin
// GoogleAuthHelper.kt
class GoogleAuthHelper(private val activity: Activity) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(activity.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        GoogleSignIn.getClient(activity, gso)
    }

    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    fun handleSignInResult(data: Intent?, onComplete: (Result<GoogleSignInAccount>) -> Unit) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            onComplete(Result.success(account))
        } catch (e: ApiException) {
            onComplete(Result.failure(e))
        }
    }

    fun firebaseAuthWithGoogle(account: GoogleSignInAccount, onComplete: (Result<FirebaseUser>) -> Unit) {
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(activity) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        onComplete(Result.success(user))
                    } else {
                        onComplete(Result.failure(Exception("User is null")))
                    }
                } else {
                    onComplete(Result.failure(task.exception ?: Exception("Unknown error")))
                }
            }
    }
}
```

### Complete Google Sign-In Flow

```kotlin
// LoginActivity.kt
class LoginActivity : AppCompatActivity() {

    private lateinit var googleAuthHelper: GoogleAuthHelper
    private lateinit var authRepository: AuthRepository
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            handleGoogleSignInResult(result.data)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        googleAuthHelper = GoogleAuthHelper(this)

        btnGoogleSignIn.setOnClickListener {
            signInWithGoogle()
        }
    }

    private fun signInWithGoogle() {
        val signInIntent = googleAuthHelper.getSignInIntent()
        googleSignInLauncher.launch(signInIntent)
    }

    private fun handleGoogleSignInResult(data: Intent?) {
        showLoading(true)

        googleAuthHelper.handleSignInResult(data) { accountResult ->
            accountResult.onSuccess { account ->
                // Step 1: Authenticate with Firebase
                googleAuthHelper.firebaseAuthWithGoogle(account) { firebaseResult ->
                    firebaseResult.onSuccess { firebaseUser ->
                        // Step 2: Register/Login with backend
                        registerWithBackend(firebaseUser, account)
                    }.onFailure { e ->
                        showLoading(false)
                        Toast.makeText(this, "Firebase auth failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }.onFailure { e ->
                showLoading(false)
                Toast.makeText(this, "Google sign in failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun registerWithBackend(firebaseUser: FirebaseUser, googleAccount: GoogleSignInAccount) {
        lifecycleScope.launch {
            val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

            // Try to register (will fail if already exists, that's ok)
            val registerResult = authRepository.registerWithGoogle(
                fullName = firebaseUser.displayName ?: "",
                email = firebaseUser.email ?: "",
                profilePictureUrl = firebaseUser.photoUrl?.toString(),
                deviceId = deviceId
            )

            // Get JWT tokens
            registerResult.onSuccess { userDto ->
                // Call /token to get JWT (or implement OAuth callback)
                val tokenResult = authRepository.getTokenForOAuthUser(
                    clientId = userDto.clientId,
                    deviceId = deviceId
                )

                tokenResult.onSuccess {
                    showLoading(false)
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                }.onFailure { e ->
                    showLoading(false)
                    Toast.makeText(this@LoginActivity, "Failed to get tokens: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }.onFailure { e ->
                // If registration failed because user exists, that's ok
                // Try to get token directly
                if (e.message?.contains("already registered") == true) {
                    // Fetch user profile first to get client_id
                    // Or implement a backend endpoint that accepts Firebase ID token
                    showLoading(false)
                    Toast.makeText(this@LoginActivity, "Please implement OAuth callback endpoint", Toast.LENGTH_LONG).show()
                } else {
                    showLoading(false)
                    Toast.makeText(this@LoginActivity, "Registration failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
```

### Recommended: Backend OAuth Callback Endpoint

For better OAuth flow, implement a backend endpoint that accepts Firebase ID tokens:

```kotlin
// Add to AuthApi.kt
@POST("auth/oauth/google")
suspend fun authenticateWithGoogle(@Body request: GoogleAuthRequest): ApiResponse<AuthResponse>

data class GoogleAuthRequest(
    val firebaseIdToken: String,
    val deviceId: String
)
```

Then on backend, verify the Firebase ID token and issue your JWT. This is more secure than exposing `/token` endpoint to OAuth users.

---

## Security & Best Practices

### 1. Password Requirements

- Minimum 8 characters
- Recommended: Mix of uppercase, lowercase, numbers, special chars
- Implement password strength indicator on frontend

### 2. Token Storage

✅ **DO:**
- Use `EncryptedSharedPreferences` on Android
- Use Keychain on iOS
- Never log tokens
- Clear tokens on logout

❌ **DON'T:**
- Store in plain SharedPreferences
- Store in logs or crash reports
- Share tokens between apps

### 3. Device Tracking

- Use `Settings.Secure.ANDROID_ID` for device identification
- Update `device_id` on every login
- Backend validates device_id before issuing tokens

### 4. Token Expiration

- Access Token: 1 hour
- Refresh Token: 7 days
- Implement automatic refresh in HTTP interceptor
- Force re-login after refresh token expires

### 5. Logout Best Practices

```kotlin
suspend fun logout() {
    try {
        // 1. Call backend logout
        authRepository.logout()
    } finally {
        // 2. Clear local tokens (even if backend call fails)
        tokenManager.clearTokens()

        // 3. Sign out from Firebase (if using OAuth)
        FirebaseAuth.getInstance().signOut()
        GoogleSignIn.getClient(context, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()

        // 4. Clear app data if needed
        clearUserData()

        // 5. Navigate to login screen
        navigateToLogin()
    }
}
```

### 6. Network Security

- Use HTTPS only in production
- Implement certificate pinning for critical apps
- Add network security config:

```xml
<!-- res/xml/network_security_config.xml -->
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false" />
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">localhost</domain>
        <domain includeSubdomains="true">10.0.2.2</domain>
    </domain-config>
</network-security-config>
```

---

## Error Handling

### Standard Error Response Format

```json
{
  "success": false,
  "message": "Error description",
  "data": null
}
```

### Error Codes

| HTTP Status | Error | Retry Strategy |
|-------------|-------|----------------|
| 400 | Bad Request | Don't retry, fix request |
| 401 | Unauthorized | Try refresh token, then re-login |
| 403 | Forbidden | User doesn't have permission |
| 404 | Not Found | Resource doesn't exist |
| 429 | Too Many Requests | Implement exponential backoff |
| 500 | Internal Server Error | Retry with backoff |
| 503 | Service Unavailable | Retry with backoff |

### Android Error Handler Example

```kotlin
sealed class ApiError(message: String) : Exception(message) {
    class BadRequest(message: String) : ApiError(message)
    class Unauthorized(message: String) : ApiError(message)
    class Forbidden(message: String) : ApiError(message)
    class NotFound(message: String) : ApiError(message)
    class RateLimit(message: String) : ApiError(message)
    class ServerError(message: String) : ApiError(message)
    class NetworkError(message: String) : ApiError(message)
    class Unknown(message: String) : ApiError(message)
}

fun handleApiError(e: Throwable): ApiError {
    return when (e) {
        is HttpException -> {
            when (e.code()) {
                400 -> ApiError.BadRequest(e.message())
                401 -> ApiError.Unauthorized("Session expired")
                403 -> ApiError.Forbidden("Access denied")
                404 -> ApiError.NotFound("Resource not found")
                429 -> ApiError.RateLimit("Too many requests")
                in 500..599 -> ApiError.ServerError("Server error")
                else -> ApiError.Unknown(e.message())
            }
        }
        is IOException -> ApiError.NetworkError("Network error. Check your connection.")
        else -> ApiError.Unknown(e.message ?: "Unknown error")
    }
}
```

---

## Testing

### 1. Postman Collection

Import the Postman collection from `postman_collection.json` in the repository.

### 2. Manual Testing Flow - Password Auth

```bash
# Step 1: Register
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "Test User",
    "email": "test@example.com",
    "password": "TestPass123",
    "deviceId": "test-device-123",
    "provider": "PASSWORD"
  }'

# Response: Note the client_id

# Step 2: Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "TestPass123",
    "deviceId": "test-device-123"
  }'

# Response: Save accessToken and refreshToken

# Step 3: Get Profile
curl -X GET http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer <accessToken>"

# Step 4: Refresh Token
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "<refreshToken>"
  }'

# Step 5: Logout
curl -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "<refreshToken>"
  }'
```

### 3. Gateway Validation Test

```bash
# Validate token from header
curl -X GET http://localhost:8080/api/v1/auth/validate \
  -H "Authorization: Bearer <accessToken>"

# Response shows token validity and user info
```

---

## JWT Token Details

### Access Token Structure

```json
{
  "sub": "a1b2c3d4-uuid-client-id",    // Subject: client_id
  "email": "user@example.com",
  "mobileNumber": "+1234567890",
  "fullName": "John Doe",
  "tokenType": "access",
  "iss": "onescale-auth",               // Issuer
  "iat": 1234567890,                    // Issued At
  "exp": 1234571490                     // Expires (1 hour)
}
```

### Refresh Token Structure

```json
{
  "sub": "a1b2c3d4-uuid-client-id",
  "tokenType": "refresh",
  "iss": "onescale-auth",
  "iat": 1234567890,
  "exp": 1235172690                     // Expires (7 days)
}
```

### Token Validation

- **Signature:** HMAC-SHA512 with secret key from `application.yml`
- **Expiration:** Automatically checked by JWT library
- **Type Check:** Filter validates `tokenType` is "access" for API calls
- **User Active Check:** Filter verifies user exists and `is_active = true`

---

## Configuration

### application.yml

```yaml
jwt:
  secret: ${JWT_SECRET:your-256-bit-secret-key-change-in-production}
  issuer: onescale-auth
  access-token-expiration: 3600000    # 1 hour in milliseconds
  refresh-token-expiration: 604800000  # 7 days in milliseconds

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/onescale_auth
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
```

### Environment Variables (Production)

```bash
# Required
export JWT_SECRET="your-secure-256-bit-secret-change-this"
export DB_USERNAME="production_user"
export DB_PASSWORD="production_password"
export DB_URL="jdbc:postgresql://prod-db:5432/onescale_auth"

# Optional
export JWT_ACCESS_TOKEN_EXPIRATION=3600000    # 1 hour
export JWT_REFRESH_TOKEN_EXPIRATION=604800000  # 7 days
```

---

## FAQ

### Q: Can I use both password and OAuth for the same user?

**A:** No. Each user account is tied to one provider (PASSWORD, GOOGLE, or FACEBOOK). If a user registers with Google and later wants to use password, they would need to create a separate account with a different email.

### Q: What happens if I lose the refresh token?

**A:** User needs to login again with their credentials to get a new token pair.

### Q: Can I have multiple devices for one user?

**A:** Yes, but the `device_id` is updated on each login. The backend tracks the most recent device. For multi-device support, you'd need to modify the schema to store multiple device_ids per user.

### Q: How do I implement "Remember Me"?

**A:** Store the refresh token securely (encrypted SharedPreferences). On app launch, check if refresh token exists and is valid. If yes, call `/refresh` to get new access token. If refresh fails, redirect to login.

### Q: What if access token expires mid-request?

**A:** The AuthInterceptor automatically catches 401 errors, calls `/refresh`, and retries the original request with the new token. This is transparent to your code.

### Q: How do I change a user's password?

**A:** You'll need to implement a `PUT /users/me/password` endpoint that accepts `oldPassword` and `newPassword`. Verify old password with BCrypt, hash new password, and update in database.

### Q: Can I extend token expiration time?

**A:** Yes, change `access-token-expiration` and `refresh-token-expiration` in `application.yml`. Keep access tokens short (1 hour) for security, refresh tokens can be longer (7-30 days).

### Q: How do I test with Firebase Auth in development?

**A:** Use Firebase Emulator Suite for local testing without hitting production Firebase: https://firebase.google.com/docs/emulator-suite

---

## Troubleshooting

### Issue: "Invalid credentials" on login

**Check:**
1. Password is correct (case-sensitive)
2. Email matches registration
3. User account is active (`is_active = true`)
4. Provider is "PASSWORD"

### Issue: "Token has expired"

**Check:**
1. Access token lifetime (1 hour default)
2. Implement refresh token flow
3. Check system time on device (JWT uses timestamps)

### Issue: "User not found" after OAuth login

**Check:**
1. User was registered with `/register` first
2. Email matches between Firebase and backend
3. Check provider field matches ("GOOGLE", "FACEBOOK")

### Issue: 403 Forbidden on API calls

**Check:**
1. Access token is valid (not expired)
2. Authorization header format: `Bearer <token>`
3. Endpoint is not public (check SecurityConfig)

### Issue: Firebase authentication works but backend fails

**Check:**
1. Backend `/register` was called with Firebase user info
2. `client_id` from registration response is stored
3. `/token` endpoint called with correct `client_id` and `device_id`

---

## Next Steps

1. **Setup Database:** Run `schema.sql` in PostgreSQL
2. **Configure Environment:** Set JWT secret and DB credentials
3. **Start Service:** `./mvnw spring-boot:run`
4. **Test with Postman:** Import collection and test endpoints
5. **Integrate Frontend:** Follow Android examples above
6. **Setup Firebase:** Enable Google Sign-In provider
7. **Deploy:** Use Docker or deploy to your cloud provider

---

## Support & Documentation

- **GitHub Issues:** Report bugs and request features
- **API Docs:** Swagger UI at `/swagger-ui.html` (if enabled)
- **Postman Collection:** `postman_collection.json` in repository
- **Database Schema:** `src/main/resources/schema.sql`

---

**Last Updated:** 2024
**Service Version:** 2.0
**Author:** OneScale Team
