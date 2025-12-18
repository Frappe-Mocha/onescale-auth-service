package com.onescale.auth.exception;

public class RateLimitException extends AuthException {

    public RateLimitException(String message) {
        super(message);
    }
}
