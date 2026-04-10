package org.example.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.example.knowledge.AliyunEmbeddingService;
import org.example.knowledge.AnalyticDBVectorStore;

import java.util.List;

/**
 * AnalyticDB 知识库检索工具
 * 提供基于向量相似度的知识检索功能
 */
@Slf4j
public class KnowledgeSearchTool {

    private final AnalyticDBVectorStore vectorStore;
    private final AliyunEmbeddingService embeddingService;
    private final int topK;
    private final double scoreThreshold;

    /**
     * 构造函数
     *
     * @param vectorStore       AnalyticDB 向量存储
     * @param embeddingService  Embedding 服务
     * @param topK              返回最相关的 K 个结果
     * @param scoreThreshold    相似度阈值（0-1）
     */
    public KnowledgeSearchTool(AnalyticDBVectorStore vectorStore, 
                              AliyunEmbeddingService embeddingService,
                              int topK, 
                              double scoreThreshold) {
        this.vectorStore = vectorStore;
        this.embeddingService = embeddingService ;
        this.topK = topK;
        this.scoreThreshold = scoreThreshold;
    }

    /**
     * 从知识库中搜索相关信息
     *
     * @param query 用户查询问题
     * @return 相关文档内容，格式为 JSON 数组
     */
    @Tool(name = "knowledge_search", description = "从知识库中搜索与查询相关的文档信息，返回最相关的内容片段")
    public String searchKnowledge(
            @ToolParam(name = "query", description = "要搜索的问题或关键词") String query) {
        
        log.info("执行知识库搜索: query={}", query);
        
        try {
            // 使用阿里云 Embedding API 将文本转换为向量
            float[] queryEmbedding = embeddingService.embed(query);
            
            // 执行向量相似度搜索
            List<AnalyticDBVectorStore.SearchResult> results = 
                vectorStore.similaritySearch(queryEmbedding, topK, scoreThreshold);
            
            if (results == null || results.isEmpty()) {
                log.info("未找到相关知识");
                return "{\"results\": [], \"message\": \"未找到相关知识\"}";
            }
            
            // 格式化搜索结果
            String formattedResults = formatSearchResults(results);
            log.info("找到 {} 条相关知识", results.size());
            
            return formattedResults;
            
        } catch (Exception e) {
            log.error("知识库搜索失败", e);
            return "{\"error\": \"搜索失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 混合检索：结合向量和关键词搜索
     *
     * @param query   用户查询问题
     * @param keyword 额外关键词（可选）
     * @return 相关文档内容
     */
    @Tool(name = "knowledge_hybrid_search", description = "使用混合检索模式（向量+关键词）搜索知识库")
    public String hybridSearchKnowledge(
            @ToolParam(name = "query", description = "要搜索的问题") String query,
            @ToolParam(name = "keyword", description = "额外的关键词", required = false) String keyword) {
        
        log.info("执行混合检索: query={}, keyword={}", query, keyword);
        
        try {
            // 使用阿里云 Embedding API 将文本转换为向量
            float[] queryEmbedding = embeddingService.embed(query);
            
            // 执行混合检索
            String searchKeyword = keyword != null && !keyword.isEmpty() ? keyword : query;
            List<AnalyticDBVectorStore.SearchResult> results = 
                vectorStore.hybridSearch(queryEmbedding, searchKeyword, topK);
            
            if (results == null || results.isEmpty()) {
                return "{\"results\": [], \"message\": \"未找到相关知识\"}";
            }
            
            return formatSearchResults(results);
            
        } catch (Exception e) {
            log.error("混合检索失败", e);
            return "{\"error\": \"检索失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 格式化搜索结果为 JSON 字符串
     */
    private String formatSearchResults(List<AnalyticDBVectorStore.SearchResult> results) {
        StringBuilder json = new StringBuilder("{\"results\": [");
        
        for (int i = 0; i < results.size(); i++) {
            AnalyticDBVectorStore.SearchResult result = results.get(i);
            
            if (i > 0) {
                json.append(",");
            }
            
            json.append("{")
                .append("\"document_id\":\"").append(escapeJson(result.getDocumentId())).append("\",")
                .append("\"content\":\"").append(escapeJson(result.getContent())).append("\",")
                .append("\"similarity_score\":").append(result.getSimilarityScore());
            
            if (result.getMetadata() != null && !result.getMetadata().isEmpty()) {
                json.append(",\"metadata\":{");
                // 简化处理：实际应使用 JSON 库序列化
                json.append("}");
            }
            
            json.append("}");
        }
        
        json.append("], \"count\":").append(results.size()).append("}");
        return json.toString();
    }

    /**
     * 转义 JSON 特殊字符
     */
    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}
