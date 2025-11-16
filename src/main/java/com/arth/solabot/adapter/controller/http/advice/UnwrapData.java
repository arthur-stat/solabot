package com.arth.solabot.adapter.controller.http.advice;

import java.lang.annotation.*;

/**
 * 标记用注解，带有该注解的 Controller 类或方法将对返回值进行解包，直接返回 data
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UnwrapData {
    // marker annotation
}
