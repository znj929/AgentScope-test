package org.example.knowledge;

import io.agentscope.core.ReActAgent;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AnalyticDB PostgreSQL 向量库配置
 * 用于配置和初始化基于 AnalyticDB PostgreSQL 的知识库
 * 
 * 注意: AgentScope Java 1.0.9 版本目前没有原生的 AnalyticDB 集成
 * 本配置提供 AnalyticDBVectorStore Bean，可以手动集成到 Agent 中
 */
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "agentscope.analyticdb")
@Data
public class AnalyticDBConfig {

    /**
     * AnalyticDB PostgreSQL 连接地址
     * 格式: jdbc:postgresql://host:port/database
     */
    private String host;

    /**
     * 端口号，默认 5432
     */
    private int port;

    /**
     * 数据库名称
     */
    private String database;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 向量表名称
     */
    private String tableName;

    /**
     * 向量维度，默认 1536（与 OpenAI embeddings 一致）
     */
    private int vectorDimension;

    /**
     * 返回 Top K 个最相关文档
     */
    private int topK;

    /**
     * 相似度阈值 (0-1)，低于此阈值的文档将被过滤
     */
    private double scoreThreshold;

    /**
     * 是否启用混合检索（结合关键词和向量检索）
     */
    private boolean enableHybridSearch;

    /**
     * 阿里云 AccessKey ID
     */
    private String accessKeyId;

    /**
     * 阿里云 AccessKey Secret
     */
    private String accessKeySecret;

    /**
     * AnalyticDB 实例 ID
     */
    private String dbInstanceId;

    /**
     * 阿里云区域 ID
     */
    private String regionId;

    /**
     * Embedding 模型名称
     */
    private String embeddingModel;

    /**
     * Embedding 向量维度
     */
    private int embeddingDimension;

    /**
     * 阿里云 Endpoint
     */
    private String endpoint;

    /**
     * Rerank 模型名称，默认 qwen3-rerank
     */
    private String rerankModel;

    /**
     * Rerank 返回的 Top K 数量，默认 5
     */
    private Integer rerankTopK;

    /**
     * 是否启用 Rerank，默认 true
     */
    private Boolean enableRerank;

    /**
     * Rerank 候选集数量（第一阶段召回的数量），默认 20
     */
    private Integer rerankCandidateCount;

    /**
     * 是否启用多路召回融合，默认 false
     */
    private Boolean enableMultiPathRecall;

    /**
     * 获取完整的 JDBC URL
     */
    public String getJdbcUrl() {
        return String.format("jdbc:postgresql://%s:%d/%s", host, port, database);
    }

    /**
     * 检查阿里云认证信息是否已配置
     */
    public boolean hasAliyunCredentials() {
        return accessKeyId != null && !accessKeyId.isEmpty() 
            && accessKeySecret != null && !accessKeySecret.isEmpty()
            && dbInstanceId != null && !dbInstanceId.isEmpty();
    }

    /**
     * 创建 AnalyticDB PostgreSQL 向量存储 Bean
     * 可以通过注入此 Bean 来使用 AnalyticDB 的向量检索功能
     */
    @Bean
    public AnalyticDBVectorStore analyticDBVectorStore() {
        log.info("初始化 AnalyticDB PostgreSQL 向量存储");
        log.info("连接地址: {}:{}", host, port);
        log.info("数据库: {}", database);
        log.info("向量表: {}", tableName);
        log.info("向量维度: {}", vectorDimension);
        
        return new AnalyticDBVectorStore(this);
    }

    /**
     * 创建使用示例 Bean
     */
    @Bean
    public AnalyticDBUsageExample analyticDBUsageExample(AnalyticDBVectorStore vectorStore) {
        return new AnalyticDBUsageExample(vectorStore);
    }
}
