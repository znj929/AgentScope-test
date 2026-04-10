package org.example.config;

import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.ToolkitConfig;
import io.agentscope.core.tool.file.ReadFileTool;
import io.agentscope.core.tool.file.WriteFileTool;
import org.example.knowledge.AliyunEmbeddingService;
import org.example.knowledge.AnalyticDBConfig;
import org.example.knowledge.AnalyticDBVectorStore;
import org.example.tool.KnowledgeSearchTool;
import org.example.tool.ProductTools;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public  class  ToolConfig  {
    
    @Autowired
    private AnalyticDBConfig analyticDBConfig;
    
    @Autowired
    private AnalyticDBVectorStore vectorStore;
    
    @Autowired
    private AliyunEmbeddingService embeddingService;
    
    @Bean
    public Toolkit merchantToolkit()  {
        Toolkit toolkit  =  new  Toolkit(ToolkitConfig.builder()
        .parallel(true)                              // 并行执行多个工具
        .allowToolDeletion(false)               // 禁止删除工具
        .executionConfig(ExecutionConfig.builder()
        .timeout(Duration.ofSeconds(30))
        .build())
        .build());

        // 创建工具组
        toolkit.createToolGroup("admin",  "系统管理操作工具",  true);
        toolkit.registration()
        .tool(new ReadFileTool())
        .tool(new WriteFileTool())
        .group("admin")
        .apply();

        toolkit.createToolGroup("product",  "商品相关工具",  true);
        toolkit.registration()
        .tool(new ProductTools())
        .group("product")
        .apply();

        // 创建知识库工具组
        toolkit.createToolGroup("knowledge", "知识库检索工具", true);
        toolkit.registration()
        .tool(new KnowledgeSearchTool(
            vectorStore, 
            embeddingService,
            analyticDBConfig.getTopK(), 
            analyticDBConfig.getScoreThreshold()
        ))
        .group("knowledge")
        .apply();

        return  toolkit;
    }
}
