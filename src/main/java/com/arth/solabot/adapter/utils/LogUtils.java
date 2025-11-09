package com.arth.solabot.adapter.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

public class LogUtils {

    private final static int MAX_LOG_LENGTH = 50;

    private LogUtils() {
    }

    public static String limitLen(String s) {
        return limitLen(s, MAX_LOG_LENGTH);
    }

    public static String limitLen(String s, int len) {
        if (s == null) return null;
        return s.length() <= len ? s : s.substring(0, len) + "...";
    }

    /**
     * 获取当前 HTTP 请求的详细信息（JSON 格式）
     *
     * @param objectMapper ObjectMapper 实例
     * @param request      HTTP 请求
     * @return 格式化的请求信息字符串
     */
    public static String getRequestInfoStr(ObjectMapper objectMapper, HttpServletRequest request) {
        if (request == null) {
            return "HttpServletRequest is null";
        }

        Map<String, Object> requestInfo = new LinkedHashMap<>();
        requestInfo.put("method", request.getMethod());
        requestInfo.put("url", request.getRequestURL().toString());
        requestInfo.put("uri", request.getRequestURI());
        requestInfo.put("queryString", request.getQueryString());
        requestInfo.put("remoteAddr", request.getRemoteAddr());
        requestInfo.put("contentType", request.getContentType());
        requestInfo.put("parameters", request.getParameterMap());

        Map<String, String> simpleHeaders = new LinkedHashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            simpleHeaders.put(name, request.getHeader(name));
        }
        requestInfo.put("headers", simpleHeaders);

        try {
            ObjectMapper mapper = objectMapper != null ? objectMapper : new ObjectMapper();
            mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(requestInfo);
        } catch (Exception e) {
            return "Error serializing request info: " + e.getMessage();
        }
    }

    /**
     * 获取当前线程绑定的 HTTP 请求的详细信息（JSON 格式）
     *
     * @param objectMapper ObjectMapper 实例
     * @return 格式化的请求信息字符串
     */
    public static String getRequestInfoStr(ObjectMapper objectMapper) {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return "No active request (RequestContextHolder is null)";
        }
        HttpServletRequest request = attrs.getRequest();
        return getRequestInfoStr(objectMapper, request);
    }
}
