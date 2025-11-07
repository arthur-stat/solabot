package com.arth.solabot.core.bot.invoker.annotation;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface BotPlugin {
    /**
     * 插件模块别名注解
     * 例如，将 "/img" 与 "/图片" 映射到同一个插件模块
     */
    String[] name();

    /**
     * 是否启用粘合模式
     * 例如，是否将 “/看saki” 在未命中插件模块 “看saki” 时回退到 “看” 并尝试将 “saki” 解释为命令名与参数的组合
     * @return
     */
    boolean glued() default false;
}
