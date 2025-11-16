package com.arth.solabot.adapter.controller.http.handler;

import com.arth.solabot.adapter.controller.http.dto.ResponseDTO;
import com.arth.solabot.core.infrastructure.exception.BusinessException;
import com.arth.solabot.core.infrastructure.exception.ErrorCode;
import com.arth.solabot.core.infrastructure.exception.ResourceNotFoundException;
import jakarta.servlet.ServletException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * 全局 HTTP 异常处理器，封装 ApiResponse
 */
@Slf4j
@ControllerAdvice(basePackages = "com.arth.solabot.adapter.controller.http")
public class GlobalHttpExceptionHandler {

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ResponseDTO<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("[adapter.http] missing parameter: {}", e.getMessage());
        return ResponseEntity
                .badRequest()
                .body(ResponseDTO.error(400_01, "Missing parameter: " + e.getParameterName()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ResponseDTO<Void>> handleUnreadable(HttpMessageNotReadableException e) {
        log.warn("[adapter.http] unreadable body: {}", e.getMessage());
        return ResponseEntity
                .badRequest()
                .body(ResponseDTO.error(400_02, "Malformed JSON or unreadable request body"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ResponseDTO<Void>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("[adapter.http] bad request: {}", e.getMessage());
        return ResponseEntity
                .badRequest()
                .body(ResponseDTO.error(400_03, e.getMessage() != null ? e.getMessage() : "Invalid argument"));
    }

    @ExceptionHandler(ServletException.class)
    public ResponseEntity<ResponseDTO<Void>> handleServletException(ServletException e) {
        log.warn("[adapter.http] servlet error: {}", e.getMessage());
        return ResponseEntity
                .badRequest()
                .body(ResponseDTO.error(400_04, e.getMessage()));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ResponseDTO<Void>> handleSecurity(SecurityException e) {
        log.warn("[adapter.http] access denied: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ResponseDTO.error(403_00, e.getMessage() != null ? e.getMessage() : "Access denied"));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ResponseDTO<Void>> handleBusinessException(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        String userMsg = e.getUserMessage() != null ? e.getUserMessage() : e.getMessage();

        HttpStatus status = code != null ? code.getHttpStatus() : HttpStatus.INTERNAL_SERVER_ERROR;
        int numeric = code != null ? code.getNumericCode() : 500_00;

        String msg = userMsg != null ? userMsg : (code != null ? code.toString() : "Internal server error");

        log.warn("[adapter.http] business exception: {} - {}", code, e.getMessage());
        return ResponseEntity.status(status).body(ResponseDTO.error(numeric, msg));
    }

    @ExceptionHandler({NoHandlerFoundException.class, ResourceNotFoundException.class})
    public ResponseEntity<ResponseDTO<Void>> handleNotFound(Exception e) {
        if (e instanceof NoHandlerFoundException nhf) {
            log.warn("[adapter.http] no handler found: {}", nhf.getRequestURL());
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ResponseDTO.error(404_00, "Resource not found: " + nhf.getRequestURL()));
        } else if (e instanceof ResourceNotFoundException rnf) {
            log.warn("[adapter.http] resource not found: {}", rnf.getMessage());
            ErrorCode code = rnf.getErrorCode();
            int numeric = code != null ? code.getNumericCode() : 404_00;
            String msg = rnf.getUserMessage() != null ? rnf.getUserMessage() : rnf.getMessage();
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ResponseDTO.error(numeric, msg != null ? msg : "Resource not found"));
        } else {
            log.warn("[adapter.http] not found: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ResponseDTO.error(404_00, "Resource not found"));
        }
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ResponseDTO<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("[adapter.http] method not allowed: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ResponseDTO.error(405_00, "Request method not supported"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ResponseDTO<Void>> handleMediaType(HttpMediaTypeNotSupportedException e) {
        log.warn("[adapter.http] unsupported media type: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ResponseDTO.error(415_00, "Unsupported media type"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseDTO<Void>> handleGeneric(Exception e) {
        log.error("[adapter.http] internal error", e);
        return ResponseEntity
                .internalServerError()
                .body(ResponseDTO.error(500_00, "Internal server error"));
    }
}
