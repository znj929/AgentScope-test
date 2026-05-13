package org.example.knowledge;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 多路召回融合器
 * 结合向量检索、全文检索和结构化检索的结果
 */
@Slf4j
public class MultiPathRecallFusion {
    
    private final AnalyticDBVectorStore vectorStore;
    private final double vectorWeight;
    private final double textWeight;
    private final double structuredWeight;
    private final int rrfK; // RRF 常数，通常设为 60
    
    public MultiPathRecallFusion(AnalyticDBVectorStore vectorStore) {
        this(vectorStore, 1.0, 1.0, 1.0, 60);
    }
    
    public MultiPathRecallFusion(AnalyticDBVectorStore vectorStore, 
                                 double vectorWeight, 
                                 double textWeight, 
                                 double structuredWeight,
                                 int rrfK) {
        this.vectorStore = vectorStore;
        this.vectorWeight = vectorWeight;
        this.textWeight = textWeight;
        this.structuredWeight = structuredWeight;
        this.rrfK = rrfK;
    }
    
    /**
     * 执行多路召回融合
     *
     * @param queryEmbedding 查询向量
     * @param keyword        关键词
     * @param topK           最终返回数量
     * @param filters        过滤条件
     * @return 融合后的搜索结果
     */
    public List<AnalyticDBVectorStore.SearchResult> fuse(
            float[] queryEmbedding, 
            String keyword, 
            int topK,
            Map<String, Object> filters) {
        
        // 默认不启用 Rerank（保持向后兼容）
        return fuse(queryEmbedding, keyword, topK, filters, null, null);
    }
    
    /**
     * 执行多路召回融合（支持 Rerank 重排序）
     *
     * @param queryEmbedding 查询向量
     * @param keyword        关键词
     * @param topK           最终返回数量
     * @param filters        过滤条件
     * @param rerankService  Rerank 服务（可选）
     * @param queryText      原始查询文本（用于 Rerank）
     * @return 融合后的搜索结果
     */
    public List<AnalyticDBVectorStore.SearchResult> fuse(
            float[] queryEmbedding, 
            String keyword, 
            int topK,
            Map<String, Object> filters,
            AliyunRerankService rerankService,
            String queryText) {
        
        log.info("========== 开始多路召回融合 ==========");
        log.info("查询关键词: {}", keyword);
        log.info("返回数量: {}", topK);
        log.info("过滤条件: {}", filters);
        
        long startTime = System.currentTimeMillis();
        
        // 第一路：向量语义检索（召回更多候选）
        List<AnalyticDBVectorStore.SearchResult> vectorResults = 
            executeVectorSearch(queryEmbedding, topK * 2, filters);
        log.info("【第一路】向量检索完成 - 召回 {} 条结果", vectorResults.size());
        
        // 第二路：全文关键词检索
        List<AnalyticDBVectorStore.SearchResult> textResults = 
            executeTextSearch(keyword, topK * 2, filters);
        log.info("【第二路】全文检索完成 - 召回 {} 条结果", textResults.size());
        
        // 第三路：结构化字段检索（如果包含产品编号或型号）
        List<AnalyticDBVectorStore.SearchResult> structuredResults = 
            executeStructuredSearch(keyword, topK, filters);
        log.info("【第三路】结构化检索完成 - 召回 {} 条结果", structuredResults.size());
        
        // 检查是否所有路都为空
        if (vectorResults.isEmpty() && textResults.isEmpty() && structuredResults.isEmpty()) {
            log.warn("⚠️ 三路检索结果均为空，返回空列表");
            return Collections.emptyList();
        }
        
        // 使用 RRF 算法融合三路结果
        List<AnalyticDBVectorStore.SearchResult> fusedResults = 
            reciprocalRankFusion(vectorResults, textResults, structuredResults, topK);
        
        log.info("RRF 融合完成 - 得到 {} 条结果", fusedResults.size());
        
        // 如果启用了 Rerank 服务，进行第二阶段重排序
        if (rerankService != null && !fusedResults.isEmpty()) {
            log.info("启用 Rerank 重排序 - 候选集: {}, 最终返回: {}", fusedResults.size(), topK);
            fusedResults = applyRerank(fusedResults, rerankService, queryText, topK);
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        log.info("========== 多路召回融合完成 ==========");
        log.info("最终返回 {} 条结果，耗时: {}ms", fusedResults.size(), elapsed);
        
        return fusedResults;
    }
    
    /**
     * 第一路：向量语义检索
     */
    private List<AnalyticDBVectorStore.SearchResult> executeVectorSearch(
            float[] queryEmbedding, int topK, Map<String, Object> filters) {
        try {
            // 使用纯向量相似度搜索，不使用混合检索
            return vectorStore.similaritySearch(queryEmbedding, topK);
        } catch (Exception e) {
            log.error("向量检索失败", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 第二路：全文关键词检索
     */
    private List<AnalyticDBVectorStore.SearchResult> executeTextSearch(
            String keyword, int topK, Map<String, Object> filters) {
        try {
            if (keyword == null || keyword.trim().isEmpty()) {
                log.warn("全文检索关键词为空，跳过此路检索");
                return Collections.emptyList();
            }
            
            log.info("执行全文检索 - keyword: {}, topK: {}", keyword, topK);
            
            // 使用文本为主的权重配置
            WeightConfig textConfig = new WeightConfig(0.4, 0.6, "TEXT_FOCUSED");
            
            // 创建一个零向量用于占位（实际不使用向量相似度）
            float[] dummyVector = new float[1536]; // 假设向量维度为 1536
            Arrays.fill(dummyVector, 0.0f);
            
            List<AnalyticDBVectorStore.SearchResult> results = vectorStore.hybridSearch(
                dummyVector,
                keyword,
                topK,
                filters,
                true,
                textConfig
            );
            
            log.info("全文检索完成 - 召回 {} 条结果", results.size());
            return results;
        } catch (Exception e) {
            log.error("全文检索失败 - keyword: {}", keyword, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 第三路：结构化字段检索
     * 针对产品编号、型号等结构化数据进行精确或模糊匹配
     */
    private List<AnalyticDBVectorStore.SearchResult> executeStructuredSearch(
            String keyword, int topK, Map<String, Object> filters) {
        
        List<AnalyticDBVectorStore.SearchResult> results = new ArrayList<>();
        
        try {
            // 检测是否包含产品编号（如 1.2.3.4 格式）
            if (containsProductCode(keyword)) {
                List<AnalyticDBVectorStore.SearchResult> codeResults = 
                    searchByProductCode(keyword, topK, filters);
                results.addAll(codeResults);
                log.info("产品编号检索召回 {} 条结果", codeResults.size());
            }
            
            // 检测是否包含产品型号（如 IPC-HFWxxx 格式）
            if (containsProductModel(keyword)) {
                List<AnalyticDBVectorStore.SearchResult> modelResults = 
                    searchByProductModel(keyword, topK, filters);
                results.addAll(modelResults);
                log.info("产品型号检索召回 {} 条结果", modelResults.size());
            }
            
            // 去重（同一个文档可能同时匹配编号和型号）
            return results.stream()
                .collect(Collectors.toMap(
                    AnalyticDBVectorStore.SearchResult::getId,
                    r -> r,
                    (existing, replacement) -> existing
                ))
                .values()
                .stream()
                .limit(topK)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("结构化检索失败", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 按产品编号搜索
     */
    private List<AnalyticDBVectorStore.SearchResult> searchByProductCode(
            String keyword, int topK, Map<String, Object> filters) {
        
        // 提取产品编号
        String productCode = extractProductCode(keyword);
        if (productCode == null) {
            return Collections.emptyList();
        }
        
        StringBuilder sql = new StringBuilder(String.format(
            "SELECT id, part_num, product_name, description, external_model, " +
            "specification, overseas_product_line, content, " +
            "1.0 AS similarity_score " +  // 精确匹配给满分
            "FROM %s " +
            "WHERE part_num LIKE ? ",
            vectorStore.getAnalyticDBConfig().getTableName()
        ));
        
        List<Object> params = new ArrayList<>();
        params.add("%" + productCode + "%");
        
        // 添加过滤条件
        if (filters != null && !filters.isEmpty()) {
            for (Map.Entry<String, Object> entry : filters.entrySet()) {
                sql.append(" AND ").append(entry.getKey()).append(" = ?");
                params.add(entry.getValue());
            }
        }
        
        sql.append(" LIMIT ?");
        params.add(topK);
        
        return vectorStore.getJdbcTemplate().query(
            sql.toString(),
            (rs, rowNum) -> {
                AnalyticDBVectorStore.SearchResult result = new AnalyticDBVectorStore.SearchResult();
                result.setId(rs.getString("id"));
                result.setPartNum(rs.getString("part_num"));
                result.setProductName(rs.getString("product_name"));
                result.setDescription(rs.getString("description"));
                result.setExternalModel(rs.getString("external_model"));
                result.setSpecification(rs.getString("specification"));
                result.setOverseasProductLine(rs.getString("overseas_product_line"));
                result.setContent(rs.getString("content"));
                result.setSimilarityScore(rs.getDouble("similarity_score"));
                return result;
            },
            params.toArray()
        );
    }
    
    /**
     * 按产品型号搜索
     */
    private List<AnalyticDBVectorStore.SearchResult> searchByProductModel(
            String keyword, int topK, Map<String, Object> filters) {
        
        String productModel = extractProductModel(keyword);
        if (productModel == null) {
            return Collections.emptyList();
        }
        
        StringBuilder sql = new StringBuilder(String.format(
            "SELECT id, part_num, product_name, description, external_model, " +
            "specification, overseas_product_line, content, " +
            "1.0 AS similarity_score " +
            "FROM %s " +
            "WHERE external_model LIKE ? OR product_name LIKE ? ",
            vectorStore.getAnalyticDBConfig().getTableName()
        ));
        
        List<Object> params = new ArrayList<>();
        params.add("%" + productModel + "%");
        params.add("%" + productModel + "%");
        
        // 添加过滤条件
        if (filters != null && !filters.isEmpty()) {
            for (Map.Entry<String, Object> entry : filters.entrySet()) {
                sql.append(" AND ").append(entry.getKey()).append(" = ?");
                params.add(entry.getValue());
            }
        }
        
        sql.append(" LIMIT ?");
        params.add(topK);
        
        return vectorStore.getJdbcTemplate().query(
            sql.toString(),
            (rs, rowNum) -> {
                AnalyticDBVectorStore.SearchResult result = new AnalyticDBVectorStore.SearchResult();
                result.setId(rs.getString("id"));
                result.setPartNum(rs.getString("part_num"));
                result.setProductName(rs.getString("product_name"));
                result.setDescription(rs.getString("description"));
                result.setExternalModel(rs.getString("external_model"));
                result.setSpecification(rs.getString("specification"));
                result.setOverseasProductLine(rs.getString("overseas_product_line"));
                result.setContent(rs.getString("content"));
                result.setSimilarityScore(rs.getDouble("similarity_score"));
                return result;
            },
            params.toArray()
        );
    }
    
    /**
     * RRF (Reciprocal Rank Fusion) 融合算法
     * 
     * 公式：RRF(d) = Σ (1 / (k + rank(d)))
     * 其中 k 是常数（通常为 60），rank(d) 是文档在某路结果中的排名
     */
    private List<AnalyticDBVectorStore.SearchResult> reciprocalRankFusion(
            List<AnalyticDBVectorStore.SearchResult> vectorResults,
            List<AnalyticDBVectorStore.SearchResult> textResults,
            List<AnalyticDBVectorStore.SearchResult> structuredResults,
            int topK) {
        
        // 存储每个文档的 RRF 分数
        Map<String, RRFScore> rrfScores = new HashMap<>();
        
        // 合并所有结果到一个 Map（以 ID 为键）
        Map<String, AnalyticDBVectorStore.SearchResult> allResults = new LinkedHashMap<>();
        
        // 处理第一路：向量检索结果
        processResults(vectorResults, rrfScores, allResults, vectorWeight, "VECTOR");
        
        // 处理第二路：全文检索结果
        processResults(textResults, rrfScores, allResults, textWeight, "TEXT");
        
        // 处理第三路：结构化检索结果
        processResults(structuredResults, rrfScores, allResults, structuredWeight, "STRUCTURED");
        
        // 按 RRF 分数排序（降序）
        List<Map.Entry<String, RRFScore>> sortedEntries = rrfScores.entrySet().stream()
            .sorted((e1, e2) -> Double.compare(e2.getValue().finalScore, e1.getValue().finalScore))
            .collect(Collectors.toList());
        
        // 构建最终结果列表
        List<AnalyticDBVectorStore.SearchResult> finalResults = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, sortedEntries.size()); i++) {
            Map.Entry<String, RRFScore> entry = sortedEntries.get(i);
            String docId = entry.getKey();
            RRFScore score = entry.getValue();
            
            AnalyticDBVectorStore.SearchResult result = allResults.get(docId);
            if (result != null) {
                // 设置融合后的分数
                result.setSimilarityScore(score.finalScore);
                
                // 在日志中显示各路贡献
                log.debug("文档 {} - RRF分数: {:.4f} (向量: {:.4f}, 文本: {:.4f}, 结构化: {:.4f})", 
                    docId, score.finalScore, score.vectorContribution, 
                    score.textContribution, score.structuredContribution);
                
                finalResults.add(result);
            }
        }
        
        return finalResults;
    }
    
    /**
     * 处理单路检索结果，计算 RRF 分数
     */
    private void processResults(
            List<AnalyticDBVectorStore.SearchResult> results,
            Map<String, RRFScore> rrfScores,
            Map<String, AnalyticDBVectorStore.SearchResult> allResults,
            double pathWeight,
            String pathName) {
        
        for (int i = 0; i < results.size(); i++) {
            AnalyticDBVectorStore.SearchResult result = results.get(i);
            String docId = result.getId();
            
            // 计算该路的 RRF 分数：1 / (k + rank)
            double rrfScore = 1.0 / (rrfK + i + 1);
            double weightedScore = rrfScore * pathWeight;
            
            // 累加到总分
            RRFScore existing = rrfScores.getOrDefault(docId, new RRFScore());
            
            switch (pathName) {
                case "VECTOR":
                    existing.vectorContribution += weightedScore;
                    break;
                case "TEXT":
                    existing.textContribution += weightedScore;
                    break;
                case "STRUCTURED":
                    existing.structuredContribution += weightedScore;
                    break;
            }
            
            existing.finalScore = existing.vectorContribution + 
                                 existing.textContribution + 
                                 existing.structuredContribution;
            
            rrfScores.put(docId, existing);
            allResults.putIfAbsent(docId, result);
        }
    }
    
    /**
     * 检测是否包含产品编号
     */
    private boolean containsProductCode(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        // 匹配类似 "1.2.3.4" 或 "1.2.3" 的格式
        return text.matches(".*\\d+\\.\\d+\\.\\d+(?:\\.\\d+)?.*");
    }
    
    /**
     * 提取产品编号
     */
    private String extractProductCode(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\d+\\.\\d+\\.\\d+(?:\\.\\d+)?");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }
    
    /**
     * 检测是否包含产品型号
     */
    private boolean containsProductModel(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        // 匹配类似 "IPC-HFW1230"、"NVR4108" 等型号
        return text.matches(".*[A-Z]{2,}-?[A-Z0-9]+.*");
    }
    
    /**
     * 提取产品型号
     */
    private String extractProductModel(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("[A-Z]{2,}-?[A-Z0-9]{3,}");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }
    
    /**
     * 应用 Rerank 重排序
     *
     * @param candidates    候选结果列表
     * @param rerankService Rerank 服务
     * @param queryText     原始查询文本
     * @param topK          最终返回数量
     * @return 重排序后的结果
     */
    private List<AnalyticDBVectorStore.SearchResult> applyRerank(
            List<AnalyticDBVectorStore.SearchResult> candidates,
            AliyunRerankService rerankService,
            String queryText,
            int topK) {
        
        try {
            // 提取候选集的文档内容
            List<String> documents = candidates.stream()
                .map(AnalyticDBVectorStore.SearchResult::getContent)
                .collect(Collectors.toList());
            
            // 调用 Rerank 服务
            List<AliyunRerankService.RerankResult> rerankedResults = 
                rerankService.rerank(queryText, documents, topK);
            
            if (rerankedResults == null || rerankedResults.isEmpty()) {
                log.warn("Rerank 结果为空，返回原始融合结果的前 {} 个", topK);
                return candidates.subList(0, Math.min(topK, candidates.size()));
            }
            
            // 根据 Rerank 结果的索引，从原始候选集中获取对应的 SearchResult
            List<AnalyticDBVectorStore.SearchResult> finalResults = new ArrayList<>();
            for (AliyunRerankService.RerankResult rerankResult : rerankedResults) {
                int index = rerankResult.getIndex();
                if (index >= 0 && index < candidates.size()) {
                    AnalyticDBVectorStore.SearchResult originalResult = candidates.get(index);
                    // 更新相似度分数为 Rerank 分数
                    originalResult.setSimilarityScore(rerankResult.getRelevanceScore());
                    finalResults.add(originalResult);
                }
            }
            
            log.info("Rerank 重排序完成 - 最终返回 {} 个结果", finalResults.size());
            return finalResults;
            
        } catch (Exception e) {
            log.error("Rerank 阶段失败，降级返回原始融合结果的前 {} 个结果", topK, e);
            return candidates.subList(0, Math.min(topK, candidates.size()));
        }
    }
    
    /**
     * RRF 分数数据结构
     */
    @Data
    @AllArgsConstructor
    private static class RRFScore {
        private double vectorContribution = 0.0;
        private double textContribution = 0.0;
        private double structuredContribution = 0.0;
        private double finalScore = 0.0;
        
        public RRFScore() {
        }
    }
}
