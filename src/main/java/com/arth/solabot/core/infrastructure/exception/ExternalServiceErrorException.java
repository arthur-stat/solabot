package com.arth.solabot.core.infrastructure.exception;

public class ExternalServiceErrorException extends BusinessException {

    public ExternalServiceErrorException() {
        super(ErrorCode.EXTERNAL_SERVICE_ERROR, "External Service Error", null);
    }

    public ExternalServiceErrorException(String message) {
        super(ErrorCode.EXTERNAL_SERVICE_ERROR, message, null);
    }

    public ExternalServiceErrorException(String message, String userMessage) {
        super(ErrorCode.EXTERNAL_SERVICE_ERROR, message, userMessage);
    }
}