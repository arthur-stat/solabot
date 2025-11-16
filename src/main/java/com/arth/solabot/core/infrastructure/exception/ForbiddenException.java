package com.arth.solabot.core.infrastructure.exception;

public class ForbiddenException extends BusinessException {

    public ForbiddenException() {
        super(ErrorCode.FORBIDDEN, "Permission Denied", null);
    }

    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, message, null);
    }

    public ForbiddenException(String message, String userMessage) {
        super(ErrorCode.FORBIDDEN, message, userMessage);
    }
}
