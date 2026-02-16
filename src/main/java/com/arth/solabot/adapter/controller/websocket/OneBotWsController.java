package com.arth.solabot.adapter.controller.websocket;

import com.arth.solabot.adapter.fetcher.EchoWaiter;
import com.arth.solabot.adapter.parser.PayloadParser;
import com.arth.solabot.adapter.session.SessionRegistry;
import com.arth.solabot.adapter.utils.LogUtils;
import com.arth.solabot.core.bot.dto.ParsedPayloadDTO;
import com.arth.solabot.core.bot.invoker.CommandInvoker;
import com.arth.solabot.core.infrastructure.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Controller
@RequiredArgsConstructor
public class OneBotWsController extends TextWebSocketHandler {

    @Value("${app.cmd-thread-timeout}")
    private int CMD_THREAD_TIMEOUT;

    private final SessionRegistry sessionRegistry;
    private final ExecutorService executorService;
    private final CommandInvoker commandInvoker;
    private final PayloadParser payloadParser;
    private final ObjectMapper objectMapper;
    private final EchoWaiter echoWaiter;

    /* 分片大小 */
    private static final int MAX_TEXT_MESSAGE_BYTES = 5 * 1024 * 1024;  // 5MB
    /* 分片 */
    private final ConcurrentHashMap<String, StringBuilder> partialBuffer = new ConcurrentHashMap<>();

    /**
     * 启用分片读取 output buffer
     *
     * @return
     */
    @Override
    public boolean supportsPartialMessages() {
        return true;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("[adapter.ws] ws connected: {}", session.getId());
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            /* 分片读取 output buffer，避免 code 1009 错误：The decoded text message was too big
            for the output buffer and the endpoint does not support partial messages */
            String rawPayload;
            String sid = session.getId();

            // 分片聚合流程（非当前会话的前序分片，或当前帧非最后一片）
            if (partialBuffer.containsKey(sid) || !message.isLast()) {
                StringBuilder buf = partialBuffer.computeIfAbsent(sid, k -> new StringBuilder());
                if (buf.length() + message.getPayloadLength() > MAX_TEXT_MESSAGE_BYTES) {
                    partialBuffer.remove(sid);
                    session.close(CloseStatus.TOO_BIG_TO_PROCESS);
                    return;
                }
                buf.append(message.getPayload());

                // 非最后一片
                if (!message.isLast()) return;
                // 最后一片，结算并取出完整消息
                rawPayload = buf.toString();
                partialBuffer.remove(sid);
            } else {
                // 非分片或仅一片，直接结算
                rawPayload = message.getPayload();
            }

            JsonNode root = objectMapper.readTree(rawPayload);

            /* 注册 session，绑定至 self_id */
            if (session.getAttributes().get("self_id") == null) {
                final long selfId = root.path("self_id").asLong(0L);
                if (selfId != 0L) {
                    sessionRegistry.put(selfId, session);
                    session.getAttributes().put("self_id", selfId);
                }
            }

            /* 协议回包：post_type 为空，优先走 EchoWaiter 并结束，不进入事件解析链路 */
            if (root.get("post_type") == null) {
                final JsonNode echoNode = root.get("echo");
                if (echoNode != null && !echoNode.isNull()) {
                    echoWaiter.complete(echoNode.asText(), rawPayload);
                }
                return;
            }

            ParsedPayloadDTO dto = payloadParser.parseRawToDTO(root, rawPayload);

            if ("message".equals(dto.getPostType()) || "message_sent".equals(dto.getPostType())) {
                if (dto.getMessageType().equals("group")) {
                    log.info("[adapter.ws] receive group message, group id: {}, user id: {}, text: {}", dto.getGroupId(), dto.getUserId(), LogUtils.limitLen(dto.getRawText()));
                } else {
                    log.info("[adapter.ws] receive private message, user id: {}, text: {}", dto.getUserId(), LogUtils.limitLen(dto.getRawText()));
                }
                log.debug("[adapter.ws] raw payload: {}", rawPayload);

                /* 多线程异步解析命令 */
                CompletableFuture.runAsync(() -> {
                            try {
                                commandInvoker.invoke(dto);
                            } catch (BusinessException ignored) {
                            } catch (Exception e) {
                                log.error("async handle error", e);
                            }
                        }, executorService)
                        .orTimeout(CMD_THREAD_TIMEOUT, TimeUnit.SECONDS)
                        .whenComplete((result, throwable) -> {
                            if (throwable != null) {
                                log.warn("Command execution timed out or failed: {}", throwable.getMessage());
                            }
                        });
            }

        } catch (Exception e) {
            log.error("[adapter.ws] ws pipeline error", e);
        }
    }

//    private void writeResultToSession(WebSocketSession session, Object res) {
//        if (res == null || !session.isOpen()) return;
//        synchronized (session) {
//            try {
//                if (res instanceof Iterable<?> it) {
//                    for (Object o : it) {
//                        if (o == null) continue;
//                        session.sendMessage(new TextMessage(String.valueOf(o)));
//                    }
//                } else {
//                    session.sendMessage(new TextMessage(String.valueOf(res)));
//                }
//            } catch (Exception e) {
//                log.warn("[adapter.ws] send failed", e);
//            }
//        }
//    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("[adapter.ws] transport error", exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object sid = session.getAttributes().get("self_id");
        if (sid instanceof Long selfId) sessionRegistry.remove(selfId);
        int code = status.getCode();
        String reason = status.getReason();
        // 有些时候并不是我们主动希望断开的，可能是缓冲区溢出等问题，例如 code 1009，打印信息
        log.info("[adapter.ws] ws closed: {}, code: {}, reason: {}", session.getId(), code, reason);
        partialBuffer.remove(session.getId());
    }
}
