package com.onescale.auth;

import com.onescale.auth.config.GoogleOAuthProperties;
import com.onescale.auth.config.JwtProperties;
import com.onescale.auth.config.TwilioProperties;
import com.onescale.auth.util.JwtUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
@EnableConfigurationProperties({JwtProperties.class, TwilioProperties.class, GoogleOAuthProperties.class})
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
