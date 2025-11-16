package com.arth.solabot.core.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class ConcurrencyConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService executorService() {
        int cores = Math.max(2, Runtime.getRuntime().availableProcessors());
        return new ThreadPoolExecutor(
                cores, cores * 2,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1000),
                r -> {
                    Thread t = new Thread(r, "cmd-" + System.nanoTime());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy()  // 回退策略：拒绝执行（抛出异常）
        ) {
            @Override
            protected void beforeExecute(Thread t, Runnable r) {
                super.beforeExecute(t, r);
                // 这里可以添加计时相关的逻辑
            }

            @Override
            protected void afterExecute(Runnable r, Throwable t) {
                super.afterExecute(r, t);
                // 这里可以添加清理相关的逻辑
            }
        };
    }
}