package com.arth.solabot.core.bot.invoker;

import com.arth.solabot.core.bot.dto.ParsedPayloadDTO;
import com.arth.solabot.core.bot.exception.BusinessException;
import com.arth.solabot.core.bot.exception.CommandNotFoundException;
import com.arth.solabot.core.bot.exception.InternalServerErrorException;
import com.arth.solabot.plugin.system.DefaultStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 指令调用器：
 * 入口按 “原空格解析 → 失败时 glue 最长前缀匹配模块 → 粘合解析” 的顺序进行，
 * 以支持常规命令与“粘合命令”的统一解析与执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommandInvoker {

    private final PluginRegistryCenter pluginRegistry;
    private final DefaultStrategy defaultStrategy = new DefaultStrategy();

    /**
     * 入口：解析并执行命令
     */
    public Object invoke(ParsedPayloadDTO payload) {
        try {
            String commandText = payload.getCommandText();

            if (commandText == null) {
                defaultStrategy.defaultHandle(payload);
                return null;
            }

            commandText = commandText.stripLeading();
            if (commandText.isEmpty() || commandText.charAt(0) != '/') {
                defaultStrategy.defaultHandle(payload);
                return null;
            }

            Parsed parsed = parse(commandText);
            if (parsed == null) return null;

            // 1) 常规精准匹配：root 作为模块别名
            String parsedRoot = parsed.root();
            log.info("[core.bot.invoker] plugin calling detected: {}", parsedRoot);

            PluginHolder holder = resolvePluginHolder(parsedRoot);
            List<String> stepsTokens = parsed.subAndArgs();

            // 2) 常规失败 → 尝试 glue 模块“最长前缀匹配”
            if (holder == null) {
                PluginRegistryCenter.GlueMatch gm = pluginRegistry.matchGlueByLongestPrefix(parsed.rawNoSlash());
                if (gm != null) {
                    holder = gm.holder;
                    String rest = parsed.rawNoSlash().substring(gm.matchedAlias.length()).trim();
                    stepsTokens = rest.isEmpty() ? List.of() : new ArrayList<>(Arrays.asList(rest.split(" ")));
                    log.info("[core.bot.invoker] glue matched plugin `{}`; rest=`{}`", gm.matchedAlias, rest);
                    return runGrouped(payload, holder, groupStepsGlue(stepsTokens, holder));
                }
            }

            if (holder == null) {
                throw new CommandNotFoundException(
                        "[core.bot.invoker] plugin not found: " + parsedRoot,
                        "不存在名为 \"" + parsedRoot + "\" 的插件/模块，请检查输入。");
            }

            // 3) 常规子命令分组执行
            return runGrouped(payload, holder, groupSteps(stepsTokens, holder));

        } catch (BusinessException e) {
            // 继续抛出，交给业务异常处理器处理
            throw e;
        } catch (Throwable e) {
            log.error("[core.bot.invoker] unexpected error while invoking", e);
            throw new InternalServerErrorException(
                    "Internal Server Error: " + e.getMessage(),
                    "服务器内部错误：未知异常");
        }
    }

    /**
     * 执行分组后的命令链
     */
    private Object runGrouped(ParsedPayloadDTO payload, PluginHolder holder, List<Step> steps) throws Throwable {
        CommandChainContext chainCtx = new CommandChainContext(payload);
        Object last = null;

        for (Step s : steps) {
            List<CommandHandler> candidates = getCandidatesWithIndexFallback(holder, s.name());
            if (candidates.isEmpty()) {
                throw new CommandNotFoundException(
                        "[core.bot.invoker] command not supported: " + s.name(),
                        "不支持的命令: " + s.name());
            }

            boolean hasArgs = s.args() != null && !s.args().isEmpty();
            String cacheKey = s.name() + (hasArgs ? ":args" : ":noarg");
            CommandHandler preferred = holder.getPreferred(cacheKey);
            if (preferred == null) {
                preferred = chooseBest(candidates, payload, s.args());
                holder.setPreferred(cacheKey, preferred);
            }
            Object ret = preferred.handle(chainCtx, payload, s.args());
            if (ret != null) chainCtx.setState(ret);
            last = ret;
        }

        return last;
    }

    /* ===================== 解析 ===================== */

    /**
     * 解析命令字符串，返回 Parsed 对象（根命令（首个 token）与 tokens，tokens 包含子命令与参数）
     *
     * @param raw 原始命令字符串
     * @return Parsed 对象
     */
    private Parsed parse(String raw) {
        String s = raw.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("^/+", "/");
        String cmdNoSlash = s.substring(1).trim();  // 去掉首个 '/'
        List<String> tokens = new ArrayList<>(Arrays.asList(cmdNoSlash.split(" ")));
        if (tokens.isEmpty()) return null;
        String root = tokens.remove(0).toLowerCase(Locale.ROOT);
        return new Parsed(root, tokens, cmdNoSlash);
    }

    /**
     * 常规：空格分组
     */
    private List<Step> groupSteps(List<String> tokens, PluginHolder holder) {
        List<Step> out = new ArrayList<>();
        if (tokens == null || tokens.isEmpty()) {
            out.add(new Step(indexAlias(), List.of()));
            return out;
        }

        String curName = null;
        List<String> curArgs = new ArrayList<>();
        Set<String> methodNames = holder.aliases();

        for (int idx = 0; idx < tokens.size(); idx++) {
            String tk = tokens.get(idx);
            String low = tk.toLowerCase(Locale.ROOT);
            boolean isAlias = methodNames.contains(low);

            if (isAlias) {
                if (curName == null) {
                    curName = low;
                    log.debug("[core.bot.invoker] matched first command: {}", curName);
                } else {
                    // 分组阶段的贪心策略：只要当前命令存在 “任一接参实现”，就把该 token 吞为参数
                    boolean greedy = acceptsArgsForGrouping(holder, curName);
                    if (greedy) {
                        curArgs.add(tk);
                        log.debug("[core.bot.invoker] matched args: {}", tk);
                        continue;
                    }
                    // 当前命令不接参，结算并切换到新命令
                    out.add(new Step(curName, List.copyOf(curArgs)));
                    curName = low;
                    curArgs = new ArrayList<>();
                    log.debug("[core.bot.invoker] matched next command: {}", curName);
                }
            } else {
                // 普通参数
                if (curName == null) {
                    // 首个 token 不是子命令别名时，启动 index 作为当前命令来承接参数
                    curName = indexAlias();
                    log.debug("[core.bot.invoker] matched args: {}", tk);
                }
                curArgs.add(tk);
            }
        }

        if (curName == null) curName = indexAlias();
        out.add(new Step(curName, List.copyOf(curArgs)));
        return out;
    }

    /**
     * 粘合模式：第一个 token 可能是“子命令+参数”贴在一起
     * 规则：
     * - 在 tokens[0] 上对 methodNames 做“最长前缀”匹配；
     * - 命中则：子命令=前缀，余串（若非空）作为第一个参数；其余 tokens 仍按普通参数；
     * - 未命中则：回落到 index。
     */
    private List<Step> groupStepsGlue(List<String> tokens, PluginHolder holder) {
        List<Step> out = new ArrayList<>();
        if (tokens == null || tokens.isEmpty()) {
            out.add(new Step(indexAlias(), List.of()));
            return out;
        }

        Set<String> methodNames = holder.aliases();
        String first = tokens.get(0);
        String firstLow = first.toLowerCase(Locale.ROOT);

        String bestAlias = null;
        for (String a : methodNames) {
            if (firstLow.startsWith(a)) {
                if (bestAlias == null || a.length() > bestAlias.length()) bestAlias = a;
            }
        }

        if (bestAlias == null) {
            // 没有任何子命令前缀命中，整个输入交给 index
            out.add(new Step(indexAlias(), List.copyOf(tokens)));
            return out;
        }

        List<String> args = new ArrayList<>();
        String remain = first.substring(bestAlias.length());
        if (!remain.isEmpty()) args.add(remain);
        if (tokens.size() > 1) args.addAll(tokens.subList(1, tokens.size()));

        out.add(new Step(bestAlias, List.copyOf(args)));
        return out;
    }

    /* ===================== 选择实现 ===================== */

    private List<CommandHandler> getCandidatesWithIndexFallback(PluginHolder holder, String aliasLower) {
        Map<String, List<CommandHandler>> all = holder.getHandlers();
        List<CommandHandler> list = all.get(aliasLower);
        if (list != null && !list.isEmpty()) return list;
        list = all.get(indexAlias());  // 这里 "" 视为 index
        if (list != null && !list.isEmpty()) return list;
        list = all.get("index");
        if (list != null && !list.isEmpty()) return list;
        return List.of();
    }

    /**
     * 评分择优：ctx+args > ctx > payload+args > payload > args > none
     */
    private CommandHandler chooseBest(List<CommandHandler> cands, ParsedPayloadDTO payload, List<String> args) {
        boolean hasArgs = args != null && !args.isEmpty();
        int best = Integer.MIN_VALUE;
        CommandHandler bestH = null;
        for (CommandHandler h : cands) {
            int s = h.score(payload, args);
            if (hasArgs && !h.acceptsArgs()) {
                s -= 1000;  // 带参但命令不接参，狠狠惩罚
            } else if (!hasArgs && h.acceptsArgs()) {
                s -= 100;   // 无参但命令接参，惩罚
            } else {
                s += 10;    // 匹配期望，加分
            }
            if (s > best) {
                best = s;
                bestH = h;
            }
        }
        return bestH != null ? bestH : cands.get(0);
    }

    private String indexAlias() {
        return "";
    }

    private PluginHolder resolvePluginHolder(String rootKey) {
        return pluginRegistry.getPluginHolder(rootKey);
    }

    /**
     * 分组阶段判断“是否接收参数”的专用逻辑：
     * 不做精细打分，只要存在任一接参实现，就认为当前命令可吞参（贪心参数），
     * 这样可避免在 args 仍为空时误选无参实现，导致把后续别名 token 当作“新命令”。
     */
    private boolean acceptsArgsForGrouping(PluginHolder holder, String aliasLower) {
        CommandHandler preferred = holder.getPreferred(aliasLower);
        if (preferred != null) return preferred.acceptsArgs();
        List<CommandHandler> candidates = getCandidatesWithIndexFallback(holder, aliasLower);
        if (candidates.isEmpty()) return false;
        for (CommandHandler h : candidates) {
            if (h.acceptsArgs()) return true;
        }
        return false;
    }

    /* ===================== data records ===================== */

    private record Parsed(String root, List<String> subAndArgs, String rawNoSlash) {
    }

    private record Step(String name, List<String> args) {
    }
}