package org.example.tool;

import cn.hutool.json.JSONUtil;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.example.knowledge.AliyunEmbeddingService;
import org.example.knowledge.AnalyticDBVectorStore;
import org.example.knowledge.QueryAnalyzer;
import org.example.knowledge.WeightConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            float[] queryEmbedding = embeddingService.embeddingText(query);
            
            // 执行向量相似度搜索
            List<AnalyticDBVectorStore.SearchResult> results = 
                vectorStore.similaritySearch(queryEmbedding, topK, scoreThreshold);
            
            if (results == null || results.isEmpty()) {
                log.info("未找到相关知识");
                return "{\"results\": [], \"message\": \"未找到相关知识，请尝试调整搜索条件或提供更多产品细节\"}";
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
     * 混合检索：结合向量和关键词搜索（支持多关键词）
     *
     * @param query     用户查询问题
     * @param keywords  额外关键词列表（可选，用逗号分隔多个关键词）
     * @return 相关文档内容
     */
    @Tool(name = "knowledge_hybrid_search", description = "使用混合检索模式（向量+关键词）搜索知识库，支持多关键词组合搜索")
    public String hybridSearchKnowledge(
            @ToolParam(name = "query", description = "要搜索的问题") String query,
            @ToolParam(name = "keywords", description = "额外的关键词列表，多个关键词用逗号分隔，如：'2MP,2.8mm,IP67'", required = false) String keywords) {
        
        return hybridSearchKnowledge(query, keywords, null);
    }
    
    /**
     * 混合检索：结合向量和关键词搜索（支持多关键词和过滤条件）
     *
     * @param query     用户查询问题
     * @param keywords  额外关键词列表（可选，用逗号分隔多个关键词）
     * @param filters   过滤条件 JSON 字符串（可选），例如：'{"overseas_product_line": "IPC"}'
     * @return 相关文档内容
     */
    @Tool(name = "knowledge_hybrid_search_with_filter", 
          description = "使用混合检索模式（向量+关键词）搜索知识库，支持多关键词组合搜索和过滤条件。" +
                       "当需要按产品线、类别等条件筛选时使用此工具。" +
                       "filters 参数格式为 JSON 字符串，例如：'{\"overseas_product_line\": \"IPC\"}'")
    public String hybridSearchKnowledge(
            @ToolParam(name = "query", description = "要搜索的问题") String query,
            @ToolParam(name = "keywords", description = "额外的关键词列表，多个关键词用逗号分隔，如：'2MP,2.8mm,IP67'", required = false) String keywords,
            @ToolParam(name = "filters", description = "过滤条件 JSON 字符串，如：'{\"overseas_product_line\": \"IPC\"}'", required = false) String filters) {
        
        log.info("执行混合检索: query={}, keywords={}, filters={}", query, keywords, filters);
        
        try {
            // 使用阿里云 Embedding API 将文本转换为向量
            float[] queryEmbedding = embeddingService.embeddingText(query);
            
            // 处理关键词：如果提供了多个关键词，则构建复合搜索词
            String searchKeyword = query; // 默认使用原始查询
            if (keywords != null && !keywords.isEmpty()) {
                // 将逗号分隔的关键词合并为搜索字符串
                String[] keywordArray = keywords.split(",");
                StringBuilder keywordBuilder = new StringBuilder(query);
                for (String keyword : keywordArray) {
                    String trimmedKeyword = keyword.trim();
                    if (!trimmedKeyword.isEmpty()) {
                        keywordBuilder.append(" ").append(trimmedKeyword);
                    }
                }
                searchKeyword = keywordBuilder.toString();
                log.info("构建复合搜索词: {}", searchKeyword);
            }
            
            // 处理过滤条件
            Map<String, Object> filterMap = null;
            if (filters != null && !filters.isEmpty()) {
                try {
                    // 解析 JSON 字符串为 Map
                    filterMap = JSONUtil.toBean(filters, Map.class);
                    log.info("解析过滤条件: {}", filterMap);
                } catch (Exception e) {
                    log.error("解析过滤条件失败: {}", filters, e);
                    // 如果解析失败，继续执行但不使用过滤条件
                }
            }
            
            // 使用动态权重分析器确定最优权重配置
            WeightConfig weightConfig = QueryAnalyzer.analyzeQuery(query, keywords);
            log.info("动态权重配置: vectorWeight={}, textWeight={}, queryType={}", 
                    weightConfig.getVectorWeight(), weightConfig.getTextWeight(), weightConfig.getQueryType());
            
            // 执行混合检索（使用动态权重）
            List<AnalyticDBVectorStore.SearchResult> results = 
                vectorStore.hybridSearch(queryEmbedding, searchKeyword, topK, filterMap, true, weightConfig);
            
            if (results == null || results.isEmpty()) {
                return "{\"results\": [], \"message\": \"未找到相关知识，请尝试调整搜索条件或提供更多产品细节\"}";
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
                .append("\"part_num\":\"").append(escapeJson(result.getPartNum())).append("\",")
                .append("\"product_name\":\"").append(escapeJson(result.getProductName())).append("\",")
                .append("\"sales_status\":\"").append(escapeJson(result.getSalesStatus())).append("\",")
                .append("\"content\":\"").append(escapeJson(result.getContent())).append("\",")
                .append("\"similarity_score\":").append(result.getSimilarityScore());
            

            
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
