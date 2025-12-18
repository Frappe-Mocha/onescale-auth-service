# OneScale Authentication Microservice

A complete authentication microservice built with Spring Boot for trading applications. Supports email/mobile OTP authentication via Twilio and Google OAuth (OIDC), issuing JWT tokens for secure API access.

## Features

- **Email OTP Authentication**: 6-digit OTP verification via Twilio Verify API
- **Mobile OTP Authentication**: SMS-based OTP verification via Twilio Verify API
- **Google OAuth (OIDC)**: ID Token verification for Google Sign-In
- **JWT Token Management**: Access tokens (15 min) and Refresh tokens (30 days)
- **Token Refresh**: Seamless token refresh without re-authentication
- **Token Revocation**: Individual and bulk token revocation
- **Rate Limiting**: Redis-based rate limiting for OTP requests
- **Security**: Spring Security 6 with JWT authentication filter
- **Database**: PostgreSQL with JPA/Hibernate
- **Caching**: Redis for OTP and rate limiting

## Technology Stack

- **Framework**: Spring Boot 3.2.5
- **Security**: Spring Security 6.x
- **Java**: 17+
- **Database**: PostgreSQL 15+
- **Cache**: Redis 7+
- **OTP Provider**: Twilio Verify API
- **JWT Library**: jjwt 0.12.5
- **Build Tool**: Maven

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- Docker & Docker Compose (for local development)
- Twilio account with Verify API enabled
- Google OAuth 2.0 Client ID

## Quick Start

### 1. Clone and Navigate

```bash
cd onescale-auth-service
```

### 2. Configure Environment Variables

Copy the example environment file:

```bash
cp .env.example .env
```

Edit `.env` and add your credentials:

```properties
# Twilio Configuration
TWILIO_ACCOUNT_SID=your-twilio-account-sid
TWILIO_AUTH_TOKEN=your-twilio-auth-token
TWILIO_VERIFY_SERVICE_SID=your-twilio-verify-service-sid

# Google OAuth Configuration
GOOGLE_OAUTH_CLIENT_ID=your-google-client-id.apps.googleusercontent.com

# JWT Secret (CHANGE IN PRODUCTION!)
JWT_SECRET=your-secure-256-bit-secret-key-min-32-chars
```

### 3. Start Infrastructure

Start PostgreSQL and Redis using Docker Compose:

```bash
docker-compose up -d
```

Verify services are running:

```bash
docker-compose ps
```

### 4. Build and Run

Build the application:

```bash
./mvnw clean package
```

Run the application:

```bash
./mvnw spring-boot:run
```

Or run with a specific profile:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The service will start on `http://localhost:8080`

### 5. Verify Health

```bash
curl http://localhost:8080/api/v1/auth/health
```

## API Documentation

### Base URL

```
http://localhost:8080/api/v1/auth
```

### Authentication Endpoints

#### 1. Send Email OTP

Send a 6-digit OTP to an email address.

**Endpoint**: `POST /otp/email/send`

**Request Body**:
```json
{
  "email": "user@example.com"
}
```

**Response**:
```json
{
  "success": true,
  "message": "OTP sent to email successfully",
  "data": null
}
```

**cURL Example**:
```bash
curl -X POST http://localhost:8080/api/v1/auth/otp/email/send \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com"}'
```

---

#### 2. Verify Email OTP

Verify the OTP and receive JWT tokens.

**Endpoint**: `POST /otp/email/verify`

**Request Body**:
```json
{
  "email": "user@example.com",
  "otpCode": "123456"
}
```

**Response**:
```json
{
  "success": true,
  "message": "Email verified successfully",
  "data": {
    "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "token_type": "Bearer",
    "expires_in": 900,
    "user": {
      "user_id": 1,
      "email": "user@example.com",
      "mobile_number": null,
      "full_name": null,
      "profile_picture_url": null,
      "is_email_verified": true,
      "is_mobile_verified": false
    }
  }
}
```

**cURL Example**:
```bash
curl -X POST http://localhost:8080/api/v1/auth/otp/email/verify \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","otpCode":"123456"}'
```

---

#### 3. Send Mobile OTP

Send a 6-digit OTP to a mobile number via SMS.

**Endpoint**: `POST /otp/mobile/send`

**Request Body**:
```json
{
  "mobileNumber": "+1234567890"
}
```

**Response**:
```json
{
  "success": true,
  "message": "OTP sent to mobile number successfully",
  "data": null
}
```

**cURL Example**:
```bash
curl -X POST http://localhost:8080/api/v1/auth/otp/mobile/send \
  -H "Content-Type: application/json" \
  -d '{"mobileNumber":"+1234567890"}'
```

---

#### 4. Verify Mobile OTP

Verify the mobile OTP and receive JWT tokens.

**Endpoint**: `POST /otp/mobile/verify`

**Request Body**:
```json
{
  "mobileNumber": "+1234567890",
  "otpCode": "123456"
}
```

**Response**:
```json
{
  "success": true,
  "message": "Mobile number verified successfully",
  "data": {
    "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "token_type": "Bearer",
    "expires_in": 900,
    "user": {
      "user_id": 2,
      "email": null,
      "mobile_number": "+1234567890",
      "full_name": null,
      "profile_picture_url": null,
      "is_email_verified": false,
      "is_mobile_verified": true
    }
  }
}
```

**cURL Example**:
```bash
curl -X POST http://localhost:8080/api/v1/auth/otp/mobile/verify \
  -H "Content-Type: application/json" \
  -d '{"mobileNumber":"+1234567890","otpCode":"123456"}'
```

---

#### 5. Google OAuth Authentication

Authenticate using Google ID Token.

**Endpoint**: `POST /google`

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
  "message": "Google authentication successful",
  "data": {
    "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "token_type": "Bearer",
    "expires_in": 900,
    "user": {
      "user_id": 3,
      "email": "user@gmail.com",
      "mobile_number": null,
      "full_name": "John Doe",
      "profile_picture_url": "https://lh3.googleusercontent.com/...",
      "is_email_verified": true,
      "is_mobile_verified": false
    }
  }
}
```

**cURL Example**:
```bash
curl -X POST http://localhost:8080/api/v1/auth/google \
  -H "Content-Type: application/json" \
  -d '{"idToken":"eyJhbGciOiJSUzI1NiIsImtpZCI6IjY4MTE..."}'
```

---

#### 6. Refresh Access Token

Get a new access token using a refresh token.

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
      "mobile_number": null,
      "full_name": null,
      "profile_picture_url": null,
      "is_email_verified": true,
      "is_mobile_verified": false
    }
  }
}
```

**cURL Example**:
```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."}'
```

---

#### 7. Logout

Revoke a refresh token.

**Endpoint**: `POST /logout` (Requires Authentication)

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

**cURL Example**:
```bash
curl -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -d '{"refreshToken":"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."}'
```

---

## JWT Token Structure

### Access Token Claims

```json
{
  "sub": "1",
  "user_id": 1,
  "email": "user@example.com",
  "mobile_number": null,
  "token_type": "access",
  "iss": "onescale-auth-service",
  "iat": 1234567890,
  "exp": 1234568790
}
```

### Refresh Token Claims

```json
{
  "sub": "1",
  "user_id": 1,
  "token_type": "refresh",
  "iss": "onescale-auth-service",
  "iat": 1234567890,
  "exp": 1237159890
}
```

## Using JWT Tokens

Include the access token in the `Authorization` header for protected endpoints:

```bash
curl http://localhost:8080/api/v1/protected-endpoint \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

## Error Responses

All errors follow a consistent format:

```json
{
  "success": false,
  "message": "Error description",
  "data": null
}
```

### Common Error Codes

| HTTP Status | Error | Description |
|-------------|-------|-------------|
| 400 | Bad Request | Invalid request body or validation failure |
| 401 | Unauthorized | Invalid or expired token |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Server error |

## Rate Limiting

- **OTP Requests**: Maximum 5 requests per hour per email/mobile
- Rate limits are enforced using Redis
- Exceeded limits return HTTP 429

## Security Best Practices

### For Production Deployment

1. **Change JWT Secret**: Use a strong, randomly generated 256-bit secret
   ```bash
   openssl rand -base64 32
   ```

2. **Use HTTPS**: Always use HTTPS in production

3. **Secure Environment Variables**: Never commit `.env` to version control

4. **Database Security**: Use strong database passwords and restrict access

5. **CORS Configuration**: Update CORS settings in `SecurityConfig.java` to whitelist specific origins

6. **Rate Limiting**: Adjust rate limits based on your requirements

## Database Schema

### Users Table

| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL | Primary key |
| email | VARCHAR(255) | User email (unique, nullable) |
| mobile_number | VARCHAR(20) | Mobile number in E.164 format (unique, nullable) |
| google_id | VARCHAR(255) | Google account ID (unique, nullable) |
| full_name | VARCHAR(255) | User's full name |
| profile_picture_url | VARCHAR(500) | Profile picture URL |
| is_email_verified | BOOLEAN | Email verification status |
| is_mobile_verified | BOOLEAN | Mobile verification status |
| is_active | BOOLEAN | Account active status |
| last_login_at | TIMESTAMP | Last login timestamp |
| created_at | TIMESTAMP | Account creation timestamp |
| updated_at | TIMESTAMP | Last update timestamp |

### Refresh Tokens Table

| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL | Primary key |
| token | VARCHAR(1000) | JWT refresh token (unique) |
| user_id | BIGINT | Foreign key to users table |
| expires_at | TIMESTAMP | Token expiration timestamp |
| is_revoked | BOOLEAN | Token revocation status |
| revoked_at | TIMESTAMP | Revocation timestamp |
| created_at | TIMESTAMP | Token creation timestamp |

## Project Structure

```
onescale-auth-service/
├── src/
│   ├── main/
│   │   ├── java/com/onescale/auth/
│   │   │   ├── config/          # Spring configuration classes
│   │   │   ├── controller/      # REST controllers
│   │   │   ├── dto/             # Data Transfer Objects
│   │   │   ├── entity/          # JPA entities
│   │   │   ├── exception/       # Custom exceptions & handlers
│   │   │   ├── filter/          # Security filters
│   │   │   ├── repository/      # JPA repositories
│   │   │   ├── service/         # Business logic services
│   │   │   ├── util/            # Utility classes
│   │   │   └── AuthServiceApplication.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       └── db/
│   │           └── schema.sql
│   └── test/                    # Test classes
├── docker-compose.yml
├── .env.example
├── pom.xml
└── README.md
```

## Development

### Running Tests

```bash
./mvnw test
```

### Building for Production

```bash
./mvnw clean package -DskipTests
```

Run the JAR:

```bash
java -jar target/auth-service-1.0.0.jar
```

### Using Different Profiles

Development:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Production:
```bash
java -jar target/auth-service-1.0.0.jar --spring.profiles.active=prod
```

## Twilio Setup

1. Sign up at [Twilio](https://www.twilio.com/)
2. Create a Verify Service:
   - Go to Verify > Services
   - Create a new service
   - Copy the Service SID
3. Get your Account SID and Auth Token from the console
4. Add credentials to `.env`

## Google OAuth Setup

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select existing
3. Enable Google+ API
4. Create OAuth 2.0 credentials:
   - Application type: Android/iOS/Web
   - Add authorized origins
5. Copy the Client ID to `.env`

### Android Integration

For Android apps using Google Sign-In:

```kotlin
// Get the ID Token from Google Sign-In
val idToken = googleSignInAccount.idToken

// Send to backend
authService.authenticateWithGoogle(idToken)
```

## Monitoring & Logging

Logs are configured in `application.yml`:

```yaml
logging:
  level:
    com.onescale.auth: INFO
    org.springframework.security: INFO
```

View logs:
```bash
tail -f logs/application.log
```

## Troubleshooting

### Common Issues

**1. Database Connection Failed**
- Ensure PostgreSQL is running: `docker-compose ps`
- Check connection details in `.env`

**2. Redis Connection Failed**
- Ensure Redis is running: `docker-compose ps`
- Check Redis host and port in `.env`

**3. Twilio OTP Not Sending**
- Verify Twilio credentials
- Check Twilio console for error logs
- Ensure phone number is in E.164 format

**4. Google OAuth Verification Failed**
- Verify Google Client ID
- Ensure ID token is valid and not expired
- Check that the token audience matches your Client ID

**5. JWT Token Invalid**
- Ensure JWT secret is at least 32 characters
- Check token expiration
- Verify token format (Bearer <token>)

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

This project is licensed under the MIT License.

## Support

For issues and questions:
- Create an issue in the repository
- Email: support@onescale.com

## Changelog

### Version 1.0.0 (2024)
- Initial release
- Email OTP authentication
- Mobile OTP authentication
- Google OAuth (OIDC) authentication
- JWT token management
- Token refresh and revocation
- Rate limiting
- Docker support
