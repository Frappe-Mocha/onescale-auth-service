package com.onescale.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FirebaseAuthDto {

    @NotBlank(message = "Firebase ID token is required")
    private String idToken;

    // Optional: Additional user data that's not in Firebase token
    private String fullName;
}
