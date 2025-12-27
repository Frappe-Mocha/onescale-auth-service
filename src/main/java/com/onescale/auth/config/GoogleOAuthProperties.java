package com.onescale.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties(prefix = "google.oauth")
@Getter
@Setter
public class GoogleOAuthProperties {
    private String clientId;
}
