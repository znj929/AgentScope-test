package org.example.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.service.IChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * 聊天控制器
 * 提供流式聊天接口
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final IChatService chatService;

    @Autowired
    public ChatController(IChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 流式聊天接口
     * 
     * @param userId    用户ID
     * @param sessionId 会话ID
     * @param datasetId 数据集ID（可选）
     * @param content   用户消息内容
     * @return 流式响应
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_PLAIN_VALUE)
    public Flux<String> chatStream(
            @RequestParam String userId,
            @RequestParam String sessionId,
            @RequestParam(required = false, defaultValue = "default") String datasetId,
            @RequestBody String content) {
        
        log.info("收到流式聊天请求: userId={}, sessionId={}, datasetId={}", userId, sessionId, datasetId);
        
        return chatService.chatStream(userId, sessionId, datasetId, content);
    }

    /**
     * 简化的聊天接口（使用默认参数）
     * 
     * @param content 用户消息内容
     * @return 流式响应
     */
    @PostMapping(value = "/simple", produces = MediaType.TEXT_PLAIN_VALUE)
    public Flux<String> simpleChat(
            @RequestParam(required = false, defaultValue = "user_001", name = "userId") String userId,
            @RequestParam(required = false, name = "sessionId") String sessionId,
            @RequestParam(required = false, defaultValue = "default", name = "datasetId") String datasetId,
            @RequestBody String content) {
        
        // 如果没有提供sessionId，则生成一个新的
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = "session_" + System.currentTimeMillis();
            log.info("生成新会话ID: sessionId={}", sessionId);
        } else {
            log.info("使用现有会话ID: sessionId={}", sessionId);
        }
        
        return chatService.chatStream(userId, sessionId, datasetId, content);
    }
}
