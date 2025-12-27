package com.onescale.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.onescale.auth.config.GoogleOAuthProperties;
import com.onescale.auth.dto.OAuthUserInfo;
import com.onescale.auth.exception.AuthException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@Slf4j
public class GoogleOAuthService {

    private final GoogleIdTokenVerifier verifier;

    public GoogleOAuthService(GoogleOAuthProperties properties) {
        this.verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                new GsonFactory()
        )
                .setAudience(Collections.singletonList(properties.getClientId()))
                .build();
    }

    public OAuthUserInfo verifyIdToken(String idTokenString) {
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);

            if (idToken == null) {
                log.error("Invalid Google ID token");
                throw new AuthException("Invalid Google ID token");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();

            log.info("Successfully verified Google ID token for user: {}", payload.getEmail());

            return OAuthUserInfo.builder()
                    .providerId(payload.getSubject())
                    .email(payload.getEmail())
                    .fullName((String) payload.get("name"))
                    .profilePictureUrl((String) payload.get("picture"))
                    .emailVerified(payload.getEmailVerified())
                    .build();

        } catch (Exception e) {
            log.error("Failed to verify Google ID token", e);
            throw new AuthException("Failed to verify Google ID token: " + e.getMessage());
        }
    }
}
