package com.arth.solabot.plugin.custom;

import com.arth.solabot.adapter.io.SessionRegistry;
import com.arth.solabot.adapter.sender.Sender;
import com.arth.solabot.core.bot.authorization.annotation.DirectAuthInterceptor;
import com.arth.solabot.core.bot.authorization.model.AuthMode;
import com.arth.solabot.core.bot.authorization.model.AuthScope;
import com.arth.solabot.core.bot.dto.ParsedPayloadDTO;
import com.arth.solabot.core.bot.exception.ExternalServiceErrorException;
import com.arth.solabot.core.bot.invoker.annotation.BotCommand;
import com.arth.solabot.core.bot.invoker.annotation.BotPlugin;
import com.arth.solabot.core.general.database.domain.StreamerAlias;
import com.arth.solabot.core.general.database.domain.StreamerSubscription;
import com.arth.solabot.core.general.database.mapper.StreamerAliasMapper;
import com.arth.solabot.core.general.database.mapper.StreamerSubscriptionMapper;
import com.arth.solabot.core.general.database.service.StreamerAliasService;
import com.arth.solabot.core.general.database.service.StreamerSubscriptionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 该插件目前还不能正常使用。目前尚不能够自动检测到开播后推送订阅消息，处于尚未完成的状态。
 */
@Slf4j
@BotPlugin(name = {"live"})
@RequiredArgsConstructor
public class Live extends Plugin {

    private final Sender sender;
    private final ObjectMapper objectMapper;
    private final StreamerAliasMapper streamerAliasMapper;
    private final StreamerAliasService streamerAliasService;
    private final StreamerSubscriptionMapper streamerSubscriptionMapper;
    private final StreamerSubscriptionService subscriptionService;
    private final SessionRegistry sessionRegistry;
    private final WebClient webClient;
    private final TaskScheduler taskScheduler;

    final Map<String, Long> aliasToStreamId = new ConcurrentHashMap<>();
    final Map<Long, Boolean> isLiving = new ConcurrentHashMap<>();
    final Map<Long, List<UserInfo>> streamIdToSubscription = new ConcurrentHashMap<>();

    @Value("${app.parameter.plugin.live.query-time-gap}")
    private int queryTimeGap;

    @Getter
    public final String helpText = """
            live 模块用于b站直播订阅推送
              - 查房 <主播别名/房间号>: 查询直播间是否开播
              - 订阅 <主播别名/房间号>: 订阅直播间开播消息
              - 退订 <主播别名/房间号>: 退订直播间开播消息
              - alias <房间号> <别名>: 为房间号起别名（比如炫狗）
              - dev: 打印所有live表的数据以供测试
            
            目前硬编码的主播名有「炫狗」""";

    @PostConstruct
    public void init() {
        LambdaQueryWrapper<StreamerAlias> qwAlias = new LambdaQueryWrapper<>();
        qwAlias.select(StreamerAlias::getAlias, StreamerAlias::getStreamId);
        List<StreamerAlias> aliases = streamerAliasMapper.selectList(qwAlias);

        for (StreamerAlias streamInfo : aliases) {
            Long streamId = streamInfo.getStreamId();
            String alias = streamInfo.getAlias();
            aliasToStreamId.put(alias, streamId);
            isLiving.put(streamId, false);
        }

        LambdaQueryWrapper<StreamerSubscription> qwSubscription = new LambdaQueryWrapper<>();
        qwSubscription.select(StreamerSubscription::getStreamId, StreamerSubscription::getUserId, StreamerSubscription::getGroupId);
        List<StreamerSubscription> subscriptions = streamerSubscriptionMapper.selectList(qwSubscription);

        for (StreamerSubscription streamInfo : subscriptions) {
            Long streamId = streamInfo.getStreamId();
            Long userId = streamInfo.getUserId();
            Long groupId = streamInfo.getGroupId();
            streamIdToSubscription.computeIfAbsent(streamId, key -> new ArrayList<>()).add(new UserInfo(userId, groupId));
        }

        taskScheduler.scheduleAtFixedRate(
                this::checkAllLiveStatus,
                Duration.ofMinutes(queryTimeGap)
        );
    }

    @Override
    @BotCommand(command = "index")
    public void index(ParsedPayloadDTO payload) {
        sender.replyText(payload, helpText);
    }

    @Override
    @BotCommand(command = "help")
    public void help(ParsedPayloadDTO payload) {
        super.help(payload);
    }

    @BotCommand(command = {"查询", "查房"})
    public void query(ParsedPayloadDTO payload, List<String> args) {
        for (String s : args) {
            Long streamId = aliasToStreamId.get(s);
            String nickname = null;

            if (streamId == null) {
                try {
                    streamId = Long.parseLong(s);
                } catch (NumberFormatException e) {
                    sender.replyText(payload, "未查询到别名，且输入也无法被视为直播间 ID");
                }
            } else {
                nickname = s;
            }

            try {
                if (checkLiving(streamId)) {
                    sender.replyText(payload, ((nickname != null) ? nickname : "房间号" + streamId) + "正在直播");
                } else {
                    sender.replyText(payload, ((nickname != null) ? nickname : "房间号" + streamId) + "没在直播");
                }
            } catch (Exception e) {
                sender.replyText(payload, "发生错误：" + e.getMessage());
            }
        }
    }

    @BotCommand(command = {"订阅"})
    public void subscribe(ParsedPayloadDTO payload, List<String> args) {
        Long userId = payload.getUserId();
        Long groupId = payload.getGroupId();

        for (String s : args) {
            Long streamId = aliasToStreamId.get(s);
            String nickname = null;

            if (streamId == null) {
                try {
                    streamId = Long.parseLong(s);
                } catch (NumberFormatException e) {
                    sender.replyText(payload, "未查询到别名，且输入也无法被视为直播间 ID");
                }
            } else {
                nickname = s;
            }

            try {
                LambdaQueryWrapper<StreamerSubscription> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(StreamerSubscription::getUserId, userId)
                        .eq(StreamerSubscription::getStreamId, streamId)
                        .eq(groupId != null, StreamerSubscription::getGroupId, groupId);

                boolean exists = streamerSubscriptionMapper.exists(queryWrapper);

                if (exists) {
                    sender.replyText(payload, (nickname != null) ?
                            "已经订阅过" + nickname + "的直播了" :
                            "已经订阅过房间号" + streamId + "的直播了");
                    continue;
                }

                StreamerSubscription subscription = new StreamerSubscription();
                subscription.setUserId(userId);
                subscription.setGroupId(groupId);
                subscription.setStreamId(streamId);

                boolean saved = subscriptionService.save(subscription);

                if (saved) {
                    streamIdToSubscription.computeIfAbsent(streamId, key -> new ArrayList<>()).add(new UserInfo(userId, groupId));
                    sender.replyText(payload, (nickname != null) ?
                            "成功订阅了" + nickname + "的直播" :
                            "成功订阅了房间号" + streamId + "的直播");
                } else {
                    sender.replyText(payload, "数据库错误，订阅失败");
                }
            } catch (Exception e) {
                sender.replyText(payload, "database error:" + e.getMessage());
            }
        }
    }

    @BotCommand(command = {"退订"})
    public void unsubscribe(ParsedPayloadDTO payload, List<String> args) {
        Long userId = payload.getUserId();
        Long groupId = payload.getGroupId();

        for (String s : args) {
            Long streamId = aliasToStreamId.get(s);
            String nickname = null;

            if (streamId == null) {
                try {
                    streamId = Long.parseLong(s);
                } catch (NumberFormatException e) {
                    sender.replyText(payload, "未查询到别名，且输入也无法被视为直播间ID");
                    continue;
                }
            } else {
                nickname = s;
            }

            try {
                LambdaQueryWrapper<StreamerSubscription> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(StreamerSubscription::getUserId, userId)
                        .eq(StreamerSubscription::getStreamId, streamId)
                        .eq(groupId != null, StreamerSubscription::getGroupId, groupId);

                boolean exists = streamerSubscriptionMapper.exists(queryWrapper);
                if (!exists) {
                    sender.replyText(payload, (nickname != null) ?
                            "尚未订阅" + nickname + "的直播" :
                            "尚未订阅房间号" + streamId + "的直播");
                    continue;
                }

                boolean removed = streamerSubscriptionMapper.delete(queryWrapper) > 0;

                if (removed) {
                    List<UserInfo> subscriptions = streamIdToSubscription.get(streamId);
                    if (subscriptions != null) {
                        subscriptions.removeIf(info -> info.userId().equals(userId) &&
                                (groupId == null || groupId.equals(info.groupId())));

                        if (subscriptions.isEmpty()) streamIdToSubscription.remove(streamId);
                    }

                    sender.replyText(payload, (nickname != null) ?
                            "已取消订阅" + nickname + "的直播" :
                            "已取消订阅房间号" + streamId + "的直播");
                } else {
                    sender.replyText(payload, "数据库错误，退订失败");
                }
            } catch (Exception e) {
                sender.replyText(payload, "数据库错误: " + e.getMessage());
            }
        }
    }

    @BotCommand(command = {"alias"})
    public void alias(ParsedPayloadDTO payload, List<String> args) {
        for (int i = 0; i < args.size(); i += 2) {
            Long streamId;
            String nickname = args.get(i + 1);
            try {
                streamId = Long.parseLong(args.get(i));
            } catch (NumberFormatException e) {
                sender.replyText(payload, "输入的直播间 ID 非法");
                continue;
            }

            Long streamerId = getStreamerIdByStreamId(streamId);

            try {
                LambdaQueryWrapper<StreamerAlias> aliasCheckWrapper = new LambdaQueryWrapper<>();
                aliasCheckWrapper.eq(StreamerAlias::getAlias, nickname);

                StreamerAlias aRecord = streamerAliasMapper.selectOne(aliasCheckWrapper);

                if (aRecord != null) {
                    sender.replyText(payload, "别名" + nickname + "已被赋予给ID为" + aRecord.getStreamerId() + "的主播");
                    continue;
                }

                LambdaQueryWrapper<StreamerAlias> duplicateCheckWrapper = new LambdaQueryWrapper<>();
                duplicateCheckWrapper.eq(StreamerAlias::getStreamId, streamId)
                        .eq(StreamerAlias::getAlias, nickname);

                boolean duplicateExists = streamerAliasMapper.exists(duplicateCheckWrapper);

                if (duplicateExists) {
                    sender.replyText(payload, "房间号" + streamId + "的主播已拥有别名" + nickname);
                    continue;
                }

                StreamerAlias newAlias = new StreamerAlias();
                newAlias.setStreamId(streamId);
                newAlias.setStreamerId(streamerId);
                newAlias.setAlias(nickname);

                int insertResult = streamerAliasMapper.insert(newAlias);

                if (insertResult > 0) {
                    aliasToStreamId.put(nickname, streamId);
                    sender.replyText(payload, "别名添加成功: " + nickname + " → " + streamId);
                } else {
                    sender.replyText(payload, "别名添加失败");
                }
            } catch (Exception e) {
                sender.replyText(payload, "数据库错误: " + e.getMessage());
            }
        }
    }

    @BotCommand(command = "dev")
    @DirectAuthInterceptor(scope = AuthScope.USER, mode = AuthMode.ALLOW, targets = "1093664084")
    public void dev(ParsedPayloadDTO payload) {
        sender.sendText(payload, "订阅表：" + streamIdToSubscription +
                "\n\n别名表：" + aliasToStreamId +
                "\n\n状态表：" + isLiving);
    }

    // ***** ============= helper ============= *****
    // ***** ============= helper ============= *****
    // ***** ============= helper ============= *****
    // ***** ============= helper ============= *****
    // ***** ============= helper ============= *****
    // ***** ============= helper ============= *****

    /**
     * 查询直播间是否正在直播
     *
     * @param streamId
     * @return
     */
    private boolean checkLiving(long streamId) {
        JsonNode rootNode = requestAPI(streamId);
        JsonNode liveStatusNode = rootNode.path("data").path("live_status");
        return liveStatusNode.isInt() && liveStatusNode.asInt() == 1;
    }

    /**
     * 通过 streamId 查询 streamerId
     *
     * @param streamId
     * @return
     */
    private long getStreamerIdByStreamId(long streamId) {
        JsonNode rootNode = requestAPI(streamId);
        JsonNode uidNode = rootNode.path("data").path("uid");
        if (uidNode.isLong()) {
            return uidNode.asLong();
        } else {
            throw new ExternalServiceErrorException("unexpected arg for streamerId from response");
        }
    }

    /**
     * 向B站API请求直播状态信息，返回解析后的Json响应
     *
     * @param streamId
     * @return
     */
    private JsonNode requestAPI(long streamId) {
        try {
            return webClient.get()
                    .uri("https://api.live.bilibili.com/room/v1/Room/room_init?id=" + streamId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .flatMap(json -> Mono.fromCallable(() -> objectMapper.readTree(json)))
                    .block(Duration.ofSeconds(5));
        } catch (Exception e) {
            log.error("Failed to request API", e);
            throw new ExternalServiceErrorException("Failed to request API", e.getMessage());
        }
    }

    /**
     * 用于推送直播间开播信息的方法
     */
    private void checkAllLiveStatus() {
        try {
            isLiving.forEach((streamId, wasLiving) -> {
                try {
                    boolean isNowLiving = checkLiving(streamId);

                    if (!wasLiving && isNowLiving) {
                        List<UserInfo> subscriptions = streamIdToSubscription.get(streamId);
                        if (subscriptions != null && !subscriptions.isEmpty()) {
                            List<WebSocketSession> sessions = sessionRegistry.getAll();
                            for (UserInfo subscription : subscriptions) {
                                for (WebSocketSession session : sessions) {
                                    sender.sendText(
                                            session,
                                            subscription.userId(),
                                            subscription.groupId(),
                                            "炫狗开播了，兄弟们撤！"
                                    );
                                }
                            }
                        }
                    }

                    isLiving.put(streamId, isNowLiving);

                } catch (Exception e) {
                    log.error("failed to check live status of stream id {}: {}", streamId, e.getMessage());
                }
            });

        } catch (Exception e) {
            log.error("get an unexpected exception: {}", e.getMessage(), e);
        }
    }

    private record UserInfo(Long userId, Long groupId) {
    }
}