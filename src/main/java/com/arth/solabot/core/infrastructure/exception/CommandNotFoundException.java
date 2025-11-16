package com.arth.solabot.core.infrastructure.exception;

public class CommandNotFoundException extends BusinessException {

    public CommandNotFoundException() {
        super(ErrorCode.COMMAND_NOT_FOUND, "Command Not Found", null);
    }

    public CommandNotFoundException(String message) {
        super(ErrorCode.COMMAND_NOT_FOUND, message, null);
    }

    public CommandNotFoundException(String message, String userMessage) {
        super(ErrorCode.COMMAND_NOT_FOUND, message, userMessage);
    }
}
