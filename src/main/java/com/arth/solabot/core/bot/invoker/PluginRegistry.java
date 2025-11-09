package com.arth.solabot.core.bot.invoker;

import com.arth.solabot.adapter.sender.Sender;
import com.arth.solabot.adapter.sender.action.ForwardChainBuilder;
import com.arth.solabot.core.bot.dto.ParsedPayloadDTO;
import com.arth.solabot.core.bot.exception.InternalServerErrorException;
import com.arth.solabot.core.bot.invoker.annotation.BotCommand;
import com.arth.solabot.core.bot.invoker.annotation.BotPlugin;
import com.arth.solabot.plugin.system.Plugin;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.reflect.Modifier.isPublic;

/**
 * 扫描插件包 plugin 下带有 @BotPlugin 注解的 public 类及其带有 @BotCommand 注解的 public 方法，
 * 根据注解值建立命令映射并注册到运行期插件注册表中。
 * 支持 glue 模式、帮助文本查询及命令前缀匹配。
 */
@Slf4j
@Component
public class PluginRegistry {

    /**
     * 插件根包路径
     */
    private static final String PLUGIN_BASE_PACKAGE = "com.arth.solabot.plugin.custom";

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private Sender sender;

    @Resource
    private ForwardChainBuilder forwardChainBuilder;

    /**
     * 模块别名（小写） -> 插件持有者
     */
    private final Map<String, PluginHolder> pluginRegistryMap = new ConcurrentHashMap<>();

    /**
     * 模块简单类名 -> 帮助文本
     */
    private final Map<String, String> helpTextMap = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        log.info("[core.bot.invoker] scanning plugins under {}", PLUGIN_BASE_PACKAGE);
        try {
            ClassPathScanningCandidateComponentProvider scanner =
                    new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AnnotationTypeFilter(BotPlugin.class));

            for (var bd : scanner.findCandidateComponents(PLUGIN_BASE_PACKAGE)) {
                Class<?> clazz = Class.forName(bd.getBeanClassName());
                if (!isPublic(clazz.getModifiers())) continue;

                BotPlugin pluginAnn = clazz.getAnnotation(BotPlugin.class);
                if (pluginAnn == null) continue;

                // 创建未注册到容器的实例，手动注入 this，避免循环依赖
                Object instance = applicationContext.getAutowireCapableBeanFactory().createBean(clazz);
                if (instance instanceof Plugin plugin) plugin.setPluginRegistry(this);

                // plugin holder（带 glue 标记）
                PluginHolder holder = new PluginHolder(instance, pluginAnn.glued());

                // 扫描 public 方法并注册命令别名
                for (Method m : clazz.getMethods()) {
                    BotCommand cmdAnn = m.getAnnotation(BotCommand.class);
                    if (cmdAnn == null) continue;
                    if (!isPublic(m.getModifiers())) continue;

                    CommandHandler handler = createHandler(instance, m);
                    for (String alias : cmdAnn.command()) {
                        String key = alias == null ? "" : alias.trim().toLowerCase(Locale.ROOT);
                        holder.addHandler(key, handler);
                        log.info("[core.bot.invoker] command alias `{}` of plugin {} is registered", key, clazz.getSimpleName());
                    }
                }

                // 为每个模块别名注册
                for (String alias : pluginAnn.name()) {
                    String key = alias.trim().toLowerCase(Locale.ROOT);
                    if (pluginRegistryMap.putIfAbsent(key, holder) != null) {
                        log.warn("[core.bot.invoker] duplicate plugin alias detected: {}", key);
                    }
                }

                // 读取帮助文本
                if (instance instanceof Plugin p) {
                    String help = p.getHelpText();
                    if (help != null) {
                        helpTextMap.put(clazz.getSimpleName(), help);
                        for (String alias : pluginAnn.name()) {
                            if (alias != null && !alias.isBlank()) {
                                helpTextMap.putIfAbsent(alias.trim().toLowerCase(Locale.ROOT), help);
                            }
                        }
                    }
                }
                log.info("[core.bot.invoker] registered plugin: {} -> {}", Arrays.toString(pluginAnn.name()), clazz.getSimpleName());
            }
        } catch (Exception e) {
            log.error("[core.bot.invoker] failed to initialize PluginRegistry", e);
            throw new InternalServerErrorException(
                    "Internal Server Error: failed to initialize PluginRegistry",
                    "框架初始化失败，请检查插件注册逻辑。");
        }

        if (pluginRegistryMap.isEmpty()) {
            log.warn("[core.bot.invoker] no plugins found under {}", PLUGIN_BASE_PACKAGE);
        }
    }

    /**
     * 将插件方法绑定为 CommandHandler。
     * 支持以下签名：
     * ()、(ParsedPayloadDTO)、(ParsedPayloadDTO, List)、
     * (CommandChainContext)、(CommandChainContext, List)
     */
    private CommandHandler createHandler(Object instance, Method m) throws Exception {
        MethodHandles.Lookup lk = MethodHandles.lookup();
        Class<?>[] types = m.getParameterTypes();

        // === () ===
        if (types.length == 0) {
            MethodHandle mh = lk.unreflect(m).bindTo(instance).asType(MethodType.methodType(Object.class));
            return new CommandHandler() {
                @Override
                public Object handle(CommandChainContext chainCtx, ParsedPayloadDTO payload, List<String> args) throws Throwable {
                    return mh.invoke();
                }

                @Override
                public int score(ParsedPayloadDTO payload, List<String> args) {
                    return 1;
                }

                @Override
                public boolean acceptsArgs() {
                    return false;
                }
            };
        }

        // === (ParsedPayloadDTO) ===
        if (types.length == 1 && types[0] == ParsedPayloadDTO.class) {
            MethodHandle mh = lk.unreflect(m).bindTo(instance)
                    .asType(MethodType.methodType(Object.class, ParsedPayloadDTO.class));
            return new CommandHandler() {
                @Override
                public Object handle(CommandChainContext chainCtx, ParsedPayloadDTO payload, List<String> args) throws Throwable {
                    return mh.invoke(payload);
                }

                @Override
                public int score(ParsedPayloadDTO payload, List<String> args) {
                    return 3;
                }

                @Override
                public boolean acceptsArgs() {
                    return false;
                }
            };
        }

        // === (ParsedPayloadDTO, List) ===
        if (types.length == 2 && types[0] == ParsedPayloadDTO.class && List.class.isAssignableFrom(types[1])) {
            MethodHandle mh = lk.unreflect(m).bindTo(instance)
                    .asType(MethodType.methodType(Object.class, ParsedPayloadDTO.class, List.class));
            return new CommandHandler() {
                @Override
                public Object handle(CommandChainContext chainCtx, ParsedPayloadDTO payload, List<String> args) throws Throwable {
                    return mh.invoke(payload, args);
                }

                @Override
                public int score(ParsedPayloadDTO payload, List<String> args) {
                    return 5;
                }

                @Override
                public boolean acceptsArgs() {
                    return true;
                }
            };
        }

        // === (List) ===
        if (types.length == 1 && List.class.isAssignableFrom(types[0])) {
            MethodHandle mh = lk.unreflect(m).bindTo(instance)
                    .asType(MethodType.methodType(Object.class, List.class));
            return new CommandHandler() {
                @Override
                public Object handle(CommandChainContext chainCtx, ParsedPayloadDTO payload, List<String> args) throws Throwable {
                    return mh.invoke(args);
                }

                @Override
                public int score(ParsedPayloadDTO payload, List<String> args) {
                    return 2;
                }

                @Override
                public boolean acceptsArgs() {
                    return true;
                }
            };
        }

        // === (CommandChainContext) ===
        if (types.length == 1 && types[0] == CommandChainContext.class) {
            MethodHandle mh = lk.unreflect(m).bindTo(instance)
                    .asType(MethodType.methodType(Object.class, CommandChainContext.class));
            return new CommandHandler() {
                @Override
                public Object handle(CommandChainContext chainCtx, ParsedPayloadDTO payload, List<String> args) throws Throwable {
                    return mh.invoke(chainCtx);
                }

                @Override
                public int score(ParsedPayloadDTO payload, List<String> args) {
                    return 5;
                }

                @Override
                public boolean acceptsArgs() {
                    return false;
                }
            };
        }

        // === (CommandChainContext, List) ===
        if (types.length == 2 && types[0] == CommandChainContext.class && List.class.isAssignableFrom(types[1])) {
            MethodHandle mh = lk.unreflect(m).bindTo(instance)
                    .asType(MethodType.methodType(Object.class, CommandChainContext.class, List.class));
            return new CommandHandler() {
                @Override
                public Object handle(CommandChainContext chainCtx, ParsedPayloadDTO payload, List<String> args) throws Throwable {
                    return mh.invoke(chainCtx, args);
                }

                @Override
                public int score(ParsedPayloadDTO payload, List<String> args) {
                    return 7;
                }

                @Override
                public boolean acceptsArgs() {
                    return true;
                }
            };
        }

        throw new InternalServerErrorException(
                "Internal Server Error: unsupported plugin method signature",
                "服务器内部错误：不支持的插件方法签名（仅支持 (), (Payload), (Payload,List), (Ctx), (Ctx,List)）");
    }

    /* ===================== 运行期查询 ===================== */

    PluginHolder getPluginHolder(String pluginName) {
        if (pluginName == null) return null;
        return pluginRegistryMap.get(pluginName.toLowerCase(Locale.ROOT));
    }

    /**
     * 按“最长前缀”在原始命令串（去掉首个 '/'）中匹配 glue=true 的模块别名。
     *
     * @param rawNoSlash 去掉首个'/'后的原始命令串，已做 trim 与多空格压缩，但未再做分词
     */
    GlueMatch matchGlueByLongestPrefix(String rawNoSlash) {
        String s = rawNoSlash.toLowerCase(Locale.ROOT);
        GlueMatch best = null;
        for (Map.Entry<String, PluginHolder> e : pluginRegistryMap.entrySet()) {
            String alias = e.getKey().toLowerCase(Locale.ROOT);
            PluginHolder holder = e.getValue();
            if (!holder.isGlued) continue;
            if (s.startsWith(alias)) {
                if (best == null || alias.length() > best.matchedAlias.length()) {
                    best = new GlueMatch(alias, holder);
                }
            }
        }
        return best;
    }

    public String getPluginHelpText(String simplePluginName) {
        return helpTextMap.getOrDefault(simplePluginName, "（暂无帮助文本）");
    }

    public String getPluginHelpText(Class<? extends Plugin> plugin) {
        String[] simplePluginName = plugin.getAnnotation(BotPlugin.class).name();
        return getPluginHelpText(simplePluginName[0]);
    }

    public void callPluginHelp(ParsedPayloadDTO payload, String pluginName) {
        Plugin aInstance = (Plugin) pluginRegistryMap.get(pluginName.toLowerCase(Locale.ROOT)).instance;
        String helpTextStr = aInstance.getHelpText();
        if (helpTextStr == null) {
            sender.replyText(payload, "不存在名为 \"" + pluginName + "\" 的插件/模块，请检查输入。");
            return;
        }

        ForwardChainBuilder building = forwardChainBuilder.create()
                .addCustomNode(payload.getSelfId(), "bot", n -> n.text("下面是 " + pluginName + " 模块的帮助文本"))
                .addCustomNode(payload.getSelfId(), "bot", n -> n.text(helpTextStr));

        String json = (payload.getGroupId() != null)
                ? building.toGroupJson(payload.getGroupId())
                : building.toPrivateJson(payload.getUserId());
        sender.pushActionJSON(payload.getSelfId(), json);
    }

    /* ============== glue 匹配结果封装 ============== */

    static final class GlueMatch {
        final String matchedAlias;  // 小写
        final PluginHolder holder;

        GlueMatch(String matchedAlias, PluginHolder holder) {
            this.matchedAlias = matchedAlias;
            this.holder = holder;
        }
    }
}