package com.arth.solabot.core.infrastructure.exception;

public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException() {
        super(ErrorCode.RESOURCE_NOT_FOUND, "Resource Not Found", null);
    }

    public ResourceNotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message, null);
    }

    public ResourceNotFoundException(String message, String userMessage) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message, userMessage);
    }
}