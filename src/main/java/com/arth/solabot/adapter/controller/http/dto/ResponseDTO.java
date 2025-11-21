package com.arth.solabot.adapter.controller.http.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResponseDTO<T> {

    private int code;

    private String message;

    private T data;

    public static <T> ResponseDTO<T> success() {
        return new ResponseDTO<>(200_00, "ok", null);
    }

    public static <T> ResponseDTO<T> success(String message) {
        return new ResponseDTO<>(200_00, message, null);
    }

    public static <T> ResponseDTO<T> success(T data) {
        return new ResponseDTO<>(200_00, "ok", data);
    }

    public static <T> ResponseDTO<T> success(String message, T data) {
        return new ResponseDTO<>(200_00, message, data);
    }

    public static <T> ResponseDTO<T> error(int code, String message) {
        return new ResponseDTO<>(code, message, null);
    }

    private static HttpStatus mapCodeToStatus(int code) {
        return switch (code) {
            case 200_00 -> HttpStatus.OK;
            case 400_00 -> HttpStatus.BAD_REQUEST;
            case 404_00 -> HttpStatus.NOT_FOUND;
            default -> {
                if (code >= 500_00) yield HttpStatus.INTERNAL_SERVER_ERROR;
                yield HttpStatus.OK;
            }
        };
    }

    public static <T> ResponseEntity<ResponseDTO<T>> okEntity() {
        return ResponseEntity.ok(null);
    }

    public static <T> ResponseEntity<ResponseDTO<T>> okEntity(T data) {
        ResponseDTO<T> body = ResponseDTO.success(data);
        return ResponseEntity.ok(body);
    }

    public static <T> ResponseEntity<ResponseDTO<T>> okEntity(T data, HttpHeaders headers) {
        ResponseDTO<T> body = ResponseDTO.success(data);
        return ResponseEntity.ok().headers(headers).body(body);
    }

    public static <T> ResponseEntity<ResponseDTO<T>> notFoundEntity(String message) {
        ResponseDTO<T> body = ResponseDTO.error(404_00, message);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    public static <T> ResponseEntity<ResponseDTO<T>> badRequestEntity(String message) {
        ResponseDTO<T> body = ResponseDTO.error(400_00, message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    public static <T> ResponseEntity<ResponseDTO<T>> internalErrorEntity(String message) {
        ResponseDTO<T> body = ResponseDTO.error(500_00, message);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    public static <T> ResponseEntity<ResponseDTO<T>> from(ResponseDTO<T> dto) {
        if (dto == null) return internalErrorEntity("null response");
        HttpStatus status = mapCodeToStatus(dto.getCode());
        return ResponseEntity.status(status).body(dto);
    }

    public static <T> ResponseEntity<ResponseDTO<T>> from(ResponseDTO<T> dto, HttpHeaders headers) {
        if (dto == null) return internalErrorEntity("null response");
        HttpStatus status = mapCodeToStatus(dto.getCode());
        return ResponseEntity.status(status).headers(headers).body(dto);
    }

    public static <T> ResponseEntity<ResponseDTO<T>> okEntityWithContentType(T data, MediaType contentType, String contentDisposition, String cacheControl) {
        HttpHeaders headers = new HttpHeaders();
        if (contentType != null) headers.setContentType(contentType);
        if (cacheControl != null) headers.set(HttpHeaders.CACHE_CONTROL, cacheControl);
        if (contentDisposition != null) headers.set(HttpHeaders.CONTENT_DISPOSITION, contentDisposition);
        return okEntity(data, headers);
    }

    public static <T> ResponseEntity<ResponseDTO<T>> fromEntity(ResponseEntity<T> entity) {
        return ResponseEntity.status(entity.getStatusCode())
                .headers(entity.getHeaders())
                .body(success(entity.getBody()));
    }
}
