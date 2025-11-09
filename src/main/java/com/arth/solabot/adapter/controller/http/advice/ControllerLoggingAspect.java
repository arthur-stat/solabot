package com.arth.solabot.adapter.controller.http.advice;

import com.arth.solabot.adapter.utils.LogUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ControllerLoggingAspect {

    private final ObjectMapper objectMapper;

    @Around("within(@org.springframework.web.bind.annotation.RestController *)")
    public Object logHttpTransaction(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            String contentLength = request.getHeader("Content-Length");
            log.info("[adapter.http] {} {} from {} (type={}, length={})",
                    request.getMethod(),
                    request.getRequestURI(),
                    request.getRemoteAddr(),
                    request.getContentType(),
                    contentLength != null ? contentLength : "-");
            log.debug(LogUtils.getRequestInfoStr(objectMapper));
        }

        long start = System.currentTimeMillis();
        Object result = null;
        Throwable thrown = null;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            thrown = e;
            throw e;
        } finally {
            long cost = System.currentTimeMillis() - start;
            if (thrown == null) {
                if (isSerializableResponse(result)) {
                    try {
                        String responseStr = objectMapper
                                .writerWithDefaultPrettyPrinter()
                                .writeValueAsString(result);

                        log.debug("[adapter.http] response ({} ms): {}", cost, responseStr);

                        if (log.isInfoEnabled()) {
                            String brief = responseStr.length() > 200
                                    ? responseStr.substring(0, 200) + "...(truncated)"
                                    : responseStr;
                            log.info("[adapter.http] completed in {} ms, response={}", cost, brief);
                        }

                    } catch (Exception e) {
                        log.debug("[adapter.http] response ({} ms): <unserializable result: {}>", cost, e.getMessage());
                        if (log.isInfoEnabled()) {
                            log.info("[adapter.http] completed in {} ms (unserializable result)", cost);
                        }
                    }
                } else {
                    log.debug("[adapter.http] response ({} ms): <non-text or stream response of type {}>",
                            cost, result == null ? "null" : result.getClass().getName());
                    if (log.isInfoEnabled()) {
                        log.info("[adapter.http] completed in {} ms (binary/stream response)", cost);
                    }
                }

            } else {
                log.debug("[adapter.http] controller threw exception after {} ms: {}", cost, thrown.toString());
                if (log.isInfoEnabled()) {
                    log.info("[adapter.http] failed after {} ms: {}", cost, thrown.toString());
                }
            }
        }
    }

    /**
     * 如果不加判断地让 Jackson 序列化目标文件以打印日志，我发现在 Windows 上会导致触发 truncate 从而损坏文件，这非常严重
     */
    private boolean isSerializableResponse(Object result) {
        if (result == null) return true;
        if (result instanceof org.springframework.core.io.Resource) return false;
        if (result instanceof java.io.InputStream) return false;
        if (result instanceof java.io.File) return false;
        if (result instanceof byte[]) return false;

        if (result instanceof org.springframework.http.ResponseEntity<?> entity) {
            Object body = entity.getBody();
            return isSerializableResponse(body);
        }

        return true;
    }
}