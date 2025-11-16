package com.arth.solabot.core.infrastructure.exception;

public class UnauthorizedException extends BusinessException {

    public UnauthorizedException() {
        super(ErrorCode.UNAUTHORIZED, "Unauthorized", null);
    }

    public UnauthorizedException(String message) {
        super(ErrorCode.UNAUTHORIZED, message, null);
    }

    public UnauthorizedException(String message, String userMessage) {
        super(ErrorCode.UNAUTHORIZED, message, userMessage);
    }
}

