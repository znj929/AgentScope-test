package org.example.tool;

import cn.hutool.json.JSONUtil;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.example.knowledge.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * AnalyticDB 知识库检索工具
 * 提供基于向量相似度的知识检索功能
 * 支持多种检索策略：向量检索、混合检索、多路召回融合、两阶段重排序
 */
@Slf4j
public class KnowledgeSearchTool {

    private final AnalyticDBVectorStore vectorStore;
    private final AliyunEmbeddingService embeddingService;
    private final AliyunRerankService rerankService;
    private final ProductTools productTools;
    private final int topK;
    private final double scoreThreshold;
    private final int candidateCount;
    private final boolean enableRerank;
    private final boolean enableMultiPathRecall;
    
    // 简单缓存：key=query+keywords+filters, value=搜索结果
    private final Map<String, CacheEntry> searchCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 3600000; // 1小时过期

    /**
     * 构造函数（兼容旧版本）
     */
    public KnowledgeSearchTool(AnalyticDBVectorStore vectorStore, 
                              AliyunEmbeddingService embeddingService,
                              AliyunRerankService rerankService,
                              ProductTools productTools,
                              int topK, 
                              double scoreThreshold,
                              int candidateCount,
                              boolean enableRerank) {
        this(vectorStore, embeddingService, rerankService, productTools, topK, scoreThreshold, 
             candidateCount, enableRerank, false);
    }
    
    /**
     * 构造函数（完整版）
     *
     * @param vectorStore           AnalyticDB 向量存储
     * @param embeddingService      Embedding 服务
     * @param rerankService         Rerank 服务（可选）
     * @param productTools          产品工具（用于智能解析过滤条件）
     * @param topK                  返回最相关的 K 个结果
     * @param scoreThreshold        相似度阈值（0-1）
     * @param candidateCount        候选集数量（用于两阶段检索）
     * @param enableRerank          是否启用 Rerank
     * @param enableMultiPathRecall 是否启用多路召回融合
     */
    public KnowledgeSearchTool(AnalyticDBVectorStore vectorStore, 
                              AliyunEmbeddingService embeddingService,
                              AliyunRerankService rerankService,
                              ProductTools productTools,
                              int topK, 
                              double scoreThreshold,
                              int candidateCount,
                              boolean enableRerank,
                              boolean enableMultiPathRecall) {
        this.vectorStore = vectorStore;
        this.embeddingService = embeddingService;
        this.rerankService = rerankService;
        this.productTools = productTools;
        this.topK = topK;
        this.scoreThreshold = scoreThreshold;
        this.candidateCount = candidateCount;
        this.enableRerank = enableRerank;
        this.enableMultiPathRecall = enableMultiPathRecall;
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
     * 支持三种检索策略：
     * 1. 多路召回融合（推荐）：向量+全文+结构化三路召回，RRF 融合
     * 2. 两阶段检索：召回 + Rerank 重排序
     * 3. 传统单阶段：动态权重混合检索
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
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 检查缓存
            String cacheKey = generateCacheKey(query, keywords, filters);
            String cachedResult = getFromCache(cacheKey);
            if (cachedResult != null) {
                log.info("缓存命中，直接返回结果");
                return cachedResult;
            }
            
            // 如果没有提供 filters 和 keywords，使用 LLM 智能解析
            Map<String, Object> filterMap = null;
            String searchKeywords = keywords;
            
            if ((filters == null || filters.isEmpty()) && (keywords == null || keywords.isEmpty())) {
                log.info("未提供过滤条件和关键词，使用 LLM 智能解析");
                ParseResult parseResult = parseQueryWithLLM(query);
                filterMap = parseResult.filters;
                searchKeywords = parseResult.keywords.isEmpty() ? null : String.join(",", parseResult.keywords);
                log.info("LLM 解析结果 - filters: {}, keywords: {}", filterMap, searchKeywords);
            } else {
                // 处理用户提供的过滤条件
                if (filters != null && !filters.isEmpty()) {
                    try {
                        filterMap = JSONUtil.toBean(filters, Map.class);
                        log.info("解析用户提供的过滤条件: {}", filterMap);
                    } catch (Exception e) {
                        log.error("解析过滤条件失败: {}", filters, e);
                        return "{\"error\": \"过滤条件格式错误，请使用有效的 JSON 格式\"}";
                    }
                }
            }
            
            // 使用阿里云 Embedding API 将文本转换为向量
            float[] queryEmbedding = embeddingService.embeddingText(query);
            
            // 执行检索（根据配置选择最优策略）
            List<AnalyticDBVectorStore.SearchResult> results;
            String strategyUsed;
            
            if (enableMultiPathRecall) {
                // 策略1：多路召回融合 + Rerank 重排序（推荐）
                log.info("启用多路召回融合策略");
                MultiPathRecallFusion fusion = new MultiPathRecallFusion(vectorStore);
                
                // 如果启用了 Rerank，传入 rerankService 进行第二阶段重排序
                if (enableRerank && rerankService != null) {
                    log.info("多路召回融合后启用 Rerank 重排序 - 候选集: {}, 最终返回: {}", topK * 2, topK);
                    results = fusion.fuse(
                        queryEmbedding, 
                        searchKeywords != null ? searchKeywords : query, 
                        topK, 
                        filterMap,
                        rerankService,
                        query  // 使用原始查询文本进行 Rerank
                    );
                } else {
                    // 不启用 Rerank，仅使用 RRF 融合
                    results = fusion.fuse(
                        queryEmbedding, 
                        searchKeywords != null ? searchKeywords : query, 
                        topK, 
                        filterMap
                    );
                }
                strategyUsed = "MULTI_PATH_FUSION" + (enableRerank ? "_WITH_RERANK" : "");
                
            } else if (enableRerank && rerankService != null) {
                // 策略2：两阶段检索：召回 + 重排序
                log.info("启用 Rerank 两阶段检索 - 候选集: {}, 最终返回: {}", candidateCount, topK);
                results = vectorStore.twoStageSearchWithRerank(
                    queryEmbedding, 
                    searchKeywords != null ? searchKeywords : query,
                    topK,
                    candidateCount,
                    filterMap,
                    rerankService,
                    query  // 使用原始查询文本进行 Rerank
                );
                strategyUsed = "TWO_STAGE_RERANK";
                
            } else {
                // 策略3：传统单阶段检索（动态权重）
                log.info("使用传统单阶段检索（动态权重）");
                WeightConfig weightConfig = QueryAnalyzer.analyzeQuery(query, keywords);
                log.info("动态权重配置: vectorWeight={}, textWeight={}, queryType={}", 
                        weightConfig.getVectorWeight(), weightConfig.getTextWeight(), weightConfig.getQueryType());
                
                results = vectorStore.hybridSearch(
                    queryEmbedding, 
                    searchKeywords != null ? searchKeywords : query,
                    topK, 
                    filterMap, 
                    true,  // 启用销售状态排序
                    weightConfig
                );
                strategyUsed = "SINGLE_STAGE_DYNAMIC_WEIGHT";
            }
            
            if (results == null || results.isEmpty()) {
                String message = "{\"results\": [], \"message\": \"未找到相关知识，请尝试调整搜索条件或提供更多产品细节\", \"strategy\": \"" + strategyUsed + "\"}";
                log.info("未找到匹配结果，使用策略: {}", strategyUsed);
                return message;
            }
            
            String formattedResults = formatSearchResults(results, strategyUsed);
            
            // 存入缓存
            putToCache(cacheKey, formattedResults);
            
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("检索完成 - 找到 {} 条结果，使用策略: {}，耗时: {}ms", 
                    results.size(), strategyUsed, elapsed);
            
            return formattedResults;
            
        } catch (Exception e) {
            log.error("混合检索失败: query={}, keywords={}", query, keywords, e);
            return "{\"error\": \"检索失败: " + escapeJson(e.getMessage()) + "\"}";
        }
    }

    /**
     * 格式化搜索结果为 JSON 字符串
     */
    private String formatSearchResults(List<AnalyticDBVectorStore.SearchResult> results) {
        return formatSearchResults(results, "UNKNOWN");
    }
    
    /**
     * 格式化搜索结果为 JSON 字符串（带策略信息）
     */
    private String formatSearchResults(List<AnalyticDBVectorStore.SearchResult> results, String strategy) {
        StringBuilder json = new StringBuilder("{\"results\": [");
        
        for (int i = 0; i < results.size(); i++) {
            AnalyticDBVectorStore.SearchResult result = results.get(i);
            
            if (i > 0) {
                json.append(",");
            }
            
            json.append("{")
                .append("\"part_num\":\"").append(escapeJson(result.getPartNum())).append("\",")
                .append("\"product_name\":\"").append(escapeJson(result.getProductName())).append("\",")
                .append("\"description\":\"").append(escapeJson(result.getDescription())).append("\",")
                .append("\"external_model\":\"").append(escapeJson(result.getExternalModel())).append("\",")
                .append("\"specification\":\"").append(escapeJson(result.getSpecification())).append("\",")
                .append("\"sales_status\":\"").append(escapeJson(result.getSalesStatus())).append("\",")
                .append("\"overseas_product_line\":\"").append(escapeJson(result.getOverseasProductLine())).append("\",")
                .append("\"content\":\"").append(escapeJson(result.getContent())).append("\",")
                .append("\"similarity_score\":").append(result.getSimilarityScore())
                .append("}");
        }
        
        json.append("], \"count\":").append(results.size())
            .append(", \"strategy\":\"").append(strategy).append("}");
        
        return json.toString();
    }

    /**
     * 生成缓存键
     */
    private String generateCacheKey(String query, String keywords, String filters) {
        return String.format("%s:%s:%s", 
            query != null ? query : "",
            keywords != null ? keywords : "",
            filters != null ? filters : ""
        );
    }
    
    /**
     * 从缓存获取结果
     */
    private String getFromCache(String key) {
        CacheEntry entry = searchCache.get(key);
        if (entry == null) {
            return null;
        }
        
        // 检查是否过期
        if (System.currentTimeMillis() - entry.timestamp > CACHE_TTL_MS) {
            searchCache.remove(key);
            log.debug("缓存过期: {}", key);
            return null;
        }
        
        log.debug("缓存命中: {}", key);
        return entry.result;
    }
    
    /**
     * 存入缓存
     */
    private void putToCache(String key, String result) {
        searchCache.put(key, new CacheEntry(result, System.currentTimeMillis()));
        log.debug("缓存写入: {}", key);
    }
    
    /**
     * 使用 LLM 智能解析用户查询
     */
    private ParseResult parseQueryWithLLM(String query) {
        ParseResult result = new ParseResult();
        
        try {
            // 调用 ProductTools 的 parseFilterConditions
            String parseResult = productTools.parseFilterConditions(query);
            
            // 解析 JSON 结果
            Map<String, Object> parsed = JSONUtil.toBean(parseResult, Map.class);
            
            // 提取 filters
            if (parsed.containsKey("filters")) {
                result.filters = (Map<String, Object>) parsed.get("filters");
            } else {
                result.filters = new HashMap<>();
            }
            
            // 提取 keywords
            if (parsed.containsKey("keywords")) {
                Object keywordsObj = parsed.get("keywords");
                if (keywordsObj instanceof List) {
                    result.keywords = (List<String>) keywordsObj;
                } else {
                    result.keywords = new ArrayList<>();
                }
            } else {
                result.keywords = new ArrayList<>();
            }
            
        } catch (Exception e) {
            log.error("LLM 解析查询失败，使用默认配置", e);
            result.filters = new HashMap<>();
            result.keywords = new ArrayList<>();
        }
        
        return result;
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
    
    /**
     * 缓存条目
     */
    private static class CacheEntry {
        private final String result;
        private final long timestamp;
        
        public CacheEntry(String result, long timestamp) {
            this.result = result;
            this.timestamp = timestamp;
        }
    }
    
    /**
     * 解析结果数据结构
     */
    private static class ParseResult {
        private Map<String, Object> filters;
        private List<String> keywords;
        
        public ParseResult() {
            this.filters = new HashMap<>();
            this.keywords = new ArrayList<>();
        }
    }
}
