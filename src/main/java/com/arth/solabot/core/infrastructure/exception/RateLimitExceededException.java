package com.arth.solabot.core.infrastructure.exception;

public class RateLimitExceededException extends BusinessException {

    public RateLimitExceededException() {
        super(ErrorCode.RATE_LIMIT_EXCEEDED, "Rate limit exceeded", null);
    }

    public RateLimitExceededException(String message) {
        super(ErrorCode.RATE_LIMIT_EXCEEDED, message, null);
    }

    public RateLimitExceededException(String message, String userMessage) {
        super(ErrorCode.RATE_LIMIT_EXCEEDED, message, userMessage);
    }
}

