package com.arth.solabot.plugin.system;

import com.arth.solabot.core.bot.dto.ParsedPayloadDTO;
import com.arth.solabot.core.bot.invoker.PluginRegistryCenter;
import com.arth.solabot.core.bot.invoker.annotation.BotCommand;
import com.arth.solabot.core.bot.invoker.annotation.BotPlugin;

public abstract class Plugin {

    protected PluginRegistryCenter pluginRegistryCenter;

    public void setPluginRegistry(PluginRegistryCenter pluginRegistry) {
        this.pluginRegistryCenter = pluginRegistry;
        registerTask();  // 注册定时任务，默认无，子类按需重写
    }

    @BotCommand(command = "index")
    public abstract void index(ParsedPayloadDTO payload);

    @BotCommand(command = "help")
    public void help(ParsedPayloadDTO payload) {
        pluginRegistryCenter.callPluginHelp(payload, this.getClass().getAnnotation(BotPlugin.class).name()[0]);
    }

    public abstract String getHelpText();

    protected void registerTask() {
    }
}
