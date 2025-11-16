package com.arth.solabot.core.infrastructure.exception;

public class BadRequestException extends BusinessException {

    public BadRequestException() {
        super(ErrorCode.BAD_REQUEST, "Bad Request", null);
    }

    public BadRequestException(String message) {
        super(ErrorCode.BAD_REQUEST, message, null);
    }

    public BadRequestException(String message, String userMessage) {
        super(ErrorCode.BAD_REQUEST, message, userMessage);
    }
}

