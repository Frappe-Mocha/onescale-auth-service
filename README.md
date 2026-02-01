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

- **Firebase Email/Password Authentication**: Users sign up and sign in with Firebase
- **Firebase Phone Authentication**: SMS-based OTP verification via Firebase
- **Automatic User Creation**: Creates users in database on first Firebase authentication
- **JWT Token Management**: Issues access tokens (15 min) and refresh tokens (30 days)
- **Token Refresh**: Seamless token refresh without re-authentication
- **Token Revocation**: Individual and bulk token revocation
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

1. **Firebase Handles Authentication**:
   - Firebase stores passwords securely
   - Firebase sends verification emails/SMS
   - Firebase validates credentials
   - Firebase issues ID tokens

2. **Backend Verifies & Manages**:
   - Verifies Firebase ID token is genuine
   - Creates/updates user in database
   - Issues JWT tokens for API access
   - Manages user sessions

3. **Security**:
   - Firebase ID tokens expire after 1 hour
   - Backend verifies tokens using Firebase public keys
   - Tokens are cryptographically signed (can't be faked)
   - JWTs provide stateless authentication for APIs

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
    "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "token_type": "Bearer",
    "expires_in": 900,
    "user": {
      "user_id": 1,
      "email": "user@example.com",
      "mobile_number": null,
      "full_name": "John Doe",
      "profile_picture_url": null,
      "is_email_verified": true,
      "is_mobile_verified": false
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
    "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "token_type": "Bearer",
    "expires_in": 900,
    "user": {
      "user_id": 1,
      "email": "user@example.com",
      ...
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

### 4. Validate Firebase Token

Validate Firebase ID token (for other microservices).

**Endpoint**: `POST /firebase/validate`

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
  "message": "Token is valid",
  "data": {
    "is_valid": true,
    "user_id": 1,
    "email": "user@example.com",
    "mobile_number": null,
    "token_type": "firebase",
    "issued_at": 1706745600,
    "expires_at": 1706749200
  }
}
```

## Integration with Android

### Gradle Dependencies

```gradle
dependencies {
    // Firebase Authentication
    implementation platform('com.google.firebase:firebase-bom:32.7.1')
    implementation 'com.google.firebase:firebase-auth'

    // For Phone Auth
    implementation 'com.google.firebase:firebase-auth-ktx'

    // Retrofit for API calls
    implementation 'com.squareup.retrofit2:retrofit:2.9.0'
    implementation 'com.squareup.retrofit2:converter-gson:2.9.0'
}
```

### Email/Password Signup

```kotlin
// 1. Sign up with Firebase
auth.createUserWithEmailAndPassword(email, password)
    .addOnCompleteListener { task ->
        if (task.isSuccessful) {
            val user = task.result?.user

            // 2. Send email verification
            user?.sendEmailVerification()
                ?.addOnCompleteListener {
                    Toast.makeText(this,
                        "Verification email sent",
                        Toast.LENGTH_LONG).show()
                }
        }
    }
```

### Email/Password Signin

```kotlin
// 1. Sign in with Firebase
auth.signInWithEmailAndPassword(email, password)
    .addOnCompleteListener { task ->
        if (task.isSuccessful) {
            val user = task.result?.user

            // 2. Check email verification
            if (user?.isEmailVerified == true) {
                authenticateWithBackend(user)
            } else {
                Toast.makeText(this,
                    "Please verify your email first",
                    Toast.LENGTH_LONG).show()
            }
        }
    }
```

### Send to Backend

```kotlin
private fun authenticateWithBackend(firebaseUser: FirebaseUser) {
    // 1. Get Firebase ID token
    firebaseUser.getIdToken(true)
        .addOnSuccessListener { result ->
            val idToken = result.token

            // 2. Send to backend
            val request = FirebaseAuthRequest(idToken)
            apiService.authenticateWithFirebase(request)
                .enqueue(object : Callback<ApiResponse<AuthResponse>> {
                    override fun onResponse(call: Call, response: Response) {
                        if (response.isSuccessful) {
                            val authResponse = response.body()?.data

                            // Save tokens
                            saveTokens(
                                authResponse?.accessToken,
                                authResponse?.refreshToken
                            )

                            // Navigate to main app
                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                            finish()
                        }
                    }

                    override fun onFailure(call: Call, t: Throwable) {
                        Toast.makeText(this@LoginActivity,
                            "Network error",
                            Toast.LENGTH_SHORT).show()
                    }
                })
        }
}
```

### Phone Authentication

```kotlin
// 1. Start phone verification
val options = PhoneAuthOptions.newBuilder(auth)
    .setPhoneNumber(phoneNumber)  // "+919876543210"
    .setTimeout(60L, TimeUnit.SECONDS)
    .setActivity(this)
    .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

        override fun onCodeSent(
            verificationId: String,
            token: PhoneAuthProvider.ForceResendingToken
        ) {
            // Firebase sent SMS
            this@PhoneAuthActivity.verificationId = verificationId
            showCodeInputDialog()
        }

        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            // Auto-verification succeeded
            signInWithPhoneAuthCredential(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            Toast.makeText(this@PhoneAuthActivity,
                "Verification failed",
                Toast.LENGTH_SHORT).show()
        }
    })
    .build()

PhoneAuthProvider.verifyPhoneNumber(options)

// 2. User enters code
private fun verifyCode(code: String) {
    val credential = PhoneAuthProvider.getCredential(verificationId, code)
    signInWithPhoneAuthCredential(credential)
}

// 3. Sign in with credential
private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
    auth.signInWithCredential(credential)
        .addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val user = task.result?.user
                authenticateWithBackend(user)
            }
        }
}
```

### API Service Interface

```kotlin
interface ApiService {
    @POST("/api/v1/auth/firebase/authenticate")
    fun authenticateWithFirebase(
        @Body request: FirebaseAuthRequest
    ): Call<ApiResponse<AuthResponse>>

    @POST("/api/v1/auth/refresh")
    fun refreshToken(
        @Body request: RefreshTokenRequest
    ): Call<ApiResponse<AuthResponse>>

    @POST("/api/v1/auth/logout")
    fun logout(
        @Header("Authorization") token: String,
        @Body request: RefreshTokenRequest
    ): Call<ApiResponse<Unit>>
}

data class FirebaseAuthRequest(val idToken: String)
data class RefreshTokenRequest(val refreshToken: String)
```

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
