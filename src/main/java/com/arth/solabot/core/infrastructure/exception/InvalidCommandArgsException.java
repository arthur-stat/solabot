package com.arth.solabot.core.infrastructure.exception;

public class InvalidCommandArgsException extends BusinessException {

    public InvalidCommandArgsException() {
        super(ErrorCode.INVALID_COMMAND_ARGS, "Invalid Command Args", null);
    }

    public InvalidCommandArgsException(String message) {
         super(ErrorCode.INVALID_COMMAND_ARGS, message, null);
    }

    public InvalidCommandArgsException(String message, String userMessage) {
        super(ErrorCode.INVALID_COMMAND_ARGS, message, userMessage);
    }
}
