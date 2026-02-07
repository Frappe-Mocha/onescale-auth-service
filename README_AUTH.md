# OneScale Authentication Service

**Version:** 2.0
**Base URL:** `http://localhost:8080/api/v1/auth`

---

## Quick Start

### What This Service Does

- ✅ Password-based authentication (email/mobile + password)
- ✅ OAuth authentication (Google, Facebook via Firebase)
- ✅ JWT access + refresh tokens
- ✅ API Gateway validation

---

## Two Authentication Methods

### Method 1: Password Authentication
**Use when:** User signs up/logs in with email and password

```
Register → Login → Get JWT Tokens → Use tokens in API calls
```

### Method 2: OAuth (Google/Facebook)
**Use when:** User signs in with Google or Facebook

```
Firebase Auth → Register Backend → Get JWT Tokens → Use tokens in API calls
```

---

## Core API Endpoints

### 1. Register User

**POST** `/register`

```json
// Request
{
  "fullName": "John Doe",
  "email": "john@example.com",
  "password": "SecurePass123",        // Required for PASSWORD
  "deviceId": "android-device-123",
  "provider": "PASSWORD"              // PASSWORD | GOOGLE | FACEBOOK
}

// Response (201)
{
  "success": true,
  "data": {
    "client_id": "uuid-generated",    // Save this!
    "email": "john@example.com",
    "full_name": "John Doe",
    ...
  }
}
```

### 2. Login (Get JWT Tokens)

**POST** `/login`

```json
// Request
{
  "email": "john@example.com",
  "password": "SecurePass123",
  "deviceId": "android-device-123"
}

// Response (200)
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGc...",      // Use in API calls
    "refreshToken": "eyJhbGc...",     // Use to get new access token
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "user": { ... }
  }
}
```

### 3. Refresh Token

**POST** `/refresh`

```json
// Request
{
  "refreshToken": "eyJhbGc..."
}

// Response (200)
{
  "success": true,
  "data": {
    "accessToken": "new-token...",
    "refreshToken": "same-refresh-token...",
    ...
  }
}
```

### 4. Logout

**POST** `/logout`
**Header:** `Authorization: Bearer <accessToken>`

```json
// Request
{
  "refreshToken": "eyJhbGc..."
}

// Response (200)
{
  "success": true,
  "message": "Logged out successfully"
}
```

### 5. Validate Token (For API Gateway)

**GET** `/validate`
**Header:** `Authorization: Bearer <accessToken>`

```json
// Response (200)
{
  "success": true,
  "data": {
    "is_valid": true,
    "client_id": "uuid...",
    "email": "john@example.com",
    "expires_at": 1234567890
  }
}
```

### 6. Get User Profile

**GET** `/users/me`
**Header:** `Authorization: Bearer <accessToken>`

```json
// Response (200)
{
  "success": true,
  "data": {
    "client_id": "uuid...",
    "email": "john@example.com",
    "full_name": "John Doe",
    ...
  }
}
```

---

## Complete Flows

### Flow A: Password Registration & Login

```
1. User fills registration form
   ↓
2. POST /register
   { email, password, fullName, deviceId, provider: "PASSWORD" }
   ↓
3. Save client_id from response
   ↓
4. POST /login
   { email, password, deviceId }
   ↓
5. Save accessToken and refreshToken
   ↓
6. Use accessToken in all API calls:
   Header: Authorization: Bearer <accessToken>
```

### Flow B: Google OAuth

```
1. User clicks "Sign in with Google"
   ↓
2. Firebase Google Sign-In
   ↓
3. Get user info from Firebase
   { email, displayName, photoURL }
   ↓
4. POST /register (backend)
   { email, fullName, provider: "GOOGLE", deviceId, profilePictureUrl }
   ↓
5. POST /token (optional, to get JWT)
   { clientId, deviceId }
   ↓
6. Save tokens and use in API calls
```

### Flow C: Token Refresh (Auto-handled)

```
1. API call fails with 401 Unauthorized
   ↓
2. Call POST /refresh with refreshToken
   ↓
3. Get new accessToken
   ↓
4. Retry original API call with new token
```

---

## Android Integration (Essential Code)

### 1. Store Tokens Securely

```kotlin
class TokenManager(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(...)

    fun saveTokens(access: String, refresh: String) {
        prefs.edit {
            putString("access_token", access)
            putString("refresh_token", refresh)
        }
    }

    fun getAccessToken(): String? = prefs.getString("access_token", null)
    fun getRefreshToken(): String? = prefs.getString("refresh_token", null)
    fun clearTokens() { prefs.edit { clear() } }
}
```

### 2. Add Auth to API Calls

```kotlin
// Add interceptor to Retrofit
class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Authorization", "Bearer ${tokenManager.getAccessToken()}")
            .build()

        val response = chain.proceed(request)

        // Auto-refresh on 401
        if (response.code == 401) {
            // Call /refresh and retry...
        }

        return response
    }
}
```

### 3. Login Function

```kotlin
suspend fun login(email: String, password: String) {
    val response = authApi.login(
        LoginRequest(
            email = email,
            password = password,
            deviceId = getDeviceId()
        )
    )

    if (response.success) {
        tokenManager.saveTokens(
            response.data.accessToken,
            response.data.refreshToken
        )
    }
}
```

### 4. Google Sign-In

```kotlin
// Step 1: Google Sign-In
val account = GoogleSignIn.getSignedInAccountFromIntent(data)

// Step 2: Firebase Auth
FirebaseAuth.getInstance()
    .signInWithCredential(GoogleAuthProvider.getCredential(account.idToken, null))

// Step 3: Register with backend
authApi.register(
    RegisterRequest(
        fullName = firebaseUser.displayName,
        email = firebaseUser.email,
        provider = "GOOGLE",
        deviceId = getDeviceId()
    )
)
```

---

## Database Schema (Essential)

```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    client_id VARCHAR(36) UNIQUE NOT NULL,    -- External UUID
    device_id VARCHAR(255) NOT NULL,
    provider VARCHAR(20) NOT NULL,            -- PASSWORD | GOOGLE | FACEBOOK
    password_hash VARCHAR(255),               -- BCrypt, null for OAuth
    email VARCHAR(255) UNIQUE,
    mobile_number VARCHAR(20) UNIQUE,
    full_name VARCHAR(255),
    profile_picture_url VARCHAR(500),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(1000) UNIQUE NOT NULL,
    user_id BIGINT REFERENCES users(id),
    expires_at TIMESTAMP NOT NULL,
    is_revoked BOOLEAN DEFAULT FALSE
);
```

---

## JWT Token Structure

### Access Token (1 hour expiry)
```json
{
  "sub": "client-id-uuid",
  "email": "user@example.com",
  "fullName": "John Doe",
  "tokenType": "access",
  "exp": 1234567890
}
```

### Refresh Token (7 days expiry)
```json
{
  "sub": "client-id-uuid",
  "tokenType": "refresh",
  "exp": 1234567890
}
```

---

## Security Best Practices

### ✅ DO
- Store tokens in `EncryptedSharedPreferences` (Android)
- Use HTTPS in production
- Implement auto token refresh
- Clear tokens on logout
- Validate password strength (min 8 chars)

### ❌ DON'T
- Store tokens in plain SharedPreferences
- Log tokens in console
- Share tokens between apps
- Use HTTP in production

---

## Common Error Codes

| Code | Error | What To Do |
|------|-------|------------|
| 400 | Bad Request | Fix your request data |
| 401 | Unauthorized | Refresh token or re-login |
| 404 | Not Found | User doesn't exist |
| 500 | Server Error | Retry with delay |

---

## Testing with cURL

```bash
# 1. Register
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "Test User",
    "email": "test@example.com",
    "password": "Test1234",
    "deviceId": "test-device",
    "provider": "PASSWORD"
  }'

# 2. Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "Test1234",
    "deviceId": "test-device"
  }'

# 3. Get Profile (use accessToken from login)
curl -X GET http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer <accessToken>"
```

---

## Quick Reference

### Public Endpoints (No token needed)
- `POST /register` - Create account
- `POST /login` - Get tokens
- `POST /refresh` - Refresh access token
- `GET /validate` - Validate token (gateway)
- `GET /health` - Health check

### Protected Endpoints (Token required)
- `POST /logout` - Revoke token
- `GET /users/me` - Get profile
- `PUT /users/me` - Update profile
- `DELETE /users/me` - Delete account

---

## Configuration

### application.yml
```yaml
jwt:
  secret: your-256-bit-secret
  access-token-expiration: 3600000   # 1 hour
  refresh-token-expiration: 604800000 # 7 days

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/onescale_auth
```

---

## Next Steps

1. **Setup Database:** Run `schema.sql`
2. **Configure:** Set JWT secret in `application.yml`
3. **Start Service:** `./mvnw spring-boot:run`
4. **Test:** Use cURL commands above
5. **Integrate:** Add Android code to your app

---

## Need More Details?

- Full documentation: `AUTH_SERVICE_GUIDE.md`
- API Collection: `postman_collection.json`
- Database: `src/main/resources/schema.sql`

---

**Questions?** Check the FAQ in `AUTH_SERVICE_GUIDE.md` or create an issue.
