package com.onescale.auth.config;

import com.twilio.Twilio;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class TwilioConfig {

    private final TwilioProperties properties;

    public TwilioConfig(TwilioProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        Twilio.init(
                properties.getAccountSid(),
                properties.getAuthToken()
        );
    }
}

