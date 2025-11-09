package com.arth.solabot.adapter.fetcher;

import com.arth.solabot.core.bot.dto.ReplayedMessagePayloadDTO;

public interface ReplyFetcher {

    /**
     * 获取当前消息所引用的消息
     *
     * @param selfId
     * @param messageId
     * @return
     */
    ReplayedMessagePayloadDTO fetch(long selfId, long messageId);
}