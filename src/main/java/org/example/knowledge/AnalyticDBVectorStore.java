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
            "INSERT INTO %s (document_id, content, embedding, metadata, created_at, updated_at) VALUES (?, ?, ?::vector, ?::jsonb, ?, ?)",
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
     * 批量插入向量记录
     *
     * @param vectors 向量记录列表
     */
    public void batchInsertVectors(List<VectorRecord> vectors) {
        String sql = String.format(
            "INSERT INTO %s (document_id, content, embedding, metadata, created_at, updated_at) VALUES (?, ?, ?::vector, ?::jsonb, ?, ?)",
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
            "SELECT id, document_id, content, metadata, " +
            "1 - (embedding <=> ?::vector) AS similarity_score " +
            "FROM %s " +
            "WHERE 1 - (embedding <=> ?::vector) >= ? " +
            "ORDER BY embedding <=> ?::vector " +
            "LIMIT ?",
            analyticDBConfig.getTableName()
        );

        try {
            return jdbcTemplate.query(sql, 
                (rs, rowNum) -> {
                    SearchResult result = new SearchResult();
                    result.setId(rs.getInt("id"));
                    result.setDocumentId(rs.getString("document_id"));
                    result.setContent(rs.getString("content"));
                    result.setSimilarityScore(rs.getDouble("similarity_score"));
                    
                    String metadataJson = rs.getString("metadata");
                    if (metadataJson != null && !metadataJson.equals("{}")) {
                        result.setMetadata(jsonStringToMap(metadataJson));
                    }
                    
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
     * 混合检索（向量 + 关键词）
     *
     * @param queryEmbedding 查询向量
     * @param keyword        关键词
     * @param topK           返回结果数量
     * @return 搜索结果列表
     */
    public List<SearchResult> hybridSearch(float[] queryEmbedding, String keyword, int topK) {
        String vectorStr = arrayToVectorString(queryEmbedding);
        
        String sql = String.format(
            "SELECT id, document_id, content, metadata, " +
            "1 - (embedding <=> ?::vector) AS vector_score, " +
            "ts_rank(to_tsvector('chinese', content), plainto_tsquery('chinese', ?)) AS text_score " +
            "FROM %s " +
            "WHERE to_tsvector('chinese', content) @@ plainto_tsquery('chinese', ?) " +
            "ORDER BY vector_score DESC, text_score DESC " +
            "LIMIT ?",
            analyticDBConfig.getTableName()
        );

        try {
            return jdbcTemplate.query(sql,
                (rs, rowNum) -> {
                    SearchResult result = new SearchResult();
                    result.setId(rs.getInt("id"));
                    result.setDocumentId(rs.getString("document_id"));
                    result.setContent(rs.getString("content"));
                    result.setSimilarityScore(rs.getDouble("vector_score"));
                    
                    String metadataJson = rs.getString("metadata");
                    if (metadataJson != null && !metadataJson.equals("{}")) {
                        result.setMetadata(jsonStringToMap(metadataJson));
                    }
                    
                    return result;
                },
                vectorStr, keyword, keyword, topK
            );
        } catch (Exception e) {
            log.error("混合检索失败", e);
            throw new RuntimeException("混合检索失败", e);
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
            "UPDATE %s SET content = ?, embedding = ?::vector, metadata = ?::jsonb, updated_at = ? WHERE document_id = ?",
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
        private float[] embedding;
        private Map<String, Object> metadata;
    }

    /**
     * 搜索结果内部类
     */
    @lombok.Data
    public static class SearchResult {
        private int id;
        private String documentId;
        private String content;
        private Map<String, Object> metadata;
        private double similarityScore;
    }
}
