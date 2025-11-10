package com.arth.solabot.plugin.custom.pjsk.model;

public class UploadPageJsonResponse {
    public static String SUCCESS_MSG = "Upload and process completed!";
    public boolean success;
    public String message;
    public long suiteTimestamp;

    public UploadPageJsonResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public UploadPageJsonResponse(boolean success, String message, long suiteTimestamp) {
        this.success = success;
        this.message = message;
        this.suiteTimestamp = suiteTimestamp;
    }

    public static UploadPageJsonResponse build(boolean success, String message) {
        return new UploadPageJsonResponse(success, message);
    }

    public static UploadPageJsonResponse build(boolean success, String message, long suiteTimestamp) {
        return new UploadPageJsonResponse(success, message, suiteTimestamp);
    }
}
