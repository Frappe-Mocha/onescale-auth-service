package com.onescale.auth.service;

import com.onescale.auth.exception.OtpException;
import com.onescale.auth.exception.RateLimitException;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${twilio.verify-service-sid}")
    private String verifyServiceSid;

    @Value("${otp.rate-limit.max-requests}")
    private int maxRequests;

    @Value("${otp.rate-limit.window-seconds}")
    private int windowSeconds;

    private static final String RATE_LIMIT_PREFIX = "rate_limit:";

    public void sendEmailOtp(String email) {
        checkRateLimit(email);

        try {
            Verification verification = Verification.creator(
                    verifyServiceSid,
                    email,
                    "email"
            ).create();

            log.info("OTP sent to email: {}, status: {}", email, verification.getStatus());
        } catch (Exception e) {
            log.error("Failed to send OTP to email: {}", email, e);
            throw new OtpException("Failed to send OTP to email");
        }
    }

    public void sendMobileOtp(String mobileNumber) {
        checkRateLimit(mobileNumber);

        try {
            Verification verification = Verification.creator(
                    verifyServiceSid,
                    mobileNumber,
                    "sms"
            ).create();

            log.info("OTP sent to mobile: {}, status: {}", mobileNumber, verification.getStatus());
        } catch (Exception e) {
            log.error("Failed to send OTP to mobile: {}", mobileNumber, e);
            throw new OtpException("Failed to send OTP to mobile number");
        }
    }

    public boolean verifyEmailOtp(String email, String otpCode) {
        try {
            VerificationCheck verificationCheck = VerificationCheck.creator(
                    verifyServiceSid,
                    otpCode
            ).setTo(email).create();

            boolean isValid = "approved".equals(verificationCheck.getStatus());
            log.info("Email OTP verification for {}: {}", email, isValid);
            return isValid;
        } catch (Exception e) {
            log.error("Failed to verify email OTP for: {}", email, e);
            throw new OtpException("Invalid or expired OTP code");
        }
    }

    public boolean verifyMobileOtp(String mobileNumber, String otpCode) {
        try {
            VerificationCheck verificationCheck = VerificationCheck.creator(
                    verifyServiceSid,
                    otpCode
            ).setTo(mobileNumber).create();

            boolean isValid = "approved".equals(verificationCheck.getStatus());
            log.info("Mobile OTP verification for {}: {}", mobileNumber, isValid);
            return isValid;
        } catch (Exception e) {
            log.error("Failed to verify mobile OTP for: {}", mobileNumber, e);
            throw new OtpException("Invalid or expired OTP code");
        }
    }

    private void checkRateLimit(String identifier) {
        String key = RATE_LIMIT_PREFIX + identifier;
        String value = redisTemplate.opsForValue().get(key);

        if (value != null) {
            int count = Integer.parseInt(value);
            if (count >= maxRequests) {
                log.warn("Rate limit exceeded for: {}", identifier);
                throw new RateLimitException("Too many OTP requests. Please try again later.");
            }
            redisTemplate.opsForValue().increment(key);
        } else {
            redisTemplate.opsForValue().set(key, "1", windowSeconds, TimeUnit.SECONDS);
        }
    }
}
