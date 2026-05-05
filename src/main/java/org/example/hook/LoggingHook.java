package org.example.hook;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostCallEvent;
import io.agentscope.core.hook.PreCallEvent;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * AgentScope Hook 日志记录器
 * 
 * AgentScope 1.0+ 支持的事件：
 * - PreCallEvent / PostCallEvent: Agent 调用前后
 * - PreReasoningEvent / PostReasoningEvent: LLM 推理前后
 * - PreActingEvent / PostActingEvent: 工具执行前后
 * - ReasoningChunkEvent / ActingChunkEvent: 流式输出
 * - ErrorEvent: 错误事件
 * 
 * 注意：PreActingEvent 和 PostActingEvent 在 1.0.9 版本中可能存在 API 差异
 * 当前只使用稳定的 PreCallEvent 和 PostCallEvent
 */
@Slf4j
public class LoggingHook implements Hook {

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        // Agent 启动前
        if (event instanceof PreCallEvent) {
            log.info("🤖 智能体启动: {}", event.getAgent().getName());
            return Mono.just(event);
        }

        // Agent 完成后
        if (event instanceof PostCallEvent) {
            log.info("✅ 智能体完成: {}", event.getAgent().getName());
            return Mono.just(event);
        }

        return Mono.just(event);
    }
}
