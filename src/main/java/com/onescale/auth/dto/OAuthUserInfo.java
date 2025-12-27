package com.onescale.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OAuthUserInfo {
    private String providerId;
    private String email;
    private String fullName;
    private String profilePictureUrl;
    private boolean emailVerified;
}
