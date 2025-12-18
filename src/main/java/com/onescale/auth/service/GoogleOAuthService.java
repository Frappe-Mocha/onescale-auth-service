package com.onescale.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.onescale.auth.exception.AuthException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@Slf4j
public class GoogleOAuthService {

    @Value("${google.oauth.client-id}")
    private String clientId;

    private final GoogleIdTokenVerifier verifier;

    public GoogleOAuthService(@Value("${google.oauth.client-id}") String clientId) {
        this.clientId = clientId;
        this.verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                new GsonFactory()
        )
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    public GoogleIdToken.Payload verifyIdToken(String idTokenString) {
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);

            if (idToken == null) {
                log.error("Invalid Google ID token");
                throw new AuthException("Invalid Google ID token");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();

            // Verify token is for our client
            if (!clientId.equals(payload.getAudience())) {
                log.error("Token audience mismatch");
                throw new AuthException("Invalid token audience");
            }

            log.info("Successfully verified Google ID token for user: {}", payload.getEmail());
            return payload;

        } catch (Exception e) {
            log.error("Failed to verify Google ID token", e);
            throw new AuthException("Failed to verify Google ID token: " + e.getMessage());
        }
    }

    public String getEmail(GoogleIdToken.Payload payload) {
        return payload.getEmail();
    }

    public String getGoogleId(GoogleIdToken.Payload payload) {
        return payload.getSubject();
    }

    public String getFullName(GoogleIdToken.Payload payload) {
        return (String) payload.get("name");
    }

    public String getProfilePictureUrl(GoogleIdToken.Payload payload) {
        return (String) payload.get("picture");
    }

    public Boolean isEmailVerified(GoogleIdToken.Payload payload) {
        return payload.getEmailVerified();
    }
}
