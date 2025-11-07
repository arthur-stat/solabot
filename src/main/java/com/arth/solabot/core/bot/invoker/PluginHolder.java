package com.arth.solabot.core.bot.invoker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 封装插件实例与其命令映射（函数式处理器）
 * - 相较于旧实现，不再暴露反射 Method（反射的实现太丑陋了）
 * - 支持 “别名 → 多候选（重载）”
 * - 支持 “别名 → 优选缓存”
 */
class PluginHolder {

    protected final Object instance;

    public final boolean isGlued;

    /**
     * 子命令别名（小写） -> 多个候选处理器（允许重载）
     */
    protected final Map<String, List<CommandHandler>> commandHandlers = new ConcurrentHashMap<>();

    /**
     * 计算后的优选处理器缓存（小写） -> 处理器
     */
    protected final Map<String, CommandHandler> preferredHandlers = new ConcurrentHashMap<>();

    PluginHolder(Object instance, boolean glued) {
        this.instance = instance;
        this.isGlued = glued;
    }

    void addHandler(String aliasLowerCase, CommandHandler handler) {
        commandHandlers.computeIfAbsent(aliasLowerCase, k -> new ArrayList<>()).add(handler);
    }

    Map<String, List<CommandHandler>> getHandlers() {
        return commandHandlers;
    }

    void setPreferred(String aliasLowerCase, CommandHandler h) {
        if (aliasLowerCase == null) aliasLowerCase = "";
        preferredHandlers.put(aliasLowerCase, h);
    }

    CommandHandler getPreferred(String aliasLowerCase) {
        if (aliasLowerCase == null) aliasLowerCase = "";
        return preferredHandlers.get(aliasLowerCase);
    }

    /**
     * 当前插件所支持的所有子命令别名（小写）
     */
    Set<String> aliases() {
        return commandHandlers.keySet();
    }
}

