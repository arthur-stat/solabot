package com.arth.solabot.core.infrastructure.exception;

public class ValidationException extends BusinessException {

    public ValidationException() {
        super(ErrorCode.VALIDATION_ERROR, "Validation Error", null);
    }

    public ValidationException(String message) {
        super(ErrorCode.VALIDATION_ERROR, message, null);
    }

    public ValidationException(String message, String userMessage) {
        super(ErrorCode.VALIDATION_ERROR, message, userMessage);
    }
}

