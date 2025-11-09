package com.arth.solabot.adapter.fetcher;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class EchoWaiter {
    private final ConcurrentHashMap<String, CompletableFuture<String>> pending = new ConcurrentHashMap<>();

    /** 可反复调用；若已存在则复用旧 future，避免回包先到的竞态 **/
    public void register(String echo) {
        pending.computeIfAbsent(echo, k -> new CompletableFuture<>());
    }

    /** 控制器收到回包时调用 **/
    public boolean complete(String echo, String rawJson) {
        var fut = pending.computeIfAbsent(echo, k -> new CompletableFuture<>());
        boolean ok = fut.complete(rawJson);
        return ok;
    }

    /** 发送端等待回包（带超时）；返回原始 JSON 字符串 **/
    public String await(String echo, long timeoutMillis) {
        var fut = pending.computeIfAbsent(echo, k -> new CompletableFuture<>());
        try {
            return fut.orTimeout(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS).join();
        } finally {
            pending.remove(echo);
        }
    }
}
