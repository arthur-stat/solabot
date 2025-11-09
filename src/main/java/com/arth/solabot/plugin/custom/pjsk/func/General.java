package com.arth.solabot.plugin.custom.pjsk.func;

import com.arth.solabot.core.bot.dto.ParsedPayloadDTO;
import com.arth.solabot.core.bot.exception.BusinessException;
import com.arth.solabot.core.bot.exception.InvalidCommandArgsException;
import com.arth.solabot.core.bot.exception.ResourceNotFoundException;
import com.arth.solabot.core.infrastructure.database.domain.PjskBinding;
import com.arth.solabot.plugin.custom.Pjsk;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class General {

    private static final Map<String, Function<PjskBinding, String>> REGION_GETTERS = Map.of(
            "cn", PjskBinding::getCnPjskId,
            "jp", PjskBinding::getJpPjskId,
            "tw", PjskBinding::getTwPjskId,
            "kr", PjskBinding::getKrPjskId,
            "en", PjskBinding::getEnPjskId
    );

    private static final Map<String, BiConsumer<PjskBinding, String>> REGION_SETTERS = Map.of(
            "cn", PjskBinding::setCnPjskId,
            "jp", PjskBinding::setJpPjskId,
            "tw", PjskBinding::setTwPjskId,
            "kr", PjskBinding::setKrPjskId,
            "en", PjskBinding::setEnPjskId
    );

    private General() {
    }

    public static void bind(Pjsk.CoreBeanContext ctx, ParsedPayloadDTO payload, List<String> args) {
        if (args == null || args.isEmpty()) {
            bound(ctx, payload);
            return;
        }

        long userId = payload.getUserId();
        String pjskId = args.get(0);
        String region = (args.size() > 1) ? args.get(1) : "cn";
        String suffix = (args.size() > 1) ? "" : "默认国服，如果要绑定其他服请在 id 后空一格加上服务器地区简写，比如 jp、tw";

        if (!isRegionValid(region)) throw new InvalidCommandArgsException("unsupported arg of region: " + region);

        // 1. 查询
        PjskBinding binding = queryBinding(ctx, userId);

        // 2. 插入或更新
        if (binding == null) {
            // 不存在记录，插入
            PjskBinding newBinding = new PjskBinding();
            newBinding.setUserId(userId);
            newBinding.setDefaultServerRegion(region);
            BiConsumer<PjskBinding, String> setter = REGION_SETTERS.get(region);
            setter.accept(newBinding, pjskId);
            newBinding.setCreatedAt(new Date());
            newBinding.setUpdatedAt(new Date());
            ctx.pjskBindingMapper().insert(newBinding);
            ctx.sender().sendText(payload, "绑定成功！" + suffix);
        } else if (queryPjskIdByRegion(ctx, binding, region) == null) {
            // 存在记录但 region 为空，写入 region 对应的 id
            BiConsumer<PjskBinding, String> setter = REGION_SETTERS.get(region);
            setter.accept(binding, pjskId);
            binding.setUpdatedAt(new Date());
            ctx.pjskBindingMapper().updateById(binding);
            ctx.sender().sendText(payload, "绑定成功！" + suffix);
        } else {
            // 存在记录且 region 非空，更新
            BiConsumer<PjskBinding, String> setter = REGION_SETTERS.get(region);
            setter.accept(binding, pjskId);
            binding.setUpdatedAt(new Date());
            ctx.pjskBindingMapper().updateById(binding);
            ctx.sender().sendText(payload, "绑定已更新！" + suffix);
        }
    }

    public static void bound(Pjsk.CoreBeanContext ctx, ParsedPayloadDTO payload) {
        long userId = payload.getUserId();
        PjskBinding binding = queryBinding(ctx, userId);

        if (binding == null) {
            ctx.sender().replyText(payload, "You haven't bound any pjsk id yet");
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("查询到下述绑定：\n");

            for (var entry : REGION_GETTERS.entrySet()) {
                String region = entry.getKey();
                Function<PjskBinding, String> getter = entry.getValue();
                String pjskId = getter.apply(binding);
                if (pjskId != null && !pjskId.isEmpty()) {
                    sb.append("  - region: " + region + ", id: " + pjskId + "\n");
                }
            }

            sb.append("  - 默认服务器: " + binding.getDefaultServerRegion());
            ctx.sender().replyText(payload, sb.toString());
        }
    }

    public static void setDefaultServerRegion(Pjsk.CoreBeanContext ctx, ParsedPayloadDTO payload, List<String> args) {
        String region = (args != null && !args.isEmpty()) ? args.get(0) : "cn";
        if (!isRegionValid(region)) throw new InvalidCommandArgsException("unsupported arg of region: " + region);

        String suffix = (args != null && !args.isEmpty()) ? "" : "默认国服，如果要绑定其他服请在 id 后空一格加上服务器地区简写，比如 jp、tw";
        long userId = payload.getUserId();
        PjskBinding binding = queryBinding(ctx, userId);

        // 账户绑定记录不存在
        if (binding == null) {
            ctx.sender().replyText(payload, "You haven't bound any pjsk id yet");
        } else {
            Function<PjskBinding, String> getter = REGION_GETTERS.get(region);
            String pjskId = getter.apply(binding);

            // 账户绑定记录存在，但该 region 对应的 id 为空
            if (pjskId == null || !pjskId.isEmpty()) {
                ctx.sender().replyText(payload, region + " 尚未绑定 id，不能设置为默认服务器");
            } else {
                // 账户绑定记录存在，且 region 对应的 id 非空
                binding.setDefaultServerRegion(region);
                ctx.sender().replyText(payload, "成功修改默认服务器为 " + region + "！" + suffix);
            }
        }
    }

    // ***** ============= query helper ============= *****
    // ***** ============= query helper ============= *****
    // ***** ============= query helper ============= *****

    /**
     * 从数据库中查询游戏 id，其中 region 允许为 null 或空串，表示查询绑定的默认服务器的 id
     *
     * @param ctx
     * @param binding
     * @param region
     * @return 如果对应 region 的绑定 id 不存在，返回 null
     */
    public static String queryPjskIdByRegion(Pjsk.CoreBeanContext ctx, PjskBinding binding, String region) throws BusinessException {
        if (binding == null) throw new ResourceNotFoundException("binding not found");
        if (region == null || region.isEmpty()) return queryDefaultPjskId(ctx, binding).pjskId();
        region = region.toLowerCase();
        if (!isRegionValid(region)) throw new InvalidCommandArgsException("unsupported arg of region: " + region);
        Function<PjskBinding, String> getter = REGION_GETTERS.get(region);
        return getter.apply(binding);
    }

    /**
     * 从数据库中查询游戏 id，其中 region 允许为 null 或空串，表示查询绑定的默认服务器的 id
     *
     * @param ctx
     * @param binding
     * @param region
     * @return 如果对应 region 的绑定 id 不存在，返回默认绑定的 region
     */
    public static IdRegionPair queryPjskIdOrDefault(Pjsk.CoreBeanContext ctx, PjskBinding binding, String region) throws BusinessException {
        if (binding == null) throw new ResourceNotFoundException("binding not found");
        if (region == null || region.isEmpty()) return queryDefaultPjskId(ctx, binding);
        region = region.toLowerCase();
        if (!isRegionValid(region)) throw new InvalidCommandArgsException("unsupported arg of region: " + region);
        Function<PjskBinding, String> getter = REGION_GETTERS.get(region);
        String pjskId = getter.apply(binding);
        if (pjskId == null || pjskId.isEmpty()) {
            region = binding.getDefaultServerRegion();
            getter = REGION_GETTERS.get(region);
            pjskId = getter.apply(binding);
        }
        return new IdRegionPair(pjskId, region);
    }

    public static IdRegionPair queryDefaultPjskId(Pjsk.CoreBeanContext ctx, PjskBinding binding) {
        String defaultRegion = binding.getDefaultServerRegion();
        Function<PjskBinding, String> getter = REGION_GETTERS.get(defaultRegion);
        return new IdRegionPair(getter.apply(binding), defaultRegion);
    }

    public static PjskBinding queryBinding(Pjsk.CoreBeanContext ctx, long userId) {
        LambdaQueryWrapper<PjskBinding> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PjskBinding::getUserId, userId);
        return ctx.pjskBindingMapper().selectOne(queryWrapper);
    }

    public static boolean isRegionValid(String region) {
        return REGION_GETTERS.containsKey(region);
    }

    public record IdRegionPair(String pjskId, String region) {
    }
}
