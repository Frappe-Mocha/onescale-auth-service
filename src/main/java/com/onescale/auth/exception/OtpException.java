package com.onescale.auth.exception;

public class OtpException extends AuthException {

    public OtpException(String message) {
        super(message);
    }

    public OtpException(String message, Throwable cause) {
        super(message, cause);
    }
}
