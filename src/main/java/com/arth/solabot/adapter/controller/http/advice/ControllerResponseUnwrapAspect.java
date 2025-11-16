package com.arth.solabot.adapter.controller.http.advice;

import com.arth.solabot.adapter.controller.http.dto.ResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * 对标记了 @ResponseUnwrapAnnotation 的 Controller 类或方法的返回值进行解包，直接返回 data
 */
@Aspect
@Component
@Slf4j
public class ControllerResponseUnwrapAspect {

    @Around("within(@UnwrapData *) || @annotation(UnwrapData)")
    public Object unwrapApiResponse(ProceedingJoinPoint pjp) throws Throwable {
        Object result = pjp.proceed();

        try {
            if (result instanceof ResponseEntity<?> respEntity) {
                Object body = respEntity.getBody();
                if (body instanceof ResponseDTO<?> api) {
                    Object data = api.getData();
                    HttpHeaders headers = respEntity.getHeaders();
                    return ResponseEntity.status(respEntity.getStatusCode()).headers(headers).body(data);
                }
                return result;
            }

            if (result instanceof ResponseDTO<?> apiResp) {
                return ResponseEntity.ok(apiResp.getData());
            }

            return result;
        } catch (Throwable e) {
            log.error("[adapter.http] failed to unwrap response in aspect", e);
            return result;
        }
    }
}
