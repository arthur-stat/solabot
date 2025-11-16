package com.arth.solabot.core.bot.handler;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * RuntimeException 的兜底异常拦截器，一般不应由 bot 向用户回复
 */
@Slf4j
@Component
public class GlobalUncaughtExceptionLogger implements Thread.UncaughtExceptionHandler {

    @PostConstruct
    public void init() {
        // 设置为所有线程的默认异常处理器
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    /**
     * 仅兜底未被 catch 的异常（此前是 @AfterThrowing 的切面实现，这会导致任何异常一旦被抛出——即使是被 catch 的，也会被在这里重复处理一次）
     * @param t the thread
     * @param e the exception
     */
    @Override
    public void uncaughtException(Thread t, Throwable e) {
        for (StackTraceElement ste : e.getStackTrace()) {
            String className = ste.getClassName();
            if (className.startsWith("com.arth.solabot.core.bot") || className.startsWith("com.arth.bot.plugin.")) {
                log.error("[global uncaught exception] unhandled runtime exception in thread: {}", t.getName(), e);
                return;
            }
        }
    }
}