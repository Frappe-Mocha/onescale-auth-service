package com.onescale.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties(prefix = "twilio")
@Getter
@Setter
public class TwilioProperties {
    private String accountSid;
    private String authToken;
    private String verifyServiceSid;
}
