package org.example.hook;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostCallEvent;
import io.agentscope.core.hook.PreCallEvent;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 事件 触发时机 典型用途
 * PreCallEventAgent 调用前参数校验、权限检查
 * PostCallEventAgent 调用后日志记录、结果后处理
 * PreToolCallEvent 工具调用前参数拦截、审计
 * PostToolCallEvent 工具调用后结果转换、缓存
 * PreModelCallEventLLM 调用前Prompt 注入、Token 统计
 * PostModelCallEventLLM 调用后响应过滤、成本统计
 */
@Slf4j
public class LoggingHook implements Hook {

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        // Agent 启动前
        if (event instanceof PreCallEvent) {
            log.info("智能体启动: {}", event.getAgent().getName());
            return Mono.just(event);
        }

        // Agent 完成后
        if (event instanceof PostCallEvent) {
            log.info("智能体完成: {}", event.getAgent().getName());
            return Mono.just(event);
        }

        return Mono.just(event);
    }
}