package com.arth.solabot.adapter.sender.action.impl;

import com.arth.solabot.adapter.sender.action.ActionChainBuilder;
import com.arth.solabot.adapter.sender.action.SimpleActionBuilder;
import com.arth.solabot.core.infrastructure.exception.InternalServerErrorException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OneBotActionChainBuilder implements ActionChainBuilder {
    
    /* 一个血泪教训是不要用字段注入，这里 ObjectMapper 无论是 @Autowired 还是 @Resource 都为 null（注入失败） */
    private final ObjectMapper objectMapper;
    private final SimpleActionBuilder simpleActionBuilder;
    private final List<Map<String, Object>> segments = new ArrayList<>();

    @Autowired
    public OneBotActionChainBuilder(ObjectMapper objectMapper, SimpleActionBuilder simpleActionBuilder) {
        this.objectMapper = objectMapper;
        this.simpleActionBuilder = simpleActionBuilder;
    }

    public OneBotActionChainBuilder create() {
        return new OneBotActionChainBuilder(this.objectMapper, this.simpleActionBuilder);
    }

    public OneBotActionChainBuilder setReplay(long messageId) {
        Map<String, Object> seg = new HashMap<>();
        seg.put("type", "reply");
        seg.put("data", Map.of("id", String.valueOf(messageId)));
        segments.add(0, seg);
        return this;
    }

    public OneBotActionChainBuilder text(String text) {
        Map<String, Object> seg = new HashMap<>();
        seg.put("type", "text");
        seg.put("data", Map.of("text", text == null ? "" : text));
        segments.add(seg);
        return this;
    }

    public OneBotActionChainBuilder image(String file) {
        if (file != null && !file.isBlank()) {
            Map<String, Object> seg = new HashMap<>();
            seg.put("type", "image");
            seg.put("data", Map.of("file", file));
            segments.add(seg);
        }
        return this;
    }

//    public OneBotActionChainBuilder video(String file) {
//        if (file != null && !file.isBlank()) {
//            Map<String, Object> seg = new HashMap<>();
//            seg.put("type", "video");
//            seg.put("data", Map.of("file", file));
//            segments.add(seg);
//        }
//        return this;
//    }

    public OneBotActionChainBuilder at(long usedId) {
        Map<String, Object> seg = new HashMap<>();
        seg.put("type", "at");
        seg.put("data", Map.of("qq", String.valueOf(usedId)));
        segments.add(seg);
        return this;
    }

    public OneBotActionChainBuilder custom(String type, Map<String, Object> data) {
        Map<String, Object> seg = new HashMap<>();
        seg.put("type", type);
        seg.put("data", data == null ? Map.of() : data);
        segments.add(seg);
        return this;
    }

    public OneBotActionChainBuilder clear() {
        segments.clear();
        return this;
    }

    public String toGroupJson(long groupId) {
        return buildSendJson("group", Map.of("group_id", groupId));
    }

    public String toPrivateJson(long userId) {
        return buildSendJson("private", Map.of("user_id", userId));
    }

    private String buildSendJson(String messageType, Map<String, Object> target) {
        Map<String, Object> root = new HashMap<>();
        root.put("action", "send_msg");
        root.put("echo", "echo-" + System.currentTimeMillis());

        Map<String, Object> params = new HashMap<>(target);
        params.put("message_type", messageType);
        // 注：直接引用 segments；如果后续还要 mutate，请先 clone
        params.put("message", new ArrayList<>(segments));

        root.put("params", params);

        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new InternalServerErrorException("serialize OneBotActionChain failed", "convert OneBotActionChain to JSON failed");
        }
    }
}
