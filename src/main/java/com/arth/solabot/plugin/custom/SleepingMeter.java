package com.arth.solabot.plugin.custom;


import com.arth.solabot.adapter.sender.Sender;
import com.arth.solabot.adapter.sender.action.ActionChainBuilder;
import com.arth.solabot.core.bot.dto.ParsedPayloadDTO;
import com.arth.solabot.core.bot.invoker.annotation.BotCommand;
import com.arth.solabot.core.bot.invoker.annotation.BotPlugin;
import com.arth.solabot.core.general.cache.service.StringCacheService;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@BotPlugin(value = {"sleep","g"},glued = true)
@RequiredArgsConstructor
public class SleepingMeter extends Plugin {

    private final StringCacheService stringCache;
    private final Sender sender;
    private final ActionChainBuilder actionChainBuilder;

    private static final String rawKey = "sleeping:%s:%s";

    private static final String morningTextSlept = " 早安！你睡了%d小时%d分钟%d秒，是群里第%s个早安的人！";
    private static final String morningTextSleptNonRank = " 早上好！你睡了%d小时%d分钟%d秒";

    private static final String nightText = " 晚安！";

    private static final String morningTextNonSlept = " 早上好！";


    @Override
    public void index(ParsedPayloadDTO payload) {

    }

    @Override
    public String getHelpText() {
        return """
               /gmorning ( /sleep morning ): 早安命令，若晚安过则同时输出睡觉时间
               /gnight ( /sleep night ): 晚安命令
               """;
    }

    @BotCommand({"早安","morning"})
    public void morning(ParsedPayloadDTO payload) {
        //sender.replyText(payload,"morning");
        User sUser = getSleepingUser(payload.getGroupId(),payload.getUserId());
        String replyText = "";
        if (sUser.isSlept()) {
            Duration duration = sUser.getSleepTime();
            replyText = String.format(morningTextSleptNonRank ,duration.toHoursPart()
                    ,duration.toMinutesPart()
                    ,duration.toSecondsPart());
        }else {
            replyText = morningTextNonSlept;
        }

        ActionChainBuilder actionChain = actionChainBuilder.create()
                .at(payload.getUserId())
                .text(replyText);

        String sendJson = payload.getMessageType().equals("group") ?
                actionChain.toGroupJson(payload.getGroupId()) :
                actionChain.toPrivateJson(payload.getUserId());

        sender.pushActionJSON(payload.getSelfId(), sendJson);
    }

    @BotCommand({"晚安","night"})
    public void night(ParsedPayloadDTO payload) {
        setSleepingTime(payload.getGroupId(),payload.getUserId(),payload.getTime());

        ActionChainBuilder actionChain = actionChainBuilder.create()
                .at(payload.getUserId())
                .text(nightText);

        String sendJson = payload.getMessageType().equals("group")?
                actionChain.toGroupJson(payload.getGroupId()) :
                actionChain.toPrivateJson(payload.getUserId());

        sender.pushActionJSON(payload.getSelfId(), sendJson);
    }

    private User getSleepingUser(long groupId,long userId){
        long currentTime = LocalDateTime.now().toEpochSecond(ZoneOffset.of("+8"));

        AtomicLong atomicSleepingTimeLong = new AtomicLong(0L);
        Optional<String> optionalString = Optional.ofNullable(getUserSleepingTime(groupId,userId));
        optionalString.ifPresent(time-> atomicSleepingTimeLong.set(Long.parseLong(time)));

        return new User(userId,atomicSleepingTimeLong.get(),currentTime,0);
    }

    /**
     *  从redis获取用户晚安时间
     * @param groupId
     * @param userId
     * @return String / null
     */
    @Nullable
    private String getUserSleepingTime(long groupId,long userId){
        return stringCache.getStringKey(getFormattedKey(groupId,userId));
    }


    /**
     *  key : sleeping:(groupId):(userId) value: timestamp
     * @param groupId
     * @param userId
     * @param timestamp
     */

    private void setSleepingTime(long groupId,long userId,long timestamp){
        stringCache.setStringKey(getFormattedKey(groupId,userId),String.valueOf(timestamp),1440);
    }

    /**
     * 格式化 key 为 sleeping:(groupId):(userId) 格式
     * @param groupId
     * @param userId
     * @return
     */
    private static String getFormattedKey(long groupId,long userId){
        return String.format(rawKey,groupId,userId);
    }

    private record User(long userId,long sleepTime,long getUpTime,int rank) {

        public boolean isSlept(){
            return sleepTime > 0 && sleepTime < getUpTime;
        }
        public Duration getSleepTime() {
            if (!isSlept()) {
                return Duration.ZERO;
            }
            //默认Asia,Shanghai +8
            LocalDateTime sleep = LocalDateTime.ofInstant(Instant.ofEpochSecond(sleepTime), ZoneId.of("+8"));
            LocalDateTime now = LocalDateTime.ofInstant(Instant.ofEpochSecond(getUpTime), ZoneId.of("+8"));
            return Duration.between(sleep, now);
        }

    }

}
