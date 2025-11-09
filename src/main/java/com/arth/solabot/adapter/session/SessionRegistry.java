package com.arth.solabot.adapter.session;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 注册 WebSocket session，将 self_id 绑定至 session
 */
@Component
public class SessionRegistry {

    private final Map<Long, WebSocketSession> map = new ConcurrentHashMap<>();

    public WebSocketSession get(long selfId) {
        return map.get(selfId);
    }

    public List<WebSocketSession> getAll() {
        return new ArrayList<>(map.values());
    }

    public void put(long selfId, WebSocketSession session) {
        map.put(selfId, session);
    }

    public void putIfAbsent(long selfId, WebSocketSession session) {
        map.putIfAbsent(selfId, session);
    }

    public void remove(long selfId) {
        map.remove(selfId);
    }
}