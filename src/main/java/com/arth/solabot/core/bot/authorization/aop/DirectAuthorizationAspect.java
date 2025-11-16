package com.arth.solabot.core.bot.authorization.aop;

import com.arth.solabot.core.bot.authorization.annotation.DirectAuthInterceptor;
import com.arth.solabot.core.bot.authorization.model.AuthMode;
import com.arth.solabot.core.bot.authorization.model.AuthScope;
import com.arth.solabot.core.bot.dto.ParsedPayloadDTO;
import com.arth.solabot.core.infrastructure.exception.BadRequestException;
import com.arth.solabot.core.infrastructure.exception.ForbiddenException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationContext;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Aspect
@Component
@Order(0)
public class DirectAuthorizationAspect {

    private final ApplicationContext appCtx;
    private final ExpressionParser parser = new SpelExpressionParser();

    public DirectAuthorizationAspect(ApplicationContext appCtx) {
        this.appCtx = appCtx;
    }

    @Around("@annotation(com.arth.solabot.core.bot.authorization.annotation.DirectAuthInterceptor) || " +
            "@annotation(com.arth.solabot.core.bot.authorization.annotation.DirectAuthInterceptors)")
    public Object check(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        Object[] args = pjp.getArgs();

        // 1) 收集当前“方法上的”全部规则（可重复注解）
        DirectAuthInterceptor[] rules = method.getAnnotationsByType(DirectAuthInterceptor.class);
        if (rules.length == 0) return pjp.proceed();

        // 2) 直接使用 MethodBasedEvaluationContext 作为 SpEL 上下文
        MethodBasedEvaluationContext ctx =
                new MethodBasedEvaluationContext(
                        pjp.getTarget(),                     // 根对象（root object）
                        method,                              // 当前方法
                        args,                                // 实参
                        new DefaultParameterNameDiscoverer() // 参数名发现器
                );
        // 使 SpEL 支持 @bean 引用
        ctx.setBeanResolver(new BeanFactoryResolver(appCtx));

        ParsedPayloadDTO payload = findArgByType(args, ParsedPayloadDTO.class);
        if (payload == null) {
            throw new IllegalStateException("缺少 ParsedPayloadDTO 参数");
        }
        ctx.setVariable("payload", payload);

        // 3) USER 优先
        if (!evaluateScope(rules, AuthScope.USER, payload.getUserId(), ctx)) {
            throw new ForbiddenException("用户" + payload.getUserId() + "权限不足", "用户" + payload.getUserId() + "权限不足");
        }
        // 4) GROUP 其次
        if (!evaluateScope(rules, AuthScope.GROUP, payload.getGroupId(), ctx)) {
            throw new ForbiddenException("群组" + payload.getGroupId() + "权限不足", "群组" + payload.getGroupId() + "权限不足");
        }

        return pjp.proceed();
    }


    private boolean evaluateScope(DirectAuthInterceptor[] all, AuthScope scope, Long subjectId, EvaluationContext ctx) {
        List<DirectAuthInterceptor> rs = Arrays.stream(all)
                .filter(r -> r.scope() == scope)
                .collect(Collectors.toList());
        if (rs.isEmpty()) return true;

        if (subjectId == null) {
            return false;
        }

        for (DirectAuthInterceptor r : rs) {
            if (r.mode() == AuthMode.DENY) {
                Set<Long> targets = evalTargetsAsLongSet(r.targets(), ctx);
                if (targets.contains(subjectId)) return false;
            }
        }

        boolean hasWhitelist = false;
        boolean hitWhitelist = false;
        for (DirectAuthInterceptor r : rs) {
            if (r.mode() == AuthMode.ALLOW) {
                hasWhitelist = true;
                Set<Long> targets = evalTargetsAsLongSet(r.targets(), ctx);
                if (targets.contains(subjectId)) {
                    hitWhitelist = true;
                    break;
                }
            }
        }
        return !hasWhitelist || hitWhitelist;
    }

    private <T> T findArgByType(Object[] args, Class<T> type) {
        for (Object a : args) {
            if (a != null && type.isAssignableFrom(a.getClass())) return type.cast(a);
        }
        return null;
    }

    /**
     * 解析 SpEL，仅接受 Number / 原生数组 / Iterable<? extends Number>，转为 Set<Long>
     */
    private Set<Long> evalTargetsAsLongSet(String spel, EvaluationContext ctx) {
        Set<Long> out = new LinkedHashSet<>();
        if (spel == null || spel.trim().isEmpty()) return out;

        Object val = parser.parseExpression(spel).getValue(ctx);
        if (val == null) return out;

        // 单个数字
        if (val instanceof Number) {
            out.add(((Number) val).longValue());
            return out;
        }

        // 原生数组 / 对象数组
        Class<?> clazz = val.getClass();
        if (clazz.isArray()) {
            int len = Array.getLength(val);
            for (int i = 0; i < len; i++) {
                Object e = Array.get(val, i);
                if (e instanceof Number) {
                    out.add(((Number) e).longValue());
                } else if (e != null) {
                    // ignored non-numeric elements
                }
            }
            return out;
        }

        // Iterable
        if (val instanceof Iterable) {
            for (Object e : (Iterable<?>) val) {
                if (e instanceof Number) {
                    out.add(((Number) e).longValue());
                } else if (e != null) {
                    // ignored non-numeric elements
                }
            }
            return out;
        }

        throw new BadRequestException(
                "targets only accept Number / primitive arrays / Iterable<Number>, actual:" + clazz,
                "授权 targets 参数类型不支持");
    }

}
