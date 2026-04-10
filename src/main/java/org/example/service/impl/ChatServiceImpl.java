package org.example.service.impl;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import lombok.extern.slf4j.Slf4j;
import org.example.manager.AgentManager;
import org.example.service.IChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天服务实现类
 * 提供流式聊天功能
 */
@Slf4j
@Service
public class ChatServiceImpl implements IChatService {

    private final AgentManager agentManager;

    @Autowired
    public ChatServiceImpl(AgentManager agentManager) {
        this.agentManager = agentManager;
    }

    @Override
    public Flux<String> chatStream(String userId, String sessionId, String datasetId, String content) {
        log.info("收到聊天请求: userId={}, sessionId={}, datasetId={}", userId, sessionId, datasetId);
        
        // 获取或创建 Agent
        ReActAgent agent = agentManager.getOrCreateAgent(userId, sessionId, datasetId);

        // 构建用户消息
        Msg userMsg = Msg.builder()
            .role(MsgRole.USER)
            .textContent(content)
            .build();

        // 使用 Mono.fromCallable 异步调用 Agent，避免阻塞
        return Mono.fromCallable(() -> {
                // 同步调用 Agent
                return agent.call(userMsg).block();
            })
            .subscribeOn(Schedulers.boundedElastic()) // 在弹性线程池中执行，避免阻塞主线程
            .flatMapMany(response -> {
                if (response == null || response.getTextContent() == null) {
                    return Flux.empty();
                }
                
                // 将响应内容分块发送（模拟流式效果）
                String text = response.getTextContent();
                int chunkSize = 10;
                List<String> chunks = new ArrayList<>();
                for (int i = 0; i < text.length(); i += chunkSize) {
                    int end = Math.min(i + chunkSize, text.length());
                    chunks.add(text.substring(i, end));
                }
                return Flux.fromIterable(chunks);
            })
            .doOnComplete(() -> {
                // 保存会话
                agentManager.saveSession(sessionId, agent);
                log.info("会话已保存: sessionId={}", sessionId);
            })
            .doOnError(error -> {
                log.error("聊天处理失败: userId={}, sessionId={}", userId, sessionId, error);
            });
    }
}
