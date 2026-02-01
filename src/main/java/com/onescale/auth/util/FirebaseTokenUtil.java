package com.onescale.auth.util;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.onescale.auth.exception.InvalidTokenException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FirebaseTokenUtil {

    /**
     * Verifies Firebase ID token and returns decoded token
     *
     * @param idToken Firebase ID token from client
     * @return Decoded Firebase token with user info
     * @throws InvalidTokenException if token is invalid or expired
     */
    public FirebaseToken verifyIdToken(String idToken) {
        try {
            // Firebase Admin SDK does the following:
            // 1. Checks token signature (ensures it's from Firebase, not fake)
            // 2. Checks token expiration (tokens expire after 1 hour)
            // 3. Checks token issuer (iss claim)
            // 4. Checks token audience (aud claim)
            // 5. Verifies it hasn't been tampered with

            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);

            log.debug("Firebase token verified successfully for UID: {}", decodedToken.getUid());
            return decodedToken;

        } catch (FirebaseAuthException e) {
            log.error("Firebase token verification failed: {}", e.getMessage());

            // Map Firebase errors to our exception
            String errorCode = e.getErrorCode();
            switch (errorCode) {
                case "EXPIRED_ID_TOKEN":
                    throw new InvalidTokenException("Firebase token has expired");
                case "INVALID_ID_TOKEN":
                    throw new InvalidTokenException("Invalid Firebase token");
                case "USER_DISABLED":
                    throw new InvalidTokenException("User account has been disabled");
                default:
                    throw new InvalidTokenException("Firebase token verification failed: " + e.getMessage());
            }
        } catch (Exception e) {
            log.error("Unexpected error during Firebase token verification", e);
            throw new InvalidTokenException("Token verification failed: " + e.getMessage());
        }
    }

    /**
     * Extract Firebase UID from token
     */
    public String getFirebaseUid(String idToken) {
        FirebaseToken token = verifyIdToken(idToken);
        return token.getUid();
    }

    /**
     * Extract email from token
     */
    public String getEmail(FirebaseToken token) {
        return token.getEmail();
    }

    /**
     * Check if email is verified in Firebase
     */
    public boolean isEmailVerified(FirebaseToken token) {
        return token.isEmailVerified();
    }

    /**
     * Extract phone number from token
     */
    public String getPhoneNumber(FirebaseToken token) {
        Object phone = token.getClaims().get("phone_number");
        return phone != null ? phone.toString() : null;
    }

    /**
     * Extract display name from token
     */
    public String getDisplayName(FirebaseToken token) {
        Object name = token.getClaims().get("name");
        return name != null ? name.toString() : null;
    }

    /**
     * Extract profile picture URL from token
     */
    public String getProfilePicture(FirebaseToken token) {
        Object picture = token.getClaims().get("picture");
        return picture != null ? picture.toString() : null;
    }
}
