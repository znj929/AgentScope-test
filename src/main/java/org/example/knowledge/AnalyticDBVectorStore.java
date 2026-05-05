package org.example.knowledge;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.annotation.PostConstruct;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/**
 * AnalyticDB PostgreSQL 向量库操作工具类
 * 提供向量的增删改查功能
 */
@Slf4j
public class AnalyticDBVectorStore {

    private final AnalyticDBConfig analyticDBConfig;
    private JdbcTemplate jdbcTemplate;

    /**
     * 构造函数
     *
     * @param config AnalyticDB 配置
     */
    public AnalyticDBVectorStore(AnalyticDBConfig config) {
        this.analyticDBConfig = config;
    }

    /**
     * 初始化数据库连接
     */
    @PostConstruct
    public void init() {
        log.info("初始化 AnalyticDB PostgreSQL 向量存储");
        
        // 创建数据源
        org.postgresql.ds.PGSimpleDataSource dataSource = new org.postgresql.ds.PGSimpleDataSource();
        dataSource.setUrl(analyticDBConfig.getJdbcUrl());
        dataSource.setUser(analyticDBConfig.getUsername());
        dataSource.setPassword(analyticDBConfig.getPassword());
        
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        
        // 验证连接
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            log.info("AnalyticDB PostgreSQL 连接成功: {}", analyticDBConfig.getJdbcUrl());
        } catch (Exception e) {
            log.error("AnalyticDB PostgreSQL 连接失败", e);
            throw new RuntimeException("无法连接到 AnalyticDB PostgreSQL", e);
        }
    }

    /**
     * 插入向量记录
     *
     * @param documentId 文档ID
     * @param content    文档内容
     * @param embedding  向量数组
     * @param metadata   元数据
     */
    public void insertVector(String documentId, String content, float[] embedding, Map<String, Object> metadata) {
        String sql = String.format(
            "INSERT INTO %s (document_id, content, vector, metadata, created_at, updated_at) VALUES (?, ?, ?::vector, ?::jsonb, ?, ?)",
            analyticDBConfig.getTableName()
        );

        try {
            String vectorStr = arrayToVectorString(embedding);
            String metadataJson = metadata != null ? mapToJsonString(metadata) : "{}";
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());

            jdbcTemplate.update(sql, documentId, content, vectorStr, metadataJson, now, now);
            log.debug("向量插入成功: documentId={}", documentId);
        } catch (Exception e) {
            log.error("向量插入失败: documentId={}", documentId, e);
            throw new RuntimeException("向量插入失败", e);
        }
    }
    
    /**
     * 插入向量记录（包含完整字段）
     *
     * @param documentId 文档ID
     * @param content    文档内容
     * @param overseasProductLine 海外产品线
     * @param partNum    产品编号
     * @param productName 产品名称
     * @param description 产品描述
     * @param externalModel 外部型号
     * @param specification 规格参数
     * @param embedding  向量数组
     * @param metadata   元数据
     */
    public void insertVectorWithFields(String documentId, String content, String overseasProductLine, 
                                      String partNum, String productName, String description,
                                      String externalModel, String specification,
                                      float[] embedding, Map<String, Object> metadata) {
        String sql = String.format(
            "INSERT INTO %s (document_id, content, overseas_product_line, part_num, product_name, description, external_model, specification, vector, metadata, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::vector, ?::jsonb, ?, ?)",
            analyticDBConfig.getTableName()
        );

        try {
            String vectorStr = arrayToVectorString(embedding);
            String metadataJson = metadata != null ? mapToJsonString(metadata) : "{}";
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());

            jdbcTemplate.update(sql, documentId, content, overseasProductLine, partNum, productName, 
                              description, externalModel, specification, vectorStr, metadataJson, now, now);
            log.debug("向量插入成功: documentId={}", documentId);
        } catch (Exception e) {
            log.error("向量插入失败: documentId={}", documentId, e);
            throw new RuntimeException("向量插入失败", e);
        }
    }

    /**
     * 批量插入向量记录
     *
     * @param vectors 向量记录列表
     */
    public void batchInsertVectors(List<VectorRecord> vectors) {
        String sql = String.format(
            "INSERT INTO %s (document_id, content, overseas_product_line, part_num, product_name, description, external_model, specification, vector, metadata, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::vector, ?::jsonb, ?, ?)",
            analyticDBConfig.getTableName()
        );

        List<Object[]> batchArgs = new ArrayList<>();
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());

        for (VectorRecord record : vectors) {
            String vectorStr = arrayToVectorString(record.getEmbedding());
            String metadataJson = record.getMetadata() != null ? mapToJsonString(record.getMetadata()) : "{}";
            batchArgs.add(new Object[]{
                record.getDocumentId(),
                record.getContent(),
                record.getOverseasProductLine(),
                record.getPartNum(),
                record.getProductName(),
                record.getDescription(),
                record.getExternalModel(),
                record.getSpecification(),
                vectorStr,
                metadataJson,
                now,
                now
            });
        }

        try {
            jdbcTemplate.batchUpdate(sql, batchArgs);
            log.info("批量插入向量成功: count={}", vectors.size());
        } catch (Exception e) {
            log.error("批量插入向量失败", e);
            throw new RuntimeException("批量插入向量失败", e);
        }
    }

    /**
     * 向量相似度搜索
     *
     * @param queryEmbedding 查询向量
     * @param topK           返回结果数量
     * @return 搜索结果列表
     */
    public List<SearchResult> similaritySearch(float[] queryEmbedding, int topK) {
        return similaritySearch(queryEmbedding, topK, analyticDBConfig.getScoreThreshold());
    }

    /**
     * 向量相似度搜索（带阈值过滤）
     *
     * @param queryEmbedding 查询向量
     * @param topK           返回结果数量
     * @param threshold      相似度阈值
     * @return 搜索结果列表
     */
    public List<SearchResult> similaritySearch(float[] queryEmbedding, int topK, double threshold) {
        String vectorStr = arrayToVectorString(queryEmbedding);
        
        String sql = String.format(
            "SELECT id, part_num, product_name, description, external_model, specification, overseas_product_line, content, " +
            "1 - (vector <=> ?::vector) AS similarity_score " +
            "FROM %s " +
            "WHERE 1 - (vector <=> ?::vector) >= ? " +
            "ORDER BY vector <=> ?::vector " +
            "LIMIT ?",
            analyticDBConfig.getTableName()
        );

        try {
            // 打印SQL和参数用于调试
            log.info("执行向量相似度搜索 - SQL: {}", sql);
            log.info("执行向量相似度搜索 - 参数: threshold={}, topK={}", threshold, topK);
            
            return jdbcTemplate.query(sql, 
                (rs, rowNum) -> {
                    SearchResult result = new SearchResult();
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
                vectorStr, vectorStr, threshold, vectorStr, topK
            );
        } catch (Exception e) {
            log.error("向量相似度搜索失败", e);
            throw new RuntimeException("向量相似度搜索失败", e);
        }
    }

    /**
     * 混合检索(向量 + 关键词)
     *
     * @param queryEmbedding 查询向量
     * @param keyword        关键词（支持多个关键词，用空格分隔）
     * @param topK           返回结果数量
     * @return 搜索结果列表
     */
    public List<SearchResult> hybridSearch(float[] queryEmbedding, String keyword, int topK) {
        return hybridSearch(queryEmbedding, keyword, topK, null);
    }
    
    /**
     * 混合检索(向量 + 关键词 + 过滤条件)
     *
     * @param queryEmbedding 查询向量
     * @param keyword        关键词（支持多个关键词，用空格分隔）
     * @param topK           返回结果数量
     * @param filters        过滤条件 Map，key 为字段名，value 为字段值
     *                       例如：{"overseas_product_line": "IPC"}
     * @return 搜索结果列表
     */
    public List<SearchResult> hybridSearch(float[] queryEmbedding, String keyword, int topK, Map<String, Object> filters) {
        return hybridSearch(queryEmbedding, keyword, topK, filters, true);
    }
    
    /**
     * 混合检索(向量 + 关键词 + 过滤条件 + 可选销售状态排序)
     *
     * @param queryEmbedding 查询向量
     * @param keyword        关键词（支持多个关键词，用空格分隔）
     * @param topK           返回结果数量
     * @param filters        过滤条件 Map，key 为字段名，value 为字段值
     *                       例如：{"overseas_product_line": "IPC"}
     * @param enableSalesStatusBoost 是否启用销售状态优先级排序
     * @return 搜索结果列表
     */
    public List<SearchResult> hybridSearch(float[] queryEmbedding, String keyword, int topK, Map<String, Object> filters, boolean enableSalesStatusBoost) {
        String vectorStr = arrayToVectorString(queryEmbedding);
            
        // 使用 zh_cn 中文分词配置(已安装在数据库中)
        String tsConfig = "zh_cn";
            
        // 处理多个关键词：将空格分隔的关键词转换为 PostgreSQL 全文检索格式
        // 使用 OR 逻辑（|），只要匹配部分关键词即可，更适合推荐场景
        String processedKeyword = processKeywordsForFullTextSearch(keyword);
        
        // 构建 WHERE 条件
        StringBuilder whereClause = new StringBuilder();
        whereClause.append("to_tsvector('" + tsConfig + "', content) @@ to_tsquery('" + tsConfig + "', ?)");
        
        // 如果有过滤条件，添加到 WHERE 子句
        List<Object> params = new ArrayList<>();
        params.add(processedKeyword); // 第一个参数是关键词
        
        if (filters != null && !filters.isEmpty()) {
            for (Map.Entry<String, Object> entry : filters.entrySet()) {
                String field = entry.getKey();
                Object value = entry.getValue();
                
                // 判断字段类型，构建不同的查询条件
                if (field.equals("overseas_product_line")) {
                    // overseas_product_line 是独立的列，直接引用
                    whereClause.append(" AND overseas_product_line = ?");
                    params.add(value.toString());
                    log.info("添加过滤条件: overseas_product_line = {}", value);
                } else if (field.equals("overseas_series")) {
                    // overseas_series 也是独立的列，直接引用
                    whereClause.append(" AND overseas_series = ?");
                    params.add(value.toString());
                    log.info("添加过滤条件: overseas_series = {}", value);
                } else {
                    // 其他字段假设在 metadata 中
                    whereClause.append(" AND metadata->>? ").append("= ?");
                    params.add(field);
                    params.add(value.toString());
                    log.info("添加过滤条件: {} = {}", field, value);
                }
            }
        }
            
        // 构建销售状态权重计算（CASE WHEN 表达式）
        String salesStatusWeight = "";
        if (enableSalesStatusBoost) {
            salesStatusWeight = ", " +
                "CASE " +
                "  WHEN sales_status = 'Normal' THEN 1.0 " +
                "  WHEN sales_status = 'Delisting Warning' THEN 0.6 " +
                "  WHEN sales_status = 'Danger' THEN 0.2 " +
                "  ELSE 0.5 " +
                "END AS sales_status_weight";
        }
            
        // 使用向量相似度 + 全文检索（OR 逻辑）+ 可选的销售状态权重
        String selectFields = String.format(
            "SELECT id, part_num, product_name, description, external_model, specification, overseas_product_line, overseas_series, content, sales_status" +
            ", 1 - (vector <=> ?::vector) AS vector_score" +
            ", ts_rank(to_tsvector('%s', content), to_tsquery('%s', ?)) AS text_score" +
            ", (1 - (vector <=> ?::vector)) * 0.6 + ts_rank(to_tsvector('%s', content), to_tsquery('%s', ?)) * 0.4 AS base_score",
            tsConfig, tsConfig, tsConfig, tsConfig
        );
        
        // 如果启用销售状态排序，添加权重字段和综合评分计算
        String finalSelect;
        String orderByClause;
        
        if (enableSalesStatusBoost) {
            finalSelect = selectFields + salesStatusWeight + " FROM " + analyticDBConfig.getTableName();
            // 综合评分 = 基础评分 * 0.8 + 销售状态权重 * 0.2
            // 注意：PostgreSQL 不允许在 ORDER BY 中直接引用 SELECT 列表中的别名，需要展开表达式
            orderByClause = "ORDER BY (((1 - (vector <=> ?::vector)) * 0.6 + ts_rank(to_tsvector('" + tsConfig + "', content), to_tsquery('" + tsConfig + "', ?)) * 0.4) * 0.8 + " +
                "(CASE WHEN sales_status = 'Normal' THEN 1.0 WHEN sales_status = 'Delisting Warning' THEN 0.6 WHEN sales_status = 'Danger' THEN 0.2 ELSE 0.5 END) * 0.2) DESC";
            log.info("启用销售状态优先级排序: Normal(1.0) > Delisting Warning(0.6) > Danger(0.2)");
        } else {
            finalSelect = selectFields + " FROM " + analyticDBConfig.getTableName();
            orderByClause = "ORDER BY ((1 - (vector <=> ?::vector)) * 0.6 + ts_rank(to_tsvector('" + tsConfig + "', content), to_tsquery('" + tsConfig + "', ?)) * 0.4) DESC";
        }
            
        String sql = finalSelect + " " +
            "WHERE " + whereClause.toString() + " " +
            orderByClause + " " +
            "LIMIT ?";
    
        try {
            // 打印SQL和参数用于调试
            log.info("执行混合检索 - SQL: {}", sql);
            log.info("执行混合检索 - 参数: keyword={}, processedKeyword={}, topK={}, tsConfig={}, filters={}, salesStatusBoost={}", 
                    keyword, processedKeyword, topK, tsConfig, filters, enableSalesStatusBoost);
            
            // 构建完整的参数数组
            // 参数顺序：vectorStr(1), processedKeyword(2), vectorStr(3), processedKeyword(4), 
            //          filters..., processedKeyword(5), topK(6)
            // 如果启用销售状态排序，ORDER BY 中还需要额外的 vectorStr 和 processedKeyword
            List<Object> allParams = new ArrayList<>();
            allParams.add(vectorStr);           // ?1: vector_score
            allParams.add(processedKeyword);    // ?2: text_score
            allParams.add(vectorStr);           // ?3: base_score 向量部分
            allParams.add(processedKeyword);    // ?4: base_score 文本部分
            allParams.addAll(params);           // WHERE 条件中的参数
            
            // 如果启用销售状态排序，ORDER BY 中需要额外的参数
            if (enableSalesStatusBoost) {
                allParams.add(vectorStr);           // ?5: ORDER BY 中的向量部分
                allParams.add(processedKeyword);    // ?6: ORDER BY 中的文本部分
            }
            
            allParams.add(topK);                // 最后一个参数是 LIMIT
                
            return jdbcTemplate.query(sql,
                (rs, rowNum) -> {
                    SearchResult result = new SearchResult();
                    result.setId(rs.getString("id"));
                    result.setPartNum(rs.getString("part_num"));
                    result.setProductName(rs.getString("product_name"));
                    result.setDescription(rs.getString("description"));
                    result.setExternalModel(rs.getString("external_model"));
                    result.setSpecification(rs.getString("specification"));
                    result.setOverseasProductLine(rs.getString("overseas_product_line"));
                    result.setOverseasSeries(rs.getString("overseas_series"));
                    result.setContent(rs.getString("content"));
                    
                    // 获取销售状态
                    String salesStatus = rs.getString("sales_status");
                    result.setSalesStatus(salesStatus);
                    
                    // 如果启用了销售状态排序，使用综合评分；否则使用基础评分
                    if (enableSalesStatusBoost) {
                        double baseScore = rs.getDouble("base_score");
                        double salesWeight = rs.getDouble("sales_status_weight");
                        double finalScore = baseScore * 0.8 + salesWeight * 0.2;
                        result.setSimilarityScore(finalScore);
                        
                        // 在日志中显示销售状态信息
                        log.debug("产品 {} - 销售状态: {}, 基础分: {:.4f}, 状态权重: {:.2f}, 综合分: {:.4f}", 
                                result.getPartNum(), salesStatus, baseScore, salesWeight, finalScore);
                    } else {
                        result.setSimilarityScore(rs.getDouble("base_score"));
                    }
                        
                    return result;
                },
                allParams.toArray()
            );
        } catch (Exception e) {
            log.error("混合检索失败(全文检索),尝试回退到 LIKE 查询", e);
            // 如果全文检索失败,回退到 LIKE 查询
            return fallbackLikeSearch(keyword, topK, filters);
        }
    }

    /**
     * 处理关键词以适配 PostgreSQL 全文检索
     * 将空格分隔的转换为 "|" 连接的 OR 查询（更适合推荐场景）
     * 例如："2MP 2.8mm IP67" -> "2MP | 2.8mm | IP67"
     * 这样只要匹配其中部分关键词就能返回结果，并按相关性排序
     *
     * @param keyword 原始关键词字符串
     * @return 处理后的关键词字符串
     */
    private String processKeywordsForFullTextSearch(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return "";
        }
        
        // 按空格分割关键词
        String[] keywords = keyword.trim().split("\\s+");
        
        // 过滤空字符串并转义特殊字符
        List<String> validKeywords = new ArrayList<>();
        for (String k : keywords) {
            String trimmed = k.trim();
            if (!trimmed.isEmpty()) {
                // 转义 PostgreSQL 全文检索特殊字符
                String escaped = escapeFullTextSearchKeyword(trimmed);
                validKeywords.add(escaped);
            }
        }
        
        // 用 "|" 连接，表示 OR 关系（匹配任一关键词即可）
        // 这样更符合推荐场景：即使不能全部满足，也能返回部分匹配的产品
        return String.join(" | ", validKeywords);
    }

    /**
     * 转义 PostgreSQL 全文检索特殊字符
     */
    private String escapeFullTextSearchKeyword(String keyword) {
        // PostgreSQL 全文检索特殊字符: & | ! ( ) < > : *
        // 这些字符需要转义或移除
        return keyword.replaceAll("[&|!()<>:*]", " ");
    }

    /**
     * 纯文本检索(不使用向量,仅用于调试)
     *
     * @param keyword 关键词
     * @param topK    返回数量
     * @return 搜索结果
     */
    public List<SearchResult> textOnlySearch(String keyword, int topK) {
        String sql = String.format(
            "SELECT id, part_num, product_name, description, external_model, specification, overseas_product_line, content, " +
            "ts_rank(to_tsvector('zh_cn', content), plainto_tsquery('zh_cn', ?)) AS text_score " +
            "FROM %s " +
            "WHERE to_tsvector('zh_cn', content) @@ plainto_tsquery('zh_cn', ?) " +
            "ORDER BY text_score DESC " +
            "LIMIT ?",
            analyticDBConfig.getTableName()
        );
            
        try {
            log.info("执行纯文本检索 - keyword: {}", keyword);
            return jdbcTemplate.query(sql,
                (rs, rowNum) -> {
                    SearchResult result = new SearchResult();
                    result.setId(rs.getString("id"));
                    result.setPartNum(rs.getString("part_num"));
                    result.setProductName(rs.getString("product_name"));
                    result.setDescription(rs.getString("description"));
                    result.setExternalModel(rs.getString("external_model"));
                    result.setSpecification(rs.getString("specification"));
                    result.setOverseasProductLine(rs.getString("overseas_product_line"));
                    result.setContent(rs.getString("content"));
                    result.setSimilarityScore(rs.getDouble("text_score"));
                    return result;
                },
                keyword, keyword, topK
            );
        } catch (Exception e) {
            log.error("纯文本检索失败", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 回退方案:使用 LIKE 进行关键词搜索
     */
    private List<SearchResult> fallbackLikeSearch(String keyword, int topK) {
        return fallbackLikeSearch(keyword, topK, null);
    }
    
    /**
     * 回退方案:使用 LIKE 进行关键词搜索（支持过滤条件）
     */
    private List<SearchResult> fallbackLikeSearch(String keyword, int topK, Map<String, Object> filters) {
        StringBuilder whereClause = new StringBuilder("content LIKE ?");
        List<Object> params = new ArrayList<>();
        params.add("%" + keyword + "%");
        
        // 添加过滤条件
        if (filters != null && !filters.isEmpty()) {
            for (Map.Entry<String, Object> entry : filters.entrySet()) {
                String field = entry.getKey();
                Object value = entry.getValue();
                
                if (field.equals("overseas_product_line")) {
                    whereClause.append(" AND overseas_product_line = ?");
                    params.add(value.toString());
                } else {
                    whereClause.append(" AND metadata->>? ").append("= ?");
                    params.add(field);
                    params.add(value.toString());
                }
            }
        }
        
        String sql = String.format(
            "SELECT id, part_num, product_name, description, external_model, specification, overseas_product_line, content, " +
            "0.5 AS vector_score " +  // 给一个默认分数
            "FROM %s " +
            "WHERE " + whereClause.toString() + " " +
            "LIMIT ?",
            analyticDBConfig.getTableName()
        );
        
        // 添加 LIMIT 参数
        params.add(topK);
        
        try {
            log.info("使用 LIKE 回退查询 - keyword: {}, filters: {}", keyword, filters);
            return jdbcTemplate.query(sql,
                (rs, rowNum) -> {
                    SearchResult result = new SearchResult();
                    result.setId(rs.getString("id"));
                    result.setPartNum(rs.getString("part_num"));
                    result.setProductName(rs.getString("product_name"));
                    result.setDescription(rs.getString("description"));
                    result.setExternalModel(rs.getString("external_model"));
                    result.setSpecification(rs.getString("specification"));
                    result.setOverseasProductLine(rs.getString("overseas_product_line"));
                    result.setContent(rs.getString("content"));
                    result.setSimilarityScore(rs.getDouble("vector_score"));
                    return result;
                },
                params.toArray()
            );
        } catch (Exception e) {
            log.error("LIKE 回退查询也失败", e);
            throw new RuntimeException("搜索失败", e);
        }
    }

    /**
     * 验证关键词是否存在于数据库中(用于调试)
     *
     * @param keyword 要查找的关键词
     * @return 是否找到包含该关键词的记录
     */
    public boolean verifyKeywordExists(String keyword) {
        // 首先尝试全文检索方式
        String sql1 = String.format(
            "SELECT COUNT(*) FROM %s WHERE to_tsvector('zh_cn', content) @@ plainto_tsquery('zh_cn', ?)",
            analyticDBConfig.getTableName()
        );
            
        // 备用:使用LIKE模糊查询
        String sql2 = String.format(
            "SELECT COUNT(*) FROM %s WHERE content LIKE ?",
            analyticDBConfig.getTableName()
        );
            
        // 查看分词结果
        String sql3 = String.format(
            "SELECT to_tsvector('zh_cn', ?) AS tsvector_result"
        );
    
        try {
            // 先查看分词结果
            String tsvectorResult = jdbcTemplate.queryForObject(sql3, String.class, keyword);
            log.info("关键词 '{}' 的分词结果: {}", keyword, tsvectorResult);
                
            // 尝试全文检索方式
            Integer count1 = jdbcTemplate.queryForObject(sql1, Integer.class, keyword);
            log.info("全文检索方式(zh_cn)找到 '{}' 的记录数: {}", keyword, count1 != null ? count1 : 0);
                
            if (count1 != null && count1 > 0) {
                // 打印一条示例数据
                String sampleSql = String.format(
                    "SELECT id, part_num, product_name, overseas_product_line, LEFT(content, 100) as preview FROM %s WHERE to_tsvector('zh_cn', content) @@ plainto_tsquery('zh_cn', ?) LIMIT 1",
                    analyticDBConfig.getTableName()
                );
                Map<String, Object> sample = jdbcTemplate.queryForMap(sampleSql, keyword);
                log.info("示例数据: {}", sample);
                return true;
            }
                
            // 如果全文检索没找到,尝试LIKE查询
            Integer count2 = jdbcTemplate.queryForObject(sql2, Integer.class, "%" + keyword + "%");
            log.info("LIKE查询方式找到 '{}' 的记录数: {}", keyword, count2 != null ? count2 : 0);
                
            if (count2 != null && count2 > 0) {
                // 打印一条示例数据
                String sampleSql = String.format(
                    "SELECT id, part_num, product_name, overseas_product_line, LEFT(content, 100) as preview FROM %s WHERE content LIKE ? LIMIT 1",
                    analyticDBConfig.getTableName()
                );
                Map<String, Object> sample = jdbcTemplate.queryForMap(sampleSql, "%" + keyword + "%");
                log.info("LIKE查询示例数据: {}", sample);
            }
                
            return count2 != null && count2 > 0;
        } catch (Exception e) {
            log.error("验证关键词存在性失败", e);
            return false;
        }
    }

    /**
     * 获取表中所有记录的简要信息（用于调试）
     *
     * @param limit 限制返回的记录数
     * @return 记录列表
     */
    public List<Map<String, Object>> getSampleRecords(int limit) {
        String sql = String.format(
            "SELECT id, part_num, product_name, overseas_product_line, LEFT(content, 100) as content_preview FROM %s LIMIT ?",
            analyticDBConfig.getTableName()
        );
        
        try {
            return jdbcTemplate.queryForList(sql, limit);
        } catch (Exception e) {
            log.error("获取样本记录失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 删除向量记录
     *
     * @param documentId 文档ID
     */
    public void deleteVector(String documentId) {
        String sql = String.format("DELETE FROM %s WHERE document_id = ?", analyticDBConfig.getTableName());
        
        try {
            jdbcTemplate.update(sql, documentId);
            log.debug("向量删除成功: documentId={}", documentId);
        } catch (Exception e) {
            log.error("向量删除失败: documentId={}", documentId, e);
            throw new RuntimeException("向量删除失败", e);
        }
    }

    /**
     * 更新向量记录
     *
     * @param documentId 文档ID
     * @param content    新的内容
     * @param embedding  新的向量
     * @param metadata   新的元数据
     */
    public void updateVector(String documentId, String content, float[] embedding, Map<String, Object> metadata) {
        String sql = String.format(
            "UPDATE %s SET content = ?, vector = ?::vector, metadata = ?::jsonb, updated_at = ? WHERE document_id = ?",
            analyticDBConfig.getTableName()
        );

        try {
            String vectorStr = arrayToVectorString(embedding);
            String metadataJson = metadata != null ? mapToJsonString(metadata) : "{}";
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());

            jdbcTemplate.update(sql, content, vectorStr, metadataJson, now, documentId);
            log.debug("向量更新成功: documentId={}", documentId);
        } catch (Exception e) {
            log.error("向量更新失败: documentId={}", documentId, e);
            throw new RuntimeException("向量更新失败", e);
        }
    }
    
    /**
     * 更新向量记录（包含完整字段）
     *
     * @param documentId 文档ID
     * @param content    新的内容
     * @param overseasProductLine 海外产品线
     * @param partNum    产品编号
     * @param productName 产品名称
     * @param description 产品描述
     * @param externalModel 外部型号
     * @param specification 规格参数
     * @param embedding  新的向量
     * @param metadata   新的元数据
     */
    public void updateVectorWithFields(String documentId, String content, String overseasProductLine,
                                      String partNum, String productName, String description,
                                      String externalModel, String specification,
                                      float[] embedding, Map<String, Object> metadata) {
        String sql = String.format(
            "UPDATE %s SET content = ?, overseas_product_line = ?, part_num = ?, product_name = ?, description = ?, external_model = ?, specification = ?, vector = ?::vector, metadata = ?::jsonb, updated_at = ? WHERE document_id = ?",
            analyticDBConfig.getTableName()
        );

        try {
            String vectorStr = arrayToVectorString(embedding);
            String metadataJson = metadata != null ? mapToJsonString(metadata) : "{}";
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());

            jdbcTemplate.update(sql, content, overseasProductLine, partNum, productName, description,
                              externalModel, specification, vectorStr, metadataJson, now, documentId);
            log.debug("向量更新成功: documentId={}", documentId);
        } catch (Exception e) {
            log.error("向量更新失败: documentId={}", documentId, e);
            throw new RuntimeException("向量更新失败", e);
        }
    }

    /**
     * 将浮点数组转换为向量字符串
     */
    private String arrayToVectorString(float[] array) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < array.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(array[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 将 Map 转换为 JSON 字符串（简化版）
     */
    private String mapToJsonString(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            sb.append("\"").append(entry.getKey()).append("\":");
            
            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append("\"").append(value).append("\"");
            } else {
                sb.append(value);
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * 将 JSON 字符串转换为 Map（简化版）
     */
    private Map<String, Object> jsonStringToMap(String json) {
        // 这里使用简化的解析，生产环境建议使用 Jackson 或 Gson
        Map<String, Object> map = new HashMap<>();
        // TODO: 实现完整的 JSON 解析
        return map;
    }

    /**
     * 向量记录内部类
     */
    @lombok.Data
    public static class VectorRecord {
        private String documentId;
        private String content;
        private String overseasProductLine;
        private String partNum;
        private String productName;
        private String description;
        private String externalModel;
        private String specification;
        private float[] embedding;
        private Map<String, Object> metadata;
    }

    /**
     * 搜索结果内部类
     */
    @lombok.Data
    public static class SearchResult {
        private String id;
        private String partNum;
        private String productName;
        private String description;
        private String externalModel;
        private String specification;
        private String overseasProductLine;
        private String overseasSeries;
        private String salesStatus;
        private String content;
        private double similarityScore;
    }
}
