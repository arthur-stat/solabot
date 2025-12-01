package com.arth.solabot.plugin.custom;

import com.arth.solabot.adapter.controller.ApiPaths;
import com.arth.solabot.adapter.sender.Sender;
import com.arth.solabot.adapter.sender.action.ForwardChainBuilder;
import com.arth.solabot.core.bot.dto.ParsedPayloadDTO;
import com.arth.solabot.core.bot.invoker.annotation.BotCommand;
import com.arth.solabot.core.bot.invoker.annotation.BotPlugin;
import com.arth.solabot.plugin.system.Plugin;
import lombok.RequiredArgsConstructor;

import java.util.List;

@BotPlugin(name = {"help"})
@RequiredArgsConstructor
public class Help extends Plugin {

    private final Sender sender;
    private final ForwardChainBuilder forwardChainBuilder;
    private final ApiPaths apiPaths;

    @Override
    @BotCommand(command = "index")
    public void index(ParsedPayloadDTO payload) {
        ForwardChainBuilder building = forwardChainBuilder.create().addCustomNode(payload.getSelfId(), "bot", n -> n.text("""
                        这里是 solabot，一只具有独立 java 后端的 bot，本世代为「ickk」，主要为翼遥烤群（某高校 pjsk 同好群）而设计，目前支持以下几个模块：
                          1. pjsk 啤酒烧烤
                          2. img 图片处理
                          3. 看看你的
                          4. live 直播订阅（暂不可用）
                          5. test 测试（仅测试用）
                        
                        命令的使用方法为 “/模块名 命令名 <参数>”，例如 /pjsk 绑定，注意大部分命令都是非紧凑的，要求以空格隔开命令与参数；
                        
                        可以通过 “/help 模块名” 或 “/模块名 help” 单独查看指定模块的帮助文档
                        
                        注：本 bot 服务端与 haruki、sakura 等其他烤 bot 没有任何关系，只是 bot 客户端也接了他们的 api。"""))
                .addCustomNode(payload.getSelfId(), "bot", n -> n.text("""
                        pjsk 啤酒烧烤模块目前支持以下命令：
                          - 绑定 <pjsk id> <可选 cn/tw/jp>: 绑定 pjsk 账号，默认国服
                          - 绑定 / 查询绑定: 查看 pjsk 账号的绑定
                          - 默认服务器 <cn/tw/jp>：切换默认服务器
                          - msm <可选 cn/tw/jp>: 查看所绑定的 mysekai 数据，默认国服
                          - box <-r> <可选 cn/tw/jp>: 查询 box，已实装，半成品，不加参数为按角色排序，-r参数为按稀有度降序排列，默认国服
                          - luna茶的组卡器，尚未实装
                        
                        可以访问""" + apiPaths.DOMAIN_NAME + "/upload.html在网站上手动上传suite与mysekai数据"));

//        if (payload.getGroupId() != null && Set.of(619096416L, 1036993047L, 570656202L, 992406250L, 916204609L, 793709714L).contains(payload.getGroupId())) {
        if (true) {  // 反正适用范围不广，随便用吧
            building.addCustomNode(payload.getSelfId(), "bot", n -> n.text("""
                            我们的绑定功能没有接游戏 api，目前唯一的作用是定位自己的数据，所以输错了也不会有提示"""))
                    .addCustomNode(payload.getSelfId(), "bot", n -> n.text("""
                            👇要使用 mysekai 功能，iOS 请将使用下面的模块配置，以国服mysekai + Shadowracket为例（需要其他模块可联系我）：在 配置→模块→右上角➕︎号，填入下面这个地址："""))
                    .addCustomNode(payload.getSelfId(), "bot", n -> n.text(apiPaths.getShadowrocketModuleDownloadMysekaiCn()))
                    .addCustomNode(payload.getSelfId(), "bot", n -> n.text("""
                            模块的使用教程可以参考 https://bot.teaphenby.com/public/tutorial/tutorial.html，步骤大体相同，记得将模块url替换为我们的"""));
        } else {
            building.addCustomNode(payload.getSelfId(), "bot", n -> n.text("「当前群聊非翼遥啤酒烧烤大排档，烤森功能不可用，pjsk 模块剩余内容略」"));
        }

        building.addCustomNode(payload.getSelfId(), "bot", n -> n.text(pluginRegistryCenter.getPluginHelpText(Img.class)))
                .addCustomNode(payload.getSelfId(), "bot", n -> n.text(pluginRegistryCenter.getPluginHelpText(Gallery.class)))
                .addCustomNode(payload.getSelfId(), "bot", n -> n.text(pluginRegistryCenter.getPluginHelpText(Live.class)))
                .addCustomNode(payload.getSelfId(), "bot", n -> n.text(pluginRegistryCenter.getPluginHelpText(Test.class)));

        String json = (payload.getGroupId() != null) ? building.toGroupJson(payload.getGroupId()) : building.toPrivateJson(payload.getUserId());

        sender.pushActionJSON(payload.getSelfId(), json);
    }

    @BotCommand(command = "index")
    public void index(ParsedPayloadDTO payload, List<String> args) {
        for (String arg : args) {
            try {
                pluginRegistryCenter.callPluginHelp(payload, arg);
            } catch (Exception ignore) {

            }
        }
    }

    @Override
    public String getHelpText() {
        return "";
    }
}