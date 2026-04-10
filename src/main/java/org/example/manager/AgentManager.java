package org.example.manager;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.session.JsonSession;
import io.agentscope.core.session.Session;
import io.agentscope.core.tool.Toolkit;
import lombok.extern.slf4j.Slf4j;
import org.example.hook.LoggingHook;
import org.example.knowledge.AnalyticDBConfig;
import org.example.knowledge.AnalyticDBVectorStore;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class AgentManager {

    private final OpenAIChatModel openAIChatModel;
    private final Toolkit merchantToolkit;
    private final AnalyticDBConfig analyticDBConfig;
    private final AnalyticDBVectorStore vectorStore;
    
    // Agent 缓存：Key = userId:sessionId:datasetId
    private final ConcurrentHashMap<String, ReActAgent> agentCache = new ConcurrentHashMap<>();
    
    // 会话存储
    private final Session session;

    public AgentManager(OpenAIChatModel openAIChatModel, Toolkit merchantToolkit, 
                       AnalyticDBConfig analyticDBConfig, AnalyticDBVectorStore vectorStore) {
        this.openAIChatModel = openAIChatModel;
        this.merchantToolkit = merchantToolkit;
        this.analyticDBConfig = analyticDBConfig;
        this.vectorStore = vectorStore;
        this.session = new JsonSession(Path.of(System.getProperty("user.home") + "/.agent-merchant/sessions"));
    }

    public ReActAgent getOrCreateAgent(String userId, String sessionId, String datasetId) {
        String cacheKey = String.format("%s:%s:%s", userId, sessionId, datasetId);
        
        return agentCache.computeIfAbsent(cacheKey, key -> {
            ReActAgent agent = createAgent(datasetId);
            // 尝试加载已有会话
            agent.loadIfExists(session, sessionId);
            return agent;
        });
    }

    private ReActAgent createAgent(String datasetId) {
        ReActAgent.Builder builder = ReActAgent.builder()
            .name("MerchantAssistant")
            .sysPrompt("你是一位店铺助手，回复关于商品、店铺联系方式或简介等信息。" +
                      "\n\n你可以使用以下工具：" +
                      "\n1. knowledge_search - 从知识库中搜索相关信息" +
                      "\n2. product_recommendation - 推荐商品" +
                      "\n3. read_file/write_file - 文件操作")
            .model(openAIChatModel)
            .toolkit(merchantToolkit)
            .memory(new InMemoryMemory())
            .hook(new LoggingHook())
            .maxIters(10);

        // AnalyticDB 知识检索功能已通过 KnowledgeSearchTool 集成到工具集中
        // Agent 可以自动调用 knowledge_search 工具进行知识库检索
        // datasetId 参数可用于区分不同的数据集（如果需要）

        return builder.build();
    }

    public void saveSession(String sessionId, ReActAgent agent) {
        agent.saveTo(session, sessionId);
    }
}
