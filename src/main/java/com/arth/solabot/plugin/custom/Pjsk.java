package com.arth.solabot.plugin.custom;

import com.arth.solabot.adapter.controller.ApiPaths;
import com.arth.solabot.adapter.sender.Sender;
import com.arth.solabot.adapter.sender.action.ActionChainBuilder;
import com.arth.solabot.core.bot.dto.ParsedPayloadDTO;
import com.arth.solabot.core.bot.invoker.annotation.BotCommand;
import com.arth.solabot.core.bot.invoker.annotation.BotPlugin;
import com.arth.solabot.core.infrastructure.LocalData;
import com.arth.solabot.core.infrastructure.cache.service.ImageCacheService;
import com.arth.solabot.core.infrastructure.database.mapper.PjskBindingMapper;
import com.arth.solabot.core.infrastructure.network.NetworkUtil;
import com.arth.solabot.core.infrastructure.network.service.ImgNetworkService;
import com.arth.solabot.plugin.custom.pjsk.func.General;
import com.arth.solabot.plugin.custom.pjsk.func.Mysekai;
import com.arth.solabot.plugin.custom.pjsk.func.Suite;
import com.arth.solabot.plugin.system.Plugin;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@BotPlugin(name = {"pjsk"})
@RequiredArgsConstructor
public class Pjsk extends Plugin {

    @Getter
    public final String helpText = "请通过 /help 查看 pjsk 模块具体的命令";

    @Override
    @BotCommand(command = "index")
    public void index(ParsedPayloadDTO payload) {
        sender.replyText(payload, "请接 pjsk 模块的具体命令哦");
    }

    @Override
    @BotCommand(command = "help")
    public void help(ParsedPayloadDTO payload) {
        sender.replyText(payload, helpText);
    }

    // ***** ============= General ============= *****

    @BotCommand(command = {"绑定", "bind"})
    public void bind(ParsedPayloadDTO payload, List<String> args) {
        General.bind(getCtx(), payload, args);
    }

    @BotCommand(command = {"查询绑定", "bound"})
    public void bound(ParsedPayloadDTO payload) {
        General.bound(getCtx(), payload);
    }

    @BotCommand(command = {"默认服务器", "默认", "default"})
    public void setDefaultServerRegion(ParsedPayloadDTO payload, List<String> args) {
        General.setDefaultServerRegion(getCtx(), payload, args);
    }

    // ***** ============= Suite ============= *****

    @BotCommand(command = {"box", "卡牌一览"})
    public void box(ParsedPayloadDTO payload,List<String> args) {
        Suite.box(getCtx(), payload,args);
    }



    // ***** ============= Mysekai ============= *****

    @BotCommand(command = {"msm", "msr"})
    public void msm(ParsedPayloadDTO payload) {
        Mysekai.msm(getCtx(), payload);
    }

    @BotCommand(command = {"msm", "msr"})
    public void msm(ParsedPayloadDTO payload, List<String> args) {
        if (args == null || args.isEmpty()) {
            msm(payload);
        } else {
            Mysekai.msm(getCtx(), payload, args.get(0));
        }
    }

    // ***** ============= ctx ============= *****
    // ***** ============= ctx ============= *****
    // ***** ============= ctx ============= *****

    private volatile CoreBeanContext ctx;
    private final Sender sender;
    private final ApiPaths apiPaths;
    private final LocalData localData;
    private final ActionChainBuilder actionChainBuilder;
    private final NetworkUtil networkUtil;
    private final ImageCacheService imageCacheService;
    private final PjskBindingMapper pjskBindingMapper;
    private final ObjectMapper objectMapper;
    private final ImgNetworkService imgService;
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Shanghai"));

    @Value("${app.parameter.plugin.pjsk.external-api.hrk.suite-api}")
    private String suiteApi;
    @Value("${app.parameter.plugin.pjsk.external-api.hrk.mysekai-api}")
    private String mysekaiApi;
    @Value("${app.parameter.plugin.pjsk.external-api.uni.thumbnail-api}")
    private String thumbnailApi;

    @Value("${app.parameter.plugin.pjsk.devel_mode}")
    private boolean devel_mode;
    @Value("${app.parameter.plugin.pjsk.cache_cards_thumbnails}")
    private boolean cache_cards_thumbnails;

    /**
     * 懒汉式线程安全获取 ctx
     *
     * @return
     */
    public CoreBeanContext getCtx() {
        if (ctx == null) {
            synchronized (this) {
                if (ctx == null) {
                    ctx = new CoreBeanContext(
                            sender,
                            apiPaths,
                            localData,
                            actionChainBuilder,
                            networkUtil,
                            imageCacheService,
                            pjskBindingMapper,
                            objectMapper,
                            imgService,
                            dateTimeFormatter,
                            suiteApi,
                            mysekaiApi,
                            thumbnailApi,
                            devel_mode,
                            cache_cards_thumbnails
                    );
                }
            }
        }
        return ctx;
    }

    public interface BeanContext {
        Sender sender();
        ApiPaths apiPaths();
        LocalData localData();
        ActionChainBuilder actionChainBuilder();
        NetworkUtil networkUtil();
        ImageCacheService imageCacheService();
        PjskBindingMapper pjskBindingMapper();
        ObjectMapper objectMapper();
        ImgNetworkService imgService();
        DateTimeFormatter dateTimeFormatter();
        String suiteApi();
        String mysekaiApi();
        String thumbnailApi();
        boolean devel_mode();
        boolean cache_cards_thumbnails();
    }

    public record CoreBeanContext(
            Sender sender,
            ApiPaths apiPaths,
            LocalData localData,
            ActionChainBuilder actionChainBuilder,
            NetworkUtil networkUtil,
            ImageCacheService imageCacheService,
            PjskBindingMapper pjskBindingMapper,
            ObjectMapper objectMapper,
            ImgNetworkService imgService,
            DateTimeFormatter dateTimeFormatter,
            String suiteApi,
            String mysekaiApi,
            String thumbnailApi,
            boolean devel_mode,
            boolean cache_cards_thumbnails) implements BeanContext {
    }
}