# OneScale Authentication Microservice with Firebase

A production-ready authentication microservice built with **Spring Boot** and **Firebase Authentication**. This service handles user authentication via Firebase (Email/Password and Phone), verifies Firebase ID tokens, and issues JWT tokens for secure API access in your trading application.

## Table of Contents

- [Features](#features)
- [Technology Stack](#technology-stack)
- [Prerequisites](#prerequisites)
- [Firebase Setup](#firebase-setup)
- [Quick Start](#quick-start)
- [How It Works](#how-it-works)
- [API Documentation](#api-documentation)
- [Integration with Android](#integration-with-android)
- [Deployment](#deployment)
- [Troubleshooting](#troubleshooting)

## Features

- **Hybrid Authentication**: Firebase handles initial auth, backend issues custom JWT tokens
- **Firebase Email/Password Authentication**: Users sign up and sign in with Firebase
- **Firebase Phone Authentication**: SMS-based OTP verification via Firebase
- **Automatic User Creation**: Creates users in database on first Firebase authentication
- **Custom JWT Token Management**: Issues access tokens (1 hour) and refresh tokens (7 days)
- **Token Refresh**: Seamless token refresh without re-authentication
- **Token Revocation**: Individual and bulk token revocation
- **Protected Endpoints**: JWT-authenticated user profile management
- **Token Validation**: Endpoint for microservices to validate custom JWT tokens
- **Security**: Spring Security 6 with JWT authentication filter
- **Database**: PostgreSQL with JPA/Hibernate
- **Caching**: Redis for session management

## Technology Stack

- **Framework**: Spring Boot 3.2.5
- **Security**: Spring Security 6.x + Firebase Admin SDK
- **Java**: 17+
- **Authentication**: Firebase Authentication
- **Database**: PostgreSQL 15+
- **Cache**: Redis 7+
- **JWT Library**: jjwt 0.12.5
- **Build Tool**: Maven

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- Docker & Docker Compose (for local development)
- Firebase project with Authentication enabled
- Android app configured with Firebase

## Firebase Setup

### Step 1: Create Firebase Project

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Click "Add project" or select existing project
3. Follow the wizard to create your project

### Step 2: Enable Authentication Methods

1. In Firebase Console, go to **Authentication** → **Sign-in method**
2. Enable the following providers:
   - **Email/Password**: Click "Enable" toggle
   - **Phone**: Click "Enable" toggle and configure your phone authentication settings

### Step 3: Get Firebase Project ID

1. In Firebase Console, click the gear icon → **Project settings**
2. Copy your **Project ID** (e.g., `my-trading-app-12345`)
3. Save this for the `.env` file

### Step 4: Generate Service Account Key

This is the **most important step** for backend authentication:

1. In Firebase Console, go to **Project settings** → **Service accounts**
2. Click "**Generate new private key**"
3. Click "**Generate key**" in the confirmation dialog
4. A JSON file will download (e.g., `my-trading-app-12345-firebase-adminsdk-xxxxx.json`)

**IMPORTANT**: This file contains sensitive credentials. **Never commit it to version control!**

### Step 5: Add Service Account to Project

For **Development** (Classpath):
```bash
# Rename the downloaded file
mv ~/Downloads/my-trading-app-*-firebase-adminsdk-*.json src/main/resources/firebase-service-account.json

# Add to .gitignore
echo "src/main/resources/firebase-service-account.json" >> .gitignore
```

For **Production** (File Path):
```bash
# Store in a secure location
mkdir -p /etc/onescale/firebase
mv ~/Downloads/my-trading-app-*-firebase-adminsdk-*.json /etc/onescale/firebase/service-account.json
chmod 600 /etc/onescale/firebase/service-account.json

# Set environment variable
export FIREBASE_CONFIG_PATH=/etc/onescale/firebase/service-account.json
```

### Service Account JSON Structure

The service account JSON file looks like this:

```json
{
  "type": "service_account",
  "project_id": "your-project-id",
  "private_key_id": "xxxxxxxxxxxxxx",
  "private_key": "-----BEGIN PRIVATE KEY-----\nMIIEvQ...\n-----END PRIVATE KEY-----\n",
  "client_email": "firebase-adminsdk-xxxxx@your-project-id.iam.gserviceaccount.com",
  "client_id": "1234567890",
  "auth_uri": "https://accounts.google.com/o/oauth2/auth",
  "token_uri": "https://oauth2.googleapis.com/token",
  "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
  "client_x509_cert_url": "https://www.googleapis.com/robot/v1/metadata/x509/..."
}
```

## Quick Start

### 1. Clone and Configure

```bash
cd onescale-auth-service
cp .env.example .env
```

### 2. Edit `.env` File

```properties
# Firebase Configuration
FIREBASE_PROJECT_ID=my-trading-app-12345  # From Firebase Console

# For Development (loads from classpath)
FIREBASE_CONFIG_PATH=

# For Production (loads from file system)
# FIREBASE_CONFIG_PATH=/etc/onescale/firebase/service-account.json

# JWT Secret (generate a secure key)
JWT_SECRET=$(openssl rand -base64 32)

# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=onescale_auth
DB_USERNAME=postgres
DB_PASSWORD=postgres
```

### 3. Start Infrastructure

```bash
docker-compose up -d
```

### 4. Build and Run

```bash
./mvnw clean package
./mvnw spring-boot:run
```

The service will start on `http://localhost:8080`

### 5. Verify Service

```bash
curl http://localhost:8080/api/v1/auth/health
```

## How It Works

### Authentication Flow

```
┌─────────────┐      ┌──────────────┐      ┌─────────────┐      ┌──────────────┐
│   Android   │      │   Firebase   │      │   Backend   │      │  PostgreSQL  │
│     App     │      │     Auth     │      │   Service   │      │  Database    │
└─────────────┘      └──────────────┘      └─────────────┘      └──────────────┘
       │                     │                      │                     │
       │  1. signInWithEmail │                      │                     │
       │────────────────────>│                      │                     │
       │                     │                      │                     │
       │  2. Firebase verifies password            │                     │
       │     (Backend NOT involved)                │                     │
       │                     │                      │                     │
       │  3. Firebase ID Token                     │                     │
       │<────────────────────│                      │                     │
       │                     │                      │                     │
       │  4. POST /firebase/authenticate           │                     │
       │     { idToken: "eyJhb..." }               │                     │
       │──────────────────────────────────────────>│                     │
       │                     │                      │                     │
       │                     │  5. Verify token     │                     │
       │                     │<─────────────────────│                     │
       │                     │                      │                     │
       │                     │  6. Token valid      │                     │
       │                     │─────────────────────>│                     │
       │                     │                      │                     │
       │                     │                      │  7. Create/Update   │
       │                     │                      │     User            │
       │                     │                      │────────────────────>│
       │                     │                      │                     │
       │                     │                      │  8. User saved      │
       │                     │                      │<────────────────────│
       │                     │                      │                     │
       │  9. JWT Tokens (access + refresh)         │                     │
       │<──────────────────────────────────────────│                     │
       │                     │                      │                     │
```

### Key Concepts

1. **Firebase Handles Initial Authentication**:
   - Firebase stores passwords securely
   - Firebase sends verification emails/SMS
   - Firebase validates credentials
   - Firebase issues ID tokens (valid for 1 hour)

2. **Backend Issues Custom JWT Tokens**:
   - Verifies Firebase ID token is genuine
   - Creates/updates user in database
   - Issues **custom JWT access token** (1 hour) and **refresh token** (7 days)
   - All subsequent API calls use custom JWT tokens, NOT Firebase tokens

3. **Hybrid Security Model**:
   - **Initial auth**: Android → Firebase → Backend (verify Firebase token)
   - **All API calls**: Android → Backend (with custom JWT in Authorization header)
   - **Token refresh**: Android → Backend (exchange refresh token for new access token)
   - Custom JWT tokens are signed with HS512 algorithm
   - Refresh tokens stored in database with revocation support

## API Documentation

### Base URL

```
http://localhost:8080/api/v1/auth
```

### 1. Authenticate with Firebase

Authenticate user with Firebase ID token (Email/Password or Phone).

**Endpoint**: `POST /firebase/authenticate`

**Request Body**:
```json
{
  "idToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6IjY4MTE..."
}
```

**Response**:
```json
{
  "success": true,
  "message": "Authentication successful",
  "data": {
    "accessToken": "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "user": {
      "userId": 1,
      "email": "user@example.com",
      "mobileNumber": null,
      "fullName": "John Doe",
      "profilePictureUrl": null,
      "isEmailVerified": true,
      "isMobileVerified": false
    }
  }
}
```

**cURL Example**:
```bash
curl -X POST http://localhost:8080/api/v1/auth/firebase/authenticate \
  -H "Content-Type: application/json" \
  -d '{"idToken":"eyJhbGciOiJSUzI1NiIsImtpZCI6IjY4MTE..."}'
```

### 2. Refresh Access Token

Get a new access token using refresh token.

**Endpoint**: `POST /refresh`

**Request Body**:
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Response**:
```json
{
  "success": true,
  "message": "Token refreshed successfully",
  "data": {
    "accessToken": "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "user": {
      "userId": 1,
      "email": "user@example.com",
      "mobileNumber": null,
      "fullName": "John Doe",
      "profilePictureUrl": null,
      "isEmailVerified": true,
      "isMobileVerified": false
    }
  }
}
```

### 3. Logout

Revoke refresh token.

**Endpoint**: `POST /logout`

**Headers**:
```
Authorization: Bearer <access_token>
```

**Request Body**:
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Response**:
```json
{
  "success": true,
  "message": "Logged out successfully",
  "data": null
}
```

### 4. Validate Custom JWT Access Token

Validate custom JWT access token (for other microservices to verify tokens).

**Endpoint**: `POST /validate`

**Request Body**:
```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9..."
}
```

**Response**:
```json
{
  "success": true,
  "message": "Token is valid",
  "data": {
    "isValid": true,
    "userId": 1,
    "email": "user@example.com",
    "mobileNumber": "+919876543210",
    "tokenType": "custom_jwt",
    "issuedAt": 1706745600,
    "expiresAt": 1706749200
  }
}
```

**cURL Example**:
```bash
curl -X POST http://localhost:8080/api/v1/auth/validate \
  -H "Content-Type: application/json" \
  -d '{"accessToken":"eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9..."}'
```

### 5. Get Current User Profile

Get authenticated user's profile (protected endpoint).

**Endpoint**: `GET /api/v1/users/me`

**Headers**:
```
Authorization: Bearer <access_token>
```

**Response**:
```json
{
  "success": true,
  "message": "User profile retrieved successfully",
  "data": {
    "userId": 1,
    "email": "user@example.com",
    "mobileNumber": "+919876543210",
    "fullName": "John Doe",
    "profilePictureUrl": "https://example.com/photo.jpg",
    "isEmailVerified": true,
    "isMobileVerified": true
  }
}
```

**cURL Example**:
```bash
curl -X GET http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9..."
```

### 6. Update Current User Profile

Update authenticated user's profile (protected endpoint).

**Endpoint**: `PUT /api/v1/users/me`

**Headers**:
```
Authorization: Bearer <access_token>
```

**Request Body**:
```json
{
  "fullName": "John Smith",
  "profilePictureUrl": "https://example.com/new-photo.jpg"
}
```

**Response**:
```json
{
  "success": true,
  "message": "User profile updated successfully",
  "data": {
    "userId": 1,
    "email": "user@example.com",
    "mobileNumber": "+919876543210",
    "fullName": "John Smith",
    "profilePictureUrl": "https://example.com/new-photo.jpg",
    "isEmailVerified": true,
    "isMobileVerified": true
  }
}
```

**cURL Example**:
```bash
curl -X PUT http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{"fullName":"John Smith","profilePictureUrl":"https://example.com/new-photo.jpg"}'
```

### 7. Delete Current User Account

Delete authenticated user's account - soft delete (protected endpoint).

**Endpoint**: `DELETE /api/v1/users/me`

**Headers**:
```
Authorization: Bearer <access_token>
```

**Response**:
```json
{
  "success": true,
  "message": "User account deleted successfully",
  "data": null
}
```

**cURL Example**:
```bash
curl -X DELETE http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9..."
```

**Note**: This performs a soft delete - the user is marked as inactive and all refresh tokens are revoked.

## Integration with Android

### Complete Android Integration Guide

This guide shows how to integrate the hybrid authentication system in your Android app.

### 1. Gradle Dependencies

Add these dependencies to your `app/build.gradle`:

```gradle
dependencies {
    // Firebase BOM
    implementation platform('com.google.firebase:firebase-bom:32.7.1')

    // Firebase Authentication
    implementation 'com.google.firebase:firebase-auth'
    implementation 'com.google.firebase:firebase-auth-ktx'

    // Retrofit for API calls
    implementation 'com.squareup.retrofit2:retrofit:2.9.0'
    implementation 'com.squareup.retrofit2:converter-gson:2.9.0'
    implementation 'com.squareup.okhttp3:logging-interceptor:4.12.0'

    // Coroutines for async operations
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'

    // Encrypted SharedPreferences for token storage
    implementation "androidx.security:security-crypto:1.1.0-alpha06"
}
```

### 2. API Service Setup

Create your API service interface:

```kotlin
// ApiService.kt
interface ApiService {
    @POST("/api/v1/auth/firebase/authenticate")
    suspend fun authenticateWithFirebase(
        @Body request: FirebaseAuthRequest
    ): ApiResponse<AuthResponse>

    @POST("/api/v1/auth/refresh")
    suspend fun refreshToken(
        @Body request: RefreshTokenRequest
    ): ApiResponse<AuthResponse>

    @POST("/api/v1/auth/logout")
    suspend fun logout(
        @Header("Authorization") token: String,
        @Body request: RefreshTokenRequest
    ): ApiResponse<Unit>

    @GET("/api/v1/users/me")
    suspend fun getCurrentUser(
        @Header("Authorization") token: String
    ): ApiResponse<UserDto>

    @PUT("/api/v1/users/me")
    suspend fun updateCurrentUser(
        @Header("Authorization") token: String,
        @Body request: UserUpdateRequest
    ): ApiResponse<UserDto>
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

### 3. Retrofit Client with Token Interceptor

Create a Retrofit client that automatically adds JWT token to requests:

```kotlin
// RetrofitClient.kt
object RetrofitClient {
    private const val BASE_URL = "http://10.0.2.2:8080"  // For emulator
    // private const val BASE_URL = "https://api.yourdomain.com"  // For production

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // Token interceptor to add JWT to all requests
    private val tokenInterceptor = Interceptor { chain ->
        val original = chain.request()

        // Skip adding token for auth endpoints
        if (original.url.encodedPath.contains("/auth/")) {
            return@Interceptor chain.proceed(original)
        }

        // Get access token from storage
        val accessToken = TokenManager.getAccessToken()

        val request = if (accessToken != null) {
            original.newBuilder()
                .header("Authorization", "Bearer $accessToken")
                .build()
        } else {
            original
        }

        chain.proceed(request)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor(tokenInterceptor)
        .build()

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
```

### 4. Secure Token Storage

Use EncryptedSharedPreferences to securely store tokens:

```kotlin
// TokenManager.kt
object TokenManager {
    private const val PREFS_NAME = "auth_prefs"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"

    private lateinit var encryptedPrefs: SharedPreferences

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

    fun saveTokens(accessToken: String, refreshToken: String) {
        encryptedPrefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            apply()
        }
    }

    fun getAccessToken(): String? = encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? = encryptedPrefs.getString(KEY_REFRESH_TOKEN, null)

    fun clearTokens() {
        encryptedPrefs.edit().clear().apply()
    }

    fun hasValidTokens(): Boolean {
        return getAccessToken() != null && getRefreshToken() != null
    }
}
```

### 5. Email/Password Signup Flow

Complete signup flow with email verification:

```kotlin
// SignupActivity.kt
class SignupActivity : AppCompatActivity() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val apiService: ApiService by lazy { RetrofitClient.apiService }

    private fun signupWithEmail(email: String, password: String) {
        // Step 1: Create account with Firebase
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = task.result?.user

                    // Step 2: Send email verification
                    user?.sendEmailVerification()
                        ?.addOnCompleteListener { verificationTask ->
                            if (verificationTask.isSuccessful) {
                                Toast.makeText(
                                    this,
                                    "Verification email sent to $email. Please verify before logging in.",
                                    Toast.LENGTH_LONG
                                ).show()

                                // Navigate to login screen
                                finish()
                            } else {
                                Toast.makeText(
                                    this,
                                    "Failed to send verification email: ${verificationTask.exception?.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                } else {
                    Toast.makeText(
                        this,
                        "Signup failed: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }
}
```

### 6. Email/Password Login Flow

Complete login flow with backend authentication:

```kotlin
// LoginActivity.kt
class LoginActivity : AppCompatActivity() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val apiService: ApiService by lazy { RetrofitClient.apiService }

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
                    showError("Please verify your email before logging in")

                    // Offer to resend verification email
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

                // Step 4: Authenticate with backend and get custom JWT tokens
                val response = apiService.authenticateWithFirebase(
                    FirebaseAuthRequest(idToken)
                )

                if (response.success && response.data != null) {
                    // Step 5: Save custom JWT tokens
                    TokenManager.saveTokens(
                        response.data.accessToken,
                        response.data.refreshToken
                    )

                    // Step 6: Navigate to main app
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                } else {
                    showError("Backend authentication failed: ${response.message}")
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

### 7. Phone Authentication Flow

Complete phone authentication with OTP:

```kotlin
// PhoneAuthActivity.kt
class PhoneAuthActivity : AppCompatActivity() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val apiService: ApiService by lazy { RetrofitClient.apiService }
    private var verificationId: String? = null

    private fun startPhoneNumberVerification(phoneNumber: String) {
        // Step 1: Configure phone auth options
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)  // Format: "+919876543210"
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(phoneAuthCallbacks)
            .build()

        // Step 2: Start verification
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private val phoneAuthCallbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

        override fun onCodeSent(
            verificationId: String,
            token: PhoneAuthProvider.ForceResendingToken
        ) {
            // OTP sent successfully
            this@PhoneAuthActivity.verificationId = verificationId
            Toast.makeText(
                this@PhoneAuthActivity,
                "OTP sent to your phone",
                Toast.LENGTH_SHORT
            ).show()

            // Show OTP input UI
            showOtpInputDialog()
        }

        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            // Auto-verification (instant or SMS retriever)
            signInWithPhoneCredential(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            Toast.makeText(
                this@PhoneAuthActivity,
                "Verification failed: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun verifyOtpCode(code: String) {
        val verificationId = this.verificationId
        if (verificationId == null) {
            Toast.makeText(this, "Verification ID is null", Toast.LENGTH_SHORT).show()
            return
        }

        // Create credential with user-entered code
        val credential = PhoneAuthProvider.getCredential(verificationId, code)
        signInWithPhoneCredential(credential)
    }

    private fun signInWithPhoneCredential(credential: PhoneAuthCredential) {
        lifecycleScope.launch {
            try {
                // Step 3: Sign in with phone credential
                val authResult = auth.signInWithCredential(credential).await()
                val firebaseUser = authResult.user

                if (firebaseUser == null) {
                    showError("Phone authentication failed")
                    return@launch
                }

                // Step 4: Get Firebase ID token
                val idToken = firebaseUser.getIdToken(true).await().token

                if (idToken == null) {
                    showError("Failed to get authentication token")
                    return@launch
                }

                // Step 5: Authenticate with backend
                val response = apiService.authenticateWithFirebase(
                    FirebaseAuthRequest(idToken)
                )

                if (response.success && response.data != null) {
                    // Step 6: Save custom JWT tokens
                    TokenManager.saveTokens(
                        response.data.accessToken,
                        response.data.refreshToken
                    )

                    // Step 7: Navigate to main app
                    startActivity(Intent(this@PhoneAuthActivity, MainActivity::class.java))
                    finish()
                } else {
                    showError("Backend authentication failed: ${response.message}")
                }

            } catch (e: Exception) {
                showError("Phone authentication error: ${e.message}")
            }
        }
    }

    private fun showOtpInputDialog() {
        // Implement OTP input dialog
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
```

### 8. Making API Calls with Custom JWT

All API calls after login use custom JWT tokens (NOT Firebase tokens):

```kotlin
// MainActivity.kt
class MainActivity : AppCompatActivity() {

    private val apiService: ApiService by lazy { RetrofitClient.apiService }

    private fun loadUserProfile() {
        lifecycleScope.launch {
            try {
                val accessToken = TokenManager.getAccessToken()
                if (accessToken == null) {
                    navigateToLogin()
                    return@launch
                }

                // API call automatically includes JWT token via interceptor
                val response = apiService.getCurrentUser("Bearer $accessToken")

                if (response.success && response.data != null) {
                    displayUserProfile(response.data)
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to load profile: ${response.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                handleApiError(e)
            }
        }
    }

    private fun updateUserProfile(fullName: String, profilePictureUrl: String) {
        lifecycleScope.launch {
            try {
                val accessToken = TokenManager.getAccessToken() ?: return@launch

                val response = apiService.updateCurrentUser(
                    "Bearer $accessToken",
                    UserUpdateRequest(fullName, profilePictureUrl)
                )

                if (response.success) {
                    Toast.makeText(
                        this@MainActivity,
                        "Profile updated successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                handleApiError(e)
            }
        }
    }

    private fun handleApiError(e: Exception) {
        when (e) {
            is HttpException -> {
                if (e.code() == 401) {
                    // Token expired or invalid - try to refresh
                    refreshTokenAndRetry()
                }
            }
            else -> {
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
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

### 9. Token Refresh Flow with Automatic Retry

Implement automatic token refresh when access token expires:

```kotlin
// AuthRepository.kt
class AuthRepository(private val apiService: ApiService) {

    suspend fun refreshAccessToken(): Result<AuthResponse> {
        val refreshToken = TokenManager.getRefreshToken()

        if (refreshToken == null) {
            return Result.failure(Exception("No refresh token available"))
        }

        return try {
            val response = apiService.refreshToken(
                RefreshTokenRequest(refreshToken)
            )

            if (response.success && response.data != null) {
                // Save new tokens
                TokenManager.saveTokens(
                    response.data.accessToken,
                    response.data.refreshToken
                )
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// Enhanced Retrofit Client with Token Refresh
object RetrofitClient {
    private val authRepository by lazy { AuthRepository(apiService) }

    private val tokenInterceptor = Interceptor { chain ->
        val original = chain.request()

        // Skip auth endpoints
        if (original.url.encodedPath.contains("/auth/")) {
            return@Interceptor chain.proceed(original)
        }

        var request = original
        val accessToken = TokenManager.getAccessToken()

        if (accessToken != null) {
            request = original.newBuilder()
                .header("Authorization", "Bearer $accessToken")
                .build()
        }

        // Execute request
        var response = chain.proceed(request)

        // If unauthorized (401), try to refresh token
        if (response.code == 401 && accessToken != null) {
            response.close()

            // Attempt token refresh
            val refreshResult = runBlocking {
                authRepository.refreshAccessToken()
            }

            if (refreshResult.isSuccess) {
                // Retry with new token
                val newAccessToken = TokenManager.getAccessToken()
                request = original.newBuilder()
                    .header("Authorization", "Bearer $newAccessToken")
                    .build()
                response = chain.proceed(request)
            }
        }

        response
    }

    // ... rest of Retrofit setup
}
```

### 10. Logout Flow

Complete logout with token revocation:

```kotlin
// LogoutManager.kt
class LogoutManager(
    private val apiService: ApiService,
    private val context: Context
) {

    suspend fun logout(): Boolean {
        return try {
            val accessToken = TokenManager.getAccessToken()
            val refreshToken = TokenManager.getRefreshToken()

            if (accessToken != null && refreshToken != null) {
                // Step 1: Revoke refresh token on backend
                val response = apiService.logout(
                    "Bearer $accessToken",
                    RefreshTokenRequest(refreshToken)
                )

                if (!response.success) {
                    Log.w("LogoutManager", "Backend logout failed: ${response.message}")
                }
            }

            // Step 2: Clear local tokens
            TokenManager.clearTokens()

            // Step 3: Sign out from Firebase
            FirebaseAuth.getInstance().signOut()

            true
        } catch (e: Exception) {
            Log.e("LogoutManager", "Logout error", e)

            // Even if backend call fails, clear local data
            TokenManager.clearTokens()
            FirebaseAuth.getInstance().signOut()

            false
        }
    }
}

// Usage in Activity
class MainActivity : AppCompatActivity() {
    private fun performLogout() {
        lifecycleScope.launch {
            val logoutManager = LogoutManager(RetrofitClient.apiService, this@MainActivity)
            val success = logoutManager.logout()

            // Navigate to login screen
            startActivity(Intent(this@MainActivity, LoginActivity::class.java))
            finish()
        }
    }
}
```

### 11. Application Initialization

Initialize TokenManager in your Application class:

```kotlin
// MyApplication.kt
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TokenManager.init(this)
    }
}
```

Don't forget to register it in `AndroidManifest.xml`:

```xml
<application
    android:name=".MyApplication"
    ...>
```

### Key Points

1. **Firebase only for initial authentication**: Firebase verifies credentials and issues ID tokens
2. **All API calls use custom JWT**: After initial auth, use custom JWT tokens (NOT Firebase tokens)
3. **Access token**: 1 hour validity, used for all API requests
4. **Refresh token**: 7 days validity, used to get new access tokens
5. **Automatic token refresh**: Interceptor handles 401 errors and refreshes token automatically
6. **Secure storage**: EncryptedSharedPreferences protects tokens at rest
7. **Logout**: Revokes refresh token on backend and clears local storage

## Testing with Postman

A complete Postman collection is included: **`postman_collection.json`**

### Import Collection

1. Open Postman
2. Click **Import** button
3. Select `postman_collection.json` from project root
4. Collection will be imported with all endpoints and test scenarios

### Collection Features

- **Collection Variables**: Automatically saves tokens after authentication
- **Authentication Endpoints**: Login, refresh, logout, validate
- **User Management Endpoints**: Get, update, delete profile
- **Test Scenarios**: Complete user flows with automated tests
- **Pre-configured Tests**: Validates responses automatically

### Setup Variables

Before testing, set the Firebase ID token:

1. Authenticate a user in your Android/iOS app via Firebase
2. Get the Firebase ID token
3. In Postman, edit collection variables
4. Set `firebaseIdToken` to your Firebase ID token
5. Run "Authenticate with Firebase" request
6. All subsequent requests will use saved JWT tokens automatically

### Test Scenarios

The collection includes pre-built test scenarios:

**Scenario 1: Complete Login Flow**
- Step 1: Authenticate with Firebase
- Step 2: Get User Profile
- Step 3: Update Profile
- Step 4: Logout

**Scenario 2: Token Refresh Flow**
- Step 1: Authenticate
- Step 2: Refresh Token
- Step 3: Use New Token

Run these scenarios using Postman's Collection Runner for automated testing.

## Database Setup

### Using the SQL Schema

A complete PostgreSQL schema file is included: **`src/main/resources/schema.sql`**

### Create Database

```bash
# Connect to PostgreSQL
psql -U postgres

# Create database
CREATE DATABASE onescale_auth;

# Connect to database
\c onescale_auth

# Run schema file
\i src/main/resources/schema.sql

# Verify tables
\dt
```

### Schema Features

- **users table**: Stores user accounts from Firebase
- **refresh_tokens table**: Stores JWT refresh tokens with revocation support
- **Indexes**: Optimized for common query patterns
- **Foreign Keys**: Ensures referential integrity
- **Triggers**: Automatic timestamp updates
- **Comments**: Inline documentation for all tables and columns

### Sample Queries

The schema file includes commented sample queries for:
- Finding users by Firebase UID, email, or mobile
- Getting valid refresh tokens
- Revoking all tokens (logout from all devices)
- Cleaning up expired tokens

## Deployment

### Production Checklist

1. **Generate Secure JWT Secret**:
```bash
openssl rand -base64 32
```

2. **Store Firebase Service Account Securely**:
```bash
# On server
mkdir -p /etc/onescale/firebase
chmod 700 /etc/onescale/firebase

# Upload service account JSON
scp firebase-service-account.json server:/etc/onescale/firebase/
ssh server "chmod 600 /etc/onescale/firebase/firebase-service-account.json"
```

3. **Set Environment Variables**:
```bash
export FIREBASE_PROJECT_ID=your-project-id
export FIREBASE_CONFIG_PATH=/etc/onescale/firebase/firebase-service-account.json
export JWT_SECRET=your-generated-secret
export DB_PASSWORD=strong-password
```

4. **Use HTTPS**: Always use HTTPS in production

5. **Configure CORS**: Update `SecurityConfig.java` to whitelist specific origins

6. **Enable Database SSL**: Configure PostgreSQL SSL connection

## Troubleshooting

### Firebase Token Verification Failed

**Error**: `Invalid Firebase token` or `Token has expired`

**Solutions**:
- Ensure Firebase service account JSON is correct
- Check `FIREBASE_PROJECT_ID` matches your Firebase project
- Verify Android app's Firebase configuration
- Firebase ID tokens expire after 1 hour - get a fresh token

### Service Account Not Found

**Error**: `Failed to initialize Firebase Admin SDK`

**Solutions**:
- Check file path in `FIREBASE_CONFIG_PATH`
- Verify file permissions (should be readable)
- For classpath loading, ensure `firebase-service-account.json` is in `src/main/resources/`

### User Not Created in Database

**Error**: `User not found` after successful Firebase authentication

**Solutions**:
- Check database connection
- Verify `firebase_uid` column exists in `users` table
- Check application logs for database errors
- Ensure `ddl-auto` is set to `validate` or `update`

### Android App Can't Connect

**Error**: Network timeout or connection refused

**Solutions**:
- Check backend service is running
- Verify `BASE_URL` in Android app points to correct address
- For local testing, use `10.0.2.2:8080` for Android emulator
- Check firewall rules

## FAQ

**Q: Do I need to verify passwords in the backend?**
A: No! Firebase handles all password verification. Your backend only verifies the Firebase ID token.

**Q: How do I reset user passwords?**
A: Use Firebase's password reset in your Android app:
```kotlin
auth.sendPasswordResetEmail(email)
```

**Q: Can I use this with iOS?**
A: Yes! The backend works with any client. Just send Firebase ID tokens from iOS.

**Q: Where are passwords stored?**
A: In Firebase (Google's infrastructure). Your database only stores Firebase UIDs and user metadata.

**Q: How do I disable a user?**
A: Update `is_active` column in database. The validation endpoint checks this field.

## Support

For issues and questions:
- Check Firebase Console for authentication issues
- Review application logs: `logs/application.log`
- Check database connectivity
- Verify environment variables are set correctly

## License

This project is licensed under the MIT License.
