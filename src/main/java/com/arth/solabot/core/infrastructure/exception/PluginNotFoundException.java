package com.arth.solabot.core.infrastructure.exception;

public class PluginNotFoundException extends BusinessException {

    public PluginNotFoundException() {
        super(ErrorCode.PLUGIN_NOT_FOUND, "Plugin Not Found", null);
    }

    public PluginNotFoundException(String message) {
        super(ErrorCode.PLUGIN_NOT_FOUND, message, null);
    }

    public PluginNotFoundException(String message, String userMessage) {
        super(ErrorCode.PLUGIN_NOT_FOUND, message, userMessage);
    }
}
