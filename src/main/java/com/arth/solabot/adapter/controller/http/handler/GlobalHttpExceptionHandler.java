package com.arth.solabot.adapter.controller.http.handler;

import com.arth.solabot.adapter.controller.http.dto.ApiResponse;
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

import java.io.FileNotFoundException;

/**
 * 全局 HTTP 异常处理器，封装 ApiResponse
 */
@Slf4j
@ControllerAdvice(basePackages = "com.arth.solabot.adapter.controller.http")
public class GlobalHttpExceptionHandler {

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("[adapter.http] missing parameter: {}", e.getMessage());
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(400_01, "Missing parameter: " + e.getParameterName()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException e) {
        log.warn("[adapter.http] unreadable body: {}", e.getMessage());
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(400_02, "Malformed JSON or unreadable request body"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("[adapter.http] bad request: {}", e.getMessage());
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(400_03, e.getMessage() != null ? e.getMessage() : "Invalid argument"));
    }

    @ExceptionHandler(ServletException.class)
    public ResponseEntity<ApiResponse<Void>> handleServletException(ServletException e) {
        log.warn("[adapter.http] servlet error: {}", e.getMessage());
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(400_04, e.getMessage()));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<Void>> handleSecurity(SecurityException e) {
        log.warn("[adapter.http] access denied: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(403_00, e.getMessage() != null ? e.getMessage() : "Access denied"));
    }

    @ExceptionHandler({NoHandlerFoundException.class, FileNotFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoHandlerFoundException e) {
        log.warn("[adapter.http] no handler found: {}", e.getRequestURL());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404_00, "Resource not found"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("[adapter.http] method not allowed: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error(405_00, "Request method not supported"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaType(HttpMediaTypeNotSupportedException e) {
        log.warn("[adapter.http] unsupported media type: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.error(415_00, "Unsupported media type"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception e) {
        log.error("[adapter.http] internal error", e);
        return ResponseEntity
                .internalServerError()
                .body(ApiResponse.error(500_00, "Internal server error"));
    }
}
