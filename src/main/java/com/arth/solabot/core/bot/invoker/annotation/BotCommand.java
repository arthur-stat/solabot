package com.arth.solabot.core.bot.invoker.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface BotCommand {
    /**
     * 命令别名注解
     * 例如，将 "/img rollover" 与 "/img 翻转" 映射到同一个命令
     */
    String[] command();
}