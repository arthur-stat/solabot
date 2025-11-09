package com.arth.solabot.plugin.custom;


import com.arth.solabot.adapter.fetcher.ReplyFetcher;
import com.arth.solabot.adapter.sender.Sender;
import com.arth.solabot.adapter.sender.action.ForwardChainBuilder;
import com.arth.solabot.core.bot.authorization.annotation.DirectAuthInterceptor;
import com.arth.solabot.core.bot.authorization.model.AuthMode;
import com.arth.solabot.core.bot.authorization.model.AuthScope;
import com.arth.solabot.core.bot.dto.ParsedPayloadDTO;
import com.arth.solabot.core.bot.dto.ReplayedMessagePayloadDTO;
import com.arth.solabot.core.bot.dto.replay.ImageRef;
import com.arth.solabot.core.bot.invoker.annotation.BotCommand;
import com.arth.solabot.core.bot.invoker.annotation.BotPlugin;
import com.arth.solabot.plugin.system.Plugin;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Objects;

@BotPlugin(name = {"test"})
@RequiredArgsConstructor
public class Test extends Plugin {

    private final Sender sender;
    private final ForwardChainBuilder forwardChainBuilder;
    private final ReplyFetcher replyFetcher;

    @Value("${app.domain-name}")
    private String endpoint;

    @Getter
    public final String helpText = """
            test 测试模块目前支持以下命令：
              - quanxian: 测试鉴权切面，硬编码仅允许 1093664084
              - zuse <delay_time>: 测试多线程异步
              - huifu: 测试 bot 回复
              - tu: 测试发图
              - shipin: 测试发视频
              - zhuanfa <QQid> <QQname> <text>: 测试链式构造合并转发消息
              - yinyong <args...>: 测试 bot 获取图片引用消息""";

    @BotCommand(command = "index")
    @Override
    public void index(ParsedPayloadDTO payload) {
        sender.replyText(payload, "未给出具体的 test 模块命令，或命令有误");
    }

    @BotCommand(command = "help")
    @Override
    public void help(ParsedPayloadDTO payload) {
        super.help(payload);
    }

    /**
     * 权限测试
     *
     * @param payload
     */
    @BotCommand(command = "quanxian")
    @DirectAuthInterceptor(
            scope = AuthScope.USER,
            mode = AuthMode.ALLOW,
            targets = "1093664084"
    )
    public void quanxian(ParsedPayloadDTO payload) {
        sender.sendText(payload, "test: group not blacklisted -> passed");
    }

    /**
     * 多线程异步测试
     * 参数解析测试
     *
     * @param payload
     */
    @BotCommand(command = "zuse")
    public void zuse(ParsedPayloadDTO payload, List<String> args) {
        long ms = 5000L;  // 默认延迟 5 秒
        try {
            if (args != null && !args.isEmpty()) {
                ms = Math.max(0, Long.parseLong(args.get(0)));
            }
        } catch (NumberFormatException ignore) {
        }

        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        sender.sendText(payload, "test: done after " + ms + " ms");
    }

    /**
     * 回复功能测试
     *
     * @param payload
     */
    @BotCommand(command = "huifu")
    public void huifu(ParsedPayloadDTO payload) {
        sender.replyText(payload, "test");
    }

    /**
     * 图片发送测试
     */
    @BotCommand(command = "tu")
    public void tu(ParsedPayloadDTO payload) {
        String url = endpoint + "/test//saki.jpg";
        sender.replyImage(payload, List.of(url, url, url));
    }

    /**
     * 视频发送测试
     *
     * @param payload
     */
    @BotCommand(command = "shipin")
    public void shipin(ParsedPayloadDTO payload) {
        String url = endpoint + "/test/icsk.mp4";
        sender.sendVideo(payload, url);
    }

    /**
     * 生成转发消息测试
     *
     * @param payload
     */
    @BotCommand(command = "zhuanfa")
    public void zhuanfa(ParsedPayloadDTO payload, List<String> args) {
        long idd = Long.parseLong(args.get(0));
        ForwardChainBuilder built = forwardChainBuilder.create()
                .addCustomNode(idd, args.get(1), n -> n.text(args.get(2)));

        String json = (payload.getGroupId() != null) ?
                built.toGroupJson(payload.getGroupId()) : built.toPrivateJson(payload.getUserId());

        sender.pushActionJSON(payload.getSelfId(), json);
    }

    /**
     * 测试 bot 获取非文本引用消息
     *
     * @param payload
     */
    @BotCommand(command = "yinyong")
    public void yinyong(ParsedPayloadDTO payload) {
        String replyMsgId = payload.getReplyToMessageId();
        if (replyMsgId == null || replyMsgId.isBlank()) {
            sender.sendText(payload, "请先『回复一条包含图片的消息』再发送 /test yinyong");
            return;
        }

        ReplayedMessagePayloadDTO r;
        try {
            r = replyFetcher.fetch(payload.getSelfId(), Long.parseLong(replyMsgId));
        } catch (Exception e) {
            sender.sendText(payload, "获取引用消息失败：" + e.getMessage());
            return;
        }
        if (r == null || r.getImages() == null || r.getImages().isEmpty()) {
            sender.sendText(payload, "引用的消息里没有图片（或实现未返回图片段）");
            return;
        }

        List<String> files = r.getImages().stream()
                .map(ImageRef::getSource)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        if (files.isEmpty()) {
            sender.sendText(payload, "引用的图片缺少可用的 url/file 字段");
            return;
        }

        sender.replyImage(payload, files);
    }

    /**
     * 测试 bot 获取参数
     *
     * @param payload
     */
    @BotCommand(command = "canshu")
    public void canshu(ParsedPayloadDTO payload, List<String> args) {
        sender.replyText(payload, args.toString());
    }
}