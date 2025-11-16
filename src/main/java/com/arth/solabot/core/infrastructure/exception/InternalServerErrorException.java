package com.arth.solabot.core.infrastructure.exception;

public class InternalServerErrorException extends BusinessException {

    public InternalServerErrorException() {
        super(ErrorCode.INTERNAL_SERVER_ERROR, "Internal Server Error", null);
    }

    public InternalServerErrorException(String message) {
        super(ErrorCode.INTERNAL_SERVER_ERROR, message, null);
    }

    public InternalServerErrorException(String message, String userMessage) {
        super(ErrorCode.INTERNAL_SERVER_ERROR, message, userMessage);
    }
}
