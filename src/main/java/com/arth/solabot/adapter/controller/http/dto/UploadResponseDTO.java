package com.arth.solabot.adapter.controller.http.dto;

import lombok.Data;

/**
 * 该 DTO 是学弟定义的，用于网页上文件的上传，暂不清楚为什么不复用 ResponseDTO，后续可能会移除并改为 ResponseDTO
 */
@Data
public class UploadResponseDTO {

    public boolean success;

    public String message;

    public long suiteTimestamp;

    public UploadResponseDTO() {
    }

    public UploadResponseDTO(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public UploadResponseDTO(boolean success, String message, long suiteTimestamp) {
        this.success = success;
        this.message = message;
        this.suiteTimestamp = suiteTimestamp;
    }

    public static UploadResponseDTO build(boolean success, String message) {
        return new UploadResponseDTO(success, message);
    }

    public static UploadResponseDTO build(boolean success, String message, long suiteTimestamp) {
        return new UploadResponseDTO(success, message, suiteTimestamp);
    }
}
