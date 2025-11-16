package com.arth.solabot.core.infrastructure.exception;

public class ConflictException extends BusinessException {

    public ConflictException() {
        super(ErrorCode.CONFLICT, "Conflict", null);
    }

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, message, null);
    }

    public ConflictException(String message, String userMessage) {
        super(ErrorCode.CONFLICT, message, userMessage);
    }
}

