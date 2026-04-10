package org.example.config;

import io.agentscope.core.model.OpenAIChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 聊天模型配置
 * 使用阿里百炼服务（兼容 OpenAI API 格式）
 */
@Slf4j
@Configuration
public class ChatModelConfig{

     @Value("${agentscope.openai.api-key}")
     private String apiKey;

     @Value("${agentscope.openai.base-url}")
     private String baseUrl;

     @Value("${agentscope.openai.model-name}")
     private String modelName;

    @Bean
    public OpenAIChatModel openAIChatModel(){
        log.info("初始化阿里百炼聊天模型");
        log.info("API地址: {}", baseUrl);
        log.info("模型名称: {}", modelName);
        
        return OpenAIChatModel.builder()
        .apiKey(apiKey)
        .baseUrl(baseUrl)
        .modelName(modelName)
        .stream(true) // 启用流式输出
        .build();
    }
}
