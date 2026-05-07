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

        // 使用 Flux.create 实现真正的流式输出
        return Flux.create(sink -> {
            try {
                // 同步调用 Agent
                var response = agent.call(userMsg).block();
                
                if (response == null || response.getTextContent() == null) {
                    sink.complete();
                    return;
                }
                
                String text = response.getTextContent();
                
                // 逐字符流式输出（更细粒度的流式）
                for (int i = 0; i < text.length(); i++) {
                    if (sink.isCancelled()) {
                        break;
                    }
                    // 每次发送一个字符，实现真正的流式效果
                    sink.next(String.valueOf(text.charAt(i)));
                    
                    // 添加小延迟，让流式效果更明显（可选）
                    try {
                        Thread.sleep(10); // 10ms 延迟
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                
                sink.complete();
                
                // 保存会话 - 确保在每次对话后都保存上下文
                agentManager.saveSession(sessionId, agent);
                log.info("会话已保存: sessionId={}, userId={}", sessionId, userId);
                
            } catch (Exception e) {
                log.error("聊天处理失败: userId={}, sessionId={}", userId, sessionId, e);
                sink.error(e);
            }
        });
    }
}
