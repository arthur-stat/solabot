package com.arth.solabot.adapter.sender.action.impl;

import com.arth.solabot.adapter.sender.action.SimpleActionBuilder;
import com.arth.solabot.core.bot.dto.OneBotReturnActionDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OneBotSimpleActionBuilder implements SimpleActionBuilder {

    private final ObjectMapper objectMapper;

    @Override
    public String buildGroupSendTextAction(long groupId, String text) {
        return buildSendText("group", Map.of("group_id", groupId), text);
    }

    @Override
    public String buildPrivateSendTextAction(long userId, String text) {
        return buildSendText("private", Map.of("user_id", userId), text);
    }

    @Override
    public String buildGroupReplyTextAction(long groupId, long messageId, String text) {
        return buildReplyText("group", Map.of("group_id", groupId), messageId, text);
    }

    @Override
    public String buildPrivateReplyTextAction(long userId, long messageId, String text) {
        return buildReplyText("private", Map.of("user_id", userId), messageId, text);
    }

    @Override
    public String buildGroupSendImageAction(long groupId, String file) {
        return buildSendImage("group", Map.of("group_id", groupId), file);
    }

    @Override
    public String buildPrivateSendImageAction(long userId, String file) {
        return buildSendImage("private", Map.of("user_id", userId), file);
    }

    @Override
    public String buildGroupReplyImageAction(long groupId, long messageId, String file) {
        return buildReplyImage("group", Map.of("group_id", groupId), messageId, file);
    }

    @Override
    public String buildPrivateReplyImageAction(long userId, long messageId, String file) {
        return buildReplyImage("private", Map.of("user_id", userId), messageId, file);
    }

    @Override
    public String buildGroupSendVideoAction(long groupId, String file) {
        return buildSendVideo("group", Map.of("group_id", groupId), file);
    }

    @Override
    public String buildPrivateSendVideoAction(long userId, String file) {
        return buildSendVideo("private", Map.of("user_id", userId), file);
    }

    @Override
    public String buildGroupForwardAction(long groupId, List<Map<String, Object>> nodes) {
        Map<String, Object> root = new HashMap<>();
        root.put("action", "send_group_forward_msg");

        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("messages", nodes);
        root.put("params", params);

        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("serialize group forward failed", e);
        }
    }

    @Override
    public String buildPrivateForwardAction(long userId, List<Map<String, Object>> nodes) {
        Map<String, Object> root = new HashMap<>();
        root.put("action", "send_private_forward_msg");

        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("messages", nodes);
        root.put("params", params);

        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("serialize private forward failed", e);
        }
    }

    // ===== helpers =====

    private String buildSendText(String messageType, Map<String, Object> target, String text) {
        OneBotReturnActionDTO dto = new OneBotReturnActionDTO();
        dto.setAction("send_msg");

        Map<String, Object> params = new HashMap<>(target);
        params.put("message_type", messageType);

        Map<String, Object> textSeg = new HashMap<>();
        textSeg.put("type", "text");
        textSeg.put("data", Map.of("text", text));

        params.put("message", List.of(textSeg));
        dto.setParams(params);

        try {
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("serialize OneBotActionDTO failed", e);
        }
    }

    private String buildReplyText(String messageType, Map<String, Object> target, long messageId, String text) {
        OneBotReturnActionDTO dto = new OneBotReturnActionDTO();
        dto.setAction("send_msg");

        Map<String, Object> params = new HashMap<>(target);
        params.put("message_type", messageType);

        Map<String, Object> replySeg = new HashMap<>();
        replySeg.put("type", "reply");
        replySeg.put("data", Map.of("id", String.valueOf(messageId)));

        Map<String, Object> textSeg = new HashMap<>();
        textSeg.put("type", "text");
        textSeg.put("data", Map.of("text", text));

        params.put("message", List.of(replySeg, textSeg));
        dto.setParams(params);

        try {
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("serialize OneBotActionDTO failed", e);
        }
    }

    private String buildSendImage(String messageType, Map<String, Object> target, String file) {
        OneBotReturnActionDTO dto = new OneBotReturnActionDTO();
        dto.setAction("send_msg");

        Map<String, Object> params = new HashMap<>(target);
        params.put("message_type", messageType);

        Map<String, Object> imgSeg = new HashMap<>();
        imgSeg.put("type", "image");
        Map<String, Object> data = new HashMap<>();
        data.put("file", file);
        imgSeg.put("data", data);

        params.put("message", List.of(imgSeg));
        dto.setParams(params);

        try {
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("serialize OneBotActionDTO failed", e);
        }
    }

    private String buildReplyImage(String messageType, Map<String, Object> target, long messageId, String file) {
        OneBotReturnActionDTO dto = new OneBotReturnActionDTO();
        dto.setAction("send_msg");

        Map<String, Object> params = new HashMap<>(target);
        params.put("message_type", messageType);

        Map<String, Object> replySeg = new HashMap<>();
        replySeg.put("type", "reply");
        replySeg.put("data", Map.of("id", String.valueOf(messageId)));

        Map<String, Object> imgSeg = new HashMap<>();
        imgSeg.put("type", "image");
        imgSeg.put("data", Map.of("file", file));

        params.put("message", List.of(replySeg, imgSeg));
        dto.setParams(params);

        try {
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("serialize OneBotActionDTO failed", e);
        }
    }

    private String buildSendVideo(String messageType, Map<String, Object> target, String file) {
        OneBotReturnActionDTO dto = new OneBotReturnActionDTO();
        dto.setAction("send_msg");

        Map<String, Object> params = new HashMap<>(target);
        params.put("message_type", messageType);

        Map<String, Object> videoSeg = new HashMap<>();
        videoSeg.put("type", "video");
        Map<String, Object> data = new HashMap<>();
        data.put("file", file);
        videoSeg.put("data", data);

        params.put("message", List.of(videoSeg));
        dto.setParams(params);

        try {
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("serialize OneBotActionDTO failed", e);
        }
    }
}
