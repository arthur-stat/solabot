package com.arth.solabot.core.infrastructure.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    BAD_REQUEST(HttpStatus.BAD_REQUEST, 400_00),

    // for bot client primarily
    INVALID_COMMAND_ARGS(HttpStatus.BAD_REQUEST, 400_01),

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, 401_00),

    FORBIDDEN(HttpStatus.FORBIDDEN, 403_00),

    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, 404_01),

    // for bot client primarily
    COMMAND_NOT_FOUND(HttpStatus.NOT_FOUND, 404_02),

    // for bot client primarily
    PLUGIN_NOT_FOUND(HttpStatus.NOT_FOUND, 404_03),

    CONFLICT(HttpStatus.CONFLICT, 409_00),

    VALIDATION_ERROR(HttpStatus.UNPROCESSABLE_ENTITY, 422_00),

    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, 429_00),

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, 500_00),

    EXTERNAL_SERVICE_ERROR(HttpStatus.BAD_GATEWAY, 502_00);

    private final HttpStatus httpStatus;

    private final int numericCode;

    ErrorCode(HttpStatus httpStatus, int numericCode) {
        this.httpStatus = httpStatus;
        this.numericCode = numericCode;
    }

    @Override
    public String toString() {
        return this.name().toLowerCase().replace('_', ' ');
    }
}
