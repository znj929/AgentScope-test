package org.example.config;

import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.ToolkitConfig;
import io.agentscope.core.tool.file.ReadFileTool;
import io.agentscope.core.tool.file.WriteFileTool;
import lombok.extern.slf4j.Slf4j;
import org.example.knowledge.AliyunEmbeddingService;
import org.example.knowledge.AliyunRerankService;
import org.example.knowledge.AnalyticDBConfig;
import org.example.knowledge.AnalyticDBVectorStore;
import org.example.tool.KnowledgeSearchTool;
import org.example.tool.ProductTools;
import org.example.tool.ProductOutputFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
public  class  ToolConfig  {
    
    @Autowired
    private AnalyticDBConfig analyticDBConfig;
    
    @Autowired
    private AnalyticDBVectorStore vectorStore;
    
    @Autowired
    private AliyunEmbeddingService embeddingService;
    
    @Autowired(required = false)
    private AliyunRerankService rerankService;
    
    @Autowired
    private ProductTools productTools;
    
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

        /*toolkit.createToolGroup("product",  "商品相关工具",  true);
        toolkit.registration()
        .tool(new ProductTools())
        .group("product")
        .apply();*/
        
        // 创建产品过滤条件解析工具组
        toolkit.createToolGroup("product", "产品相关工具", true);
        toolkit.registration()
        .tool(productTools)
        .group("product")
        .apply();
        
        // 创建产品输出格式化工具组（单独配置）
        toolkit.createToolGroup("formatter", "产品输出格式化工具", true);
        toolkit.registration()
        .tool(new ProductOutputFormatter())
        .group("formatter")
        .apply();

        // 创建知识库工具组
        toolkit.createToolGroup("knowledge", "知识库检索工具", true);
        
        // 获取 Rerank 配置
        boolean enableRerank = analyticDBConfig.getEnableRerank() != null ? 
            analyticDBConfig.getEnableRerank() : false;
        int candidateCount = analyticDBConfig.getRerankCandidateCount() != null ? 
            analyticDBConfig.getRerankCandidateCount() : 20;
        boolean enableMultiPathRecall = analyticDBConfig.getEnableMultiPathRecall() != null ?
            analyticDBConfig.getEnableMultiPathRecall() : false;
        
        log.info("初始化知识库检索工具 - TopK: {}, ScoreThreshold: {}, EnableRerank: {}, CandidateCount: {}, EnableMultiPathRecall: {}",
            analyticDBConfig.getTopK(), analyticDBConfig.getScoreThreshold(), enableRerank, candidateCount, enableMultiPathRecall);
        
        toolkit.registration()
        .tool(new KnowledgeSearchTool(
            vectorStore, 
            embeddingService,
            rerankService,  // Rerank 服务（可选）
            productTools,   // 产品工具（用于智能解析过滤条件）
            analyticDBConfig.getTopK(), 
            analyticDBConfig.getScoreThreshold(),
            candidateCount,
            enableRerank,
            enableMultiPathRecall  // 是否启用多路召回融合
        ))
        .group("knowledge")
        .apply();

        return  toolkit;
    }
}
