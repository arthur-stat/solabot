package com.arth.solabot.adapter.sender.impl;

import com.arth.solabot.adapter.fetcher.EchoWaiter;
import com.arth.solabot.adapter.sender.Sender;
import com.arth.solabot.adapter.sender.action.SimpleActionBuilder;
import com.arth.solabot.adapter.session.SessionRegistry;
import com.arth.solabot.core.bot.dto.ParsedPayloadDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.lang.reflect.Array;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class OneBotSender implements Sender {

    private final SessionRegistry sessions;
    private final SimpleActionBuilder simpleActionBuilder;
    private final ObjectMapper objectMapper;
    private final EchoWaiter echoWaiter;

    private static final int PARTIAL_MESSAGE_MAX_CHAR_NUM = 300_000;

    @Override
    public void sendText(ParsedPayloadDTO payload, Object text) {
        WebSocketSession session = sessions.get(payload.getSelfId());
        if (session == null || !session.isOpen() || text == null) return;

        try {
            // 针对可迭代对象，例如 List
            if (text instanceof Iterable<?> it) {
                for (Object o : it) sendTextOnce(session, payload, o);
            } else if (text.getClass().isArray()) {
                // 针对数组
                int length = Array.getLength(text);
                for (int i = 0; i < length; i++) {
                    Object element = Array.get(text, i);
                    sendTextOnce(session, payload, element);
                }
            } else {
                // 否则直接作为非迭代而直接传入的参数
                sendTextOnce(session, payload, text);
            }
        } catch (Exception e) {
            log.warn("[adapter.sender] send text failed", e);
        }
    }

    @Override
    public void sendText(WebSocketSession session, long userId, Long groupId, Object text) {
        if (session == null || !session.isOpen() || text == null) return;

        try {
            // 针对可迭代对象，例如 List
            if (text instanceof Iterable<?> it) {
                for (Object o : it) sendTextOnce(session, userId, groupId, o);
            } else if (text.getClass().isArray()) {
                // 针对数组
                int length = Array.getLength(text);
                for (int i = 0; i < length; i++) {
                    Object element = Array.get(text, i);
                    sendTextOnce(session, userId, groupId, element);
                }
            } else {
                // 否则直接作为非迭代而直接传入的参数
                sendTextOnce(session, userId, groupId, text);
            }
        } catch (Exception e) {
            log.warn("[adapter.sender] send text failed", e);
        }
    }

    @Override
    public void replyText(ParsedPayloadDTO payload, Object text) {
        WebSocketSession session = sessions.get(payload.getSelfId());
        if (session == null || !session.isOpen() || text == null) return;

        try {
            // 针对可迭代对象，例如 List
            if (text instanceof Iterable<?> it) {
                for (Object o : it) replyTextOnce(session, payload, o);
            } else if (text.getClass().isArray()) {
                // 针对数组
                int length = Array.getLength(text);
                for (int i = 0; i < length; i++) {
                    Object element = Array.get(text, i);
                    replyTextOnce(session, payload, element);
                }
            } else {
                // 否则直接作为非迭代而直接传入的参数
                replyTextOnce(session, payload, text);
            }
        } catch (Exception e) {
            log.warn("[adapter.sender] send text failed", e);
        }
    }

    @Override
    public void replyText(WebSocketSession session, long userId, Long groupId, long messageId, Object text) {
        if (session == null || !session.isOpen() || text == null) return;

        try {
            // 针对可迭代对象，例如 List
            if (text instanceof Iterable<?> it) {
                for (Object o : it) replyTextOnce(session, userId, groupId, messageId, o);
            } else if (text.getClass().isArray()) {
                // 针对数组
                int length = Array.getLength(text);
                for (int i = 0; i < length; i++) {
                    Object element = Array.get(text, i);
                    replyTextOnce(session, userId, groupId, messageId, element);
                }
            } else {
                // 否则直接作为非迭代而直接传入的参数
                replyTextOnce(session, userId, groupId, messageId, text);
            }
        } catch (Exception e) {
            log.warn("[adapter.sender] send text failed", e);
        }
    }

    @Override
    public void sendImage(ParsedPayloadDTO payload, Object image) {
        WebSocketSession session = sessions.get(payload.getSelfId());
        if (session == null || !session.isOpen() || image == null) return;

        try {
            String json;
            // 针对可迭代对象，例如 List
            if (image instanceof Iterable<?> it) {
                var files = toFileList(it);
                if (files.isEmpty()) return;
                if (files.size() == 1) {
                    String f = files.get(0);
                    json = payload.getGroupId() != null
                            ? simpleActionBuilder.buildGroupSendImageAction(payload.getGroupId(), f)
                            : simpleActionBuilder.buildPrivateSendImageAction(payload.getUserId(), f);
                } else {
                    json = payload.getGroupId() != null
                            ? buildMultiImageSendJson("group", Map.of("group_id", payload.getGroupId()), files)
                            : buildMultiImageSendJson("private", Map.of("user_id", payload.getUserId()), files);
                }
            } else if (image.getClass().isArray()) {
                // 针对数组
                List<String> files = toFileListFromArray(image);
                if (files.isEmpty()) return;
                if (files.size() == 1) {
                    String f = files.get(0);
                    json = payload.getGroupId() != null
                            ? simpleActionBuilder.buildGroupSendImageAction(payload.getGroupId(), f)
                            : simpleActionBuilder.buildPrivateSendImageAction(payload.getUserId(), f);
                } else {
                    json = payload.getGroupId() != null
                            ? buildMultiImageSendJson("group", Map.of("group_id", payload.getGroupId()), files)
                            : buildMultiImageSendJson("private", Map.of("user_id", payload.getUserId()), files);
                }
            } else {
                // 否则直接作为非迭代而直接传入的参数
                String f = String.valueOf(image);
                if (f == null || f.isBlank()) return;
                json = payload.getGroupId() != null
                        ? simpleActionBuilder.buildGroupSendImageAction(payload.getGroupId(), f)
                        : simpleActionBuilder.buildPrivateSendImageAction(payload.getUserId(), f);
            }

            log.info("[adapter.sender] build sending image: {}", image);
            log.debug("[adapter.sender] raw sending image action json: {}", json);
            sendPartialMessage(session, json, PARTIAL_MESSAGE_MAX_CHAR_NUM);
        } catch (Exception e) {
            log.warn("[adapter.sender] send image failed", e);
        }
    }

    @Override
    public void replyImage(ParsedPayloadDTO payload, Object image) {
        WebSocketSession session = sessions.get(payload.getSelfId());
        if (session == null || !session.isOpen() || image == null) return;

        try {
            String json;
            // 针对可迭代对象，例如 List
            if (image instanceof Iterable<?> it) {
                var files = toFileList(it);
                if (files.isEmpty()) return;
                if (files.size() == 1) {
                    String f = files.get(0);
                    json = payload.getGroupId() != null
                            ? simpleActionBuilder.buildGroupReplyImageAction(payload.getGroupId(), payload.getMessageId(), f)
                            : simpleActionBuilder.buildPrivateReplyImageAction(payload.getUserId(), payload.getMessageId(), f);
                } else {
                    json = payload.getGroupId() != null
                            ? buildMultiImageResponseJson("group", Map.of("group_id", payload.getGroupId()),
                            payload.getMessageId(), files)
                            : buildMultiImageResponseJson("private", Map.of("user_id", payload.getUserId()),
                            payload.getMessageId(), files);
                }
            } else if (image.getClass().isArray()) {
                // 针对数组
                List<String> files = toFileListFromArray(image);
                if (files.isEmpty()) return;
                if (files.size() == 1) {
                    String f = files.get(0);
                    json = payload.getGroupId() != null
                            ? simpleActionBuilder.buildGroupReplyImageAction(payload.getGroupId(), payload.getMessageId(), f)
                            : simpleActionBuilder.buildPrivateReplyImageAction(payload.getUserId(), payload.getMessageId(), f);
                } else {
                    json = payload.getGroupId() != null
                            ? buildMultiImageResponseJson("group", Map.of("group_id", payload.getGroupId()),
                            payload.getMessageId(), files)
                            : buildMultiImageResponseJson("private", Map.of("user_id", payload.getUserId()),
                            payload.getMessageId(), files);
                }
            } else {
                // 否则直接作为非迭代而直接传入的参数
                String f = String.valueOf(image);
                if (f == null || f.isBlank()) return;
                json = payload.getGroupId() != null
                        ? simpleActionBuilder.buildGroupReplyImageAction(payload.getGroupId(), payload.getMessageId(), f)
                        : simpleActionBuilder.buildPrivateReplyImageAction(payload.getUserId(), payload.getMessageId(), f);
            }

            log.info("[adapter.sender] build replying image: {}", image);
            log.debug("[adapter.sender] raw replying image action json: {}", json);
            sendPartialMessage(session, json, PARTIAL_MESSAGE_MAX_CHAR_NUM);
        } catch (Exception e) {
            log.warn("[adapter.sender] replay image failed", e);
        }
    }

    @Override
    public void sendVideo(ParsedPayloadDTO payload, Object video) {
        WebSocketSession session = sessions.get(payload.getSelfId());
        if (session == null || !session.isOpen() || video == null) return;

        try {
            String json;
            // 针对可迭代对象，例如 List
            if (video instanceof Iterable<?> it) {
                var files = toFileList(it);
                if (files.isEmpty()) return;
                if (files.size() == 1) {
                    String f = files.get(0);
                    json = payload.getGroupId() != null
                            ? simpleActionBuilder.buildGroupSendVideoAction(payload.getGroupId(), f)
                            : simpleActionBuilder.buildPrivateSendVideoAction(payload.getUserId(), f);
                } else {
                    String f = files.get(0);
                    json = payload.getGroupId() != null
                            ? simpleActionBuilder.buildGroupSendVideoAction(payload.getGroupId(), f)
                            : simpleActionBuilder.buildPrivateSendVideoAction(payload.getUserId(), f);
                    for (int i = 1; i < files.size(); i++) {
                        String remainingFile = files.get(i);
                        String remainingJson = payload.getGroupId() != null
                                ? simpleActionBuilder.buildGroupSendVideoAction(payload.getGroupId(), remainingFile)
                                : simpleActionBuilder.buildPrivateSendVideoAction(payload.getUserId(), remainingFile);
                        sendPartialMessage(session, remainingJson, PARTIAL_MESSAGE_MAX_CHAR_NUM);
                    }
                }
            } else if (video.getClass().isArray()) {
                // 针对数组
                List<String> files = toFileListFromArray(video);
                if (files.isEmpty()) return;
                if (files.size() == 1) {
                    String f = files.get(0);
                    json = payload.getGroupId() != null
                            ? simpleActionBuilder.buildGroupSendVideoAction(payload.getGroupId(), f)
                            : simpleActionBuilder.buildPrivateSendVideoAction(payload.getUserId(), f);
                } else {
                    String f = files.get(0);
                    json = payload.getGroupId() != null
                            ? simpleActionBuilder.buildGroupSendVideoAction(payload.getGroupId(), f)
                            : simpleActionBuilder.buildPrivateSendVideoAction(payload.getUserId(), f);
                    for (int i = 1; i < files.size(); i++) {
                        String remainingFile = files.get(i);
                        String remainingJson = payload.getGroupId() != null
                                ? simpleActionBuilder.buildGroupSendVideoAction(payload.getGroupId(), remainingFile)
                                : simpleActionBuilder.buildPrivateSendVideoAction(payload.getUserId(), remainingFile);
                        sendPartialMessage(session, remainingJson, PARTIAL_MESSAGE_MAX_CHAR_NUM);
                    }
                }
            } else {
                // 否则直接作为非迭代而直接传入的参数
                String f = String.valueOf(video);
                if (f == null || f.isBlank()) return;
                json = payload.getGroupId() != null
                        ? simpleActionBuilder.buildGroupSendVideoAction(payload.getGroupId(), f)
                        : simpleActionBuilder.buildPrivateSendVideoAction(payload.getUserId(), f);
            }

            log.info("[adapter.sender] build sending video: {}", video);
            log.debug("[adapter.sender] raw sending video action json: {}", json);
            sendPartialMessage(session, json, PARTIAL_MESSAGE_MAX_CHAR_NUM);
        } catch (Exception e) {
            log.warn("[adapter.sender] send video failed", e);
        }
    }

    @Override
    public void pushActionJSON(long selfId, String json) {
        WebSocketSession session = sessions.get(selfId);
        if (session == null || !session.isOpen()) {
            log.warn("[adapter.sender] raw json send: session missing/closed, selfId={}", selfId);
            return;
        }

        try {
            log.info("[adapter.sender] push raw action json (may be a command response)");
            log.debug("[adapter.sender] raw action json: {}", json);
            sendPartialMessage(session, json, PARTIAL_MESSAGE_MAX_CHAR_NUM);
        } catch (Exception e) {
            log.warn("[adapter.sender] raw json send failed, selfId={}", selfId, e);
        }
    }

    @Override
    public JsonNode request(long selfId, String action, JsonNode params, Duration timeout) {
        String echo = java.util.UUID.randomUUID().toString();
        echoWaiter.register(echo);

        ObjectNode req = objectMapper.createObjectNode();
        req.put("action", action);
        if (params != null) req.set("params", params);
        req.put("echo", echo);

        pushActionJSON(selfId, req.toString());

        try {
            String raw = echoWaiter.await(echo, timeout.toMillis());  // 等待 controller 完成的回包
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new RuntimeException("request '" + action + "' failed/timeout", e);
        }
    }

    @Override
    public <T> T request(long selfId, String json, JsonNode params, Duration timeout, Function<JsonNode, T> mapper) {
        JsonNode resp = request(selfId, json, params, timeout);
        return mapper.apply(resp);
    }

    private void sendTextOnce(WebSocketSession session, ParsedPayloadDTO payload, Object text) throws IOException {
        sendTextOnce(session, payload.getUserId(), payload.getGroupId(), text);
    }

    private void sendTextOnce(WebSocketSession session, long userId, Long groupId, Object text) throws IOException {
        if (text == null) return;
        String json;
        String content = (text instanceof String s) ? s : String.valueOf(text);

        if (groupId != null) {
            json = simpleActionBuilder.buildGroupSendTextAction(groupId, content);
            log.debug("[adapter.sender] build sending text to group {}: {}", groupId, content);
        } else {
            json = simpleActionBuilder.buildPrivateSendTextAction(userId, content);
            log.debug("[adapter.sender] build sending text to user {}: {}", userId, content);
        }

        log.debug("[adapter.sender] raw sending text action json: {}", json);
        sendPartialMessage(session, json, PARTIAL_MESSAGE_MAX_CHAR_NUM);
    }

    private void replyTextOnce(WebSocketSession session, ParsedPayloadDTO payload, Object text) throws IOException {
        replyTextOnce(session, payload.getUserId(), payload.getGroupId(), payload.getMessageId(), text);
    }

    private void replyTextOnce(WebSocketSession session, long userId, Long groupId, long messageId, Object text) throws IOException {
        if (text == null) return;
        String json;
        String content = (text instanceof String s) ? s : String.valueOf(text);

        if (groupId != null) {
            json = simpleActionBuilder.buildGroupReplyTextAction(groupId, messageId, content);
            log.info("[adapter.sender] build replying text to group {}: {}", groupId, content);
        } else {
            json = simpleActionBuilder.buildPrivateReplyTextAction(userId, messageId, content);
            log.info("[adapter.sender] build replying text to user {}: {}", userId, content);
        }

        log.debug("[adapter.sender] raw replying text action json: {}", json);
        sendPartialMessage(session, json, PARTIAL_MESSAGE_MAX_CHAR_NUM);
    }

    // ++=============** helpers **=============++

    /**
     * Iterable -> 去空白后的字符串列表
     */
    private static List<String> toFileList(Iterable<?> it) {
        ArrayList<String> out = new ArrayList<>();
        for (Object o : it) {
            if (o == null) continue;
            String s = String.valueOf(o).trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    /**
     * Array -> 去空白后的字符串列表
     */
    private static List<String> toFileListFromArray(Object array) {
        ArrayList<String> out = new ArrayList<>();
        int length = Array.getLength(array);
        for (int i = 0; i < length; i++) {
            Object o = Array.get(array, i);
            if (o == null) continue;
            String s = String.valueOf(o).trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    /**
     * 构造：多图同一条消息（不带 reply）
     */
    private String buildMultiImageSendJson(String messageType, Map<String, Object> target, List<String> files) {
        Map<String, Object> root = new HashMap<>();
        root.put("action", "send_msg");

        Map<String, Object> params = new HashMap<>(target);
        params.put("message_type", messageType);

        List<Object> message = new ArrayList<>(files.size());
        for (String f : files) {
            message.add(imageSeg(f));
        }
        params.put("message", message);
        root.put("params", params);

        try {
            return objectMapper.writeValueAsString(root);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("serialize multi-image send failed", e);
        }
    }

    /**
     * 构造：多图同一条消息（带 reply）
     */
    private String buildMultiImageResponseJson(String messageType, Map<String, Object> target, long messageId, List<String> files) {
        Map<String, Object> root = new HashMap<>();
        root.put("action", "send_msg");

        Map<String, Object> params = new HashMap<>(target);
        params.put("message_type", messageType);

        List<Object> message = new ArrayList<>(files.size() + 1);
        Map<String, Object> reply = new HashMap<>();
        reply.put("type", "reply");
        reply.put("data", Map.of("id", String.valueOf(messageId)));
        message.add(reply);

        for (String f : files) {
            message.add(imageSeg(f));
        }
        params.put("message", message);
        root.put("params", params);

        try {
            return objectMapper.writeValueAsString(root);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("serialize multi-image reply failed", e);
        }
    }

    private static Map<String, Object> imageSeg(String file) {
        Map<String, Object> seg = new HashMap<>();
        seg.put("type", "image");
        seg.put("data", Map.of("file", file));
        return seg;
    }

    /**
     * partial message 发送，避免 The decoded text message was too big for the output buffer
     *
     * @param session
     * @param json
     * @param chunkSize 分片大小，以 UTF-8 字符数为单位
     * @throws IOException
     */
    private void sendPartialMessage(WebSocketSession session, Object json, int chunkSize) throws IOException {
        if (session == null || !session.isOpen() || json == null) return;

        String jsonStr = (json instanceof String s) ? s : objectMapper.writeValueAsString(json);
        int length = jsonStr.length();

        for (int offset = 0; offset < length; ) {
            int end = Math.min(length, offset + chunkSize);
            if (Character.isHighSurrogate(jsonStr.charAt(end - 1))) end--;

            boolean isLast = (end == length);
            String chunk = jsonStr.substring(offset, end);
            session.sendMessage(new TextMessage(chunk, isLast));
            offset = end;
        }
    }
}
