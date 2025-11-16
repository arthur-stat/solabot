package com.arth.solabot.core.infrastructure.exception;

import lombok.Getter;

@Getter
public abstract class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    private final String userMessage;

    public BusinessException(final ErrorCode errorCode, String internalMessage, String userMessage) {
        super(internalMessage);
        this.errorCode = errorCode;
        this.userMessage = userMessage;
    }
}
