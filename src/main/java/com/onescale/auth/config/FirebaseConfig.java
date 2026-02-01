package com.onescale.auth.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${firebase.config.path:}")
    private String firebaseConfigPath;

    @Value("${firebase.project-id}")
    private String projectId;

    @PostConstruct
    public void initialize() {
        try {
            FirebaseOptions options;

            if (firebaseConfigPath != null && !firebaseConfigPath.isEmpty()) {
                // Load from file path (for production)
                log.info("Loading Firebase config from path: {}", firebaseConfigPath);
                InputStream serviceAccount = new FileInputStream(firebaseConfigPath);

                options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();
            } else {
                // Load from classpath (for development)
                log.info("Loading Firebase config from classpath: firebase-service-account.json");
                InputStream serviceAccount = new ClassPathResource("firebase-service-account.json").getInputStream();

                options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();
            }

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                log.info("Firebase Admin SDK initialized successfully for project: {}", projectId);
            }

        } catch (IOException e) {
            log.error("Failed to initialize Firebase Admin SDK", e);
            throw new RuntimeException("Failed to initialize Firebase Admin SDK: " + e.getMessage());
        }
    }
}
