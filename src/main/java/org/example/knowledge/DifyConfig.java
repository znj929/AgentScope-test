package org.example.knowledge;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.rag.RAGMode;
import io.agentscope.core.rag.integration.dify.DifyKnowledge;
import io.agentscope.core.rag.integration.dify.DifyRAGConfig;
import io.agentscope.core.rag.integration.dify.RetrievalMode;

public class DifyConfig {
    DifyKnowledge knowledge = DifyKnowledge.builder()
        .config(DifyRAGConfig.builder()
            .apiBaseUrl("http://your-dify-server/v1")
            .apiKey("dataset-xxx")
            .datasetId("your-dataset-id")
            .topK(3)                                              // 返回 Top 3 相关文档
            .scoreThreshold(0.5)                                  // 相似度阈值
            .retrievalMode(RetrievalMode.HYBRID_SEARCH)           // 混合检索
            .build())
        .build();

    ReActAgent agent = ReActAgent.builder()
        .knowledge(knowledge)
        .ragMode(RAGMode.GENERIC)     // 自动检索模式
        .build();
}
