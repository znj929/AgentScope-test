package org.example.knowledge;

import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * AnalyticDB PostgreSQL 向量库使用示例
 * 演示如何进行向量的增删改查操作
 */
@Slf4j
public class AnalyticDBUsageExample {

    private final AnalyticDBVectorStore vectorStore;

    /**
     * 构造函数
     *
     * @param vectorStore 向量存储
     */
    public AnalyticDBUsageExample(AnalyticDBVectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 初始化示例数据
     */
    @PostConstruct
    public void initExample() {
        log.info("AnalyticDB PostgreSQL 向量库使用示例已加载");
    }

    /**
     * 示例1: 插入单个向量
     */
    public void exampleInsertSingleVector() {
        // 准备数据
        String documentId = "doc_001";
        String content = "AnalyticDB PostgreSQL 是阿里云提供的云原生数据仓库服务";
        
        // 模拟向量（实际应用中应该通过 embedding 模型生成）
        float[] embedding = new float[1536];
        Arrays.fill(embedding, 0.1f); // 这里只是示例
        
        // 元数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "官方文档");
        metadata.put("category", "产品介绍");
        metadata.put("author", "阿里云");

        // 插入向量
        vectorStore.insertVector(documentId, content, embedding, metadata);
        log.info("示例1完成: 插入单个向量");
    }

    /**
     * 示例2: 批量插入向量
     */
    public void exampleBatchInsertVectors() {
        List<AnalyticDBVectorStore.VectorRecord> records = new ArrayList<>();

        // 创建多条记录
        for (int i = 0; i < 5; i++) {
            AnalyticDBVectorStore.VectorRecord record = new AnalyticDBVectorStore.VectorRecord();
            record.setDocumentId("doc_batch_" + i);
            record.setContent("这是第 " + i + " 条测试文档内容");
            
            // 模拟向量
            float[] embedding = new float[1536];
            Arrays.fill(embedding, 0.1f + i * 0.01f);
            record.setEmbedding(embedding);
            
            // 元数据
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("index", i);
            metadata.put("type", "batch_test");
            record.setMetadata(metadata);
            
            records.add(record);
        }

        // 批量插入
        vectorStore.batchInsertVectors(records);
        log.info("示例2完成: 批量插入 {} 条向量", records.size());
    }

    /**
     * 示例3: 向量相似度搜索
     */
    public void exampleSimilaritySearch() {
        // 准备查询向量
        float[] queryEmbedding = new float[1536];
        Arrays.fill(queryEmbedding, 0.1f);

        // 执行相似度搜索
        List<AnalyticDBVectorStore.SearchResult> results = 
            vectorStore.similaritySearch(queryEmbedding, 3);

        // 处理结果
        log.info("示例3完成: 向量相似度搜索返回 {} 条结果", results.size());
        for (AnalyticDBVectorStore.SearchResult result : results) {
            log.info("  - Document ID: {}, 相似度: {:.4f}", 
                result.getDocumentId(), result.getSimilarityScore());
            log.info("    内容: {}", result.getContent().substring(0, Math.min(50, result.getContent().length())));
        }
    }

    /**
     * 示例4: 带阈值的向量搜索
     */
    public void exampleSearchWithThreshold() {
        float[] queryEmbedding = new float[1536];
        Arrays.fill(queryEmbedding, 0.1f);

        // 设置相似度阈值为 0.7
        List<AnalyticDBVectorStore.SearchResult> results = 
            vectorStore.similaritySearch(queryEmbedding, 5, 0.7);

        log.info("示例4完成: 带阈值搜索返回 {} 条结果（阈值: 0.7）", results.size());
    }

    /**
     * 示例5: 混合检索（向量 + 关键词）
     */
    public void exampleHybridSearch() {
        float[] queryEmbedding = new float[1536];
        Arrays.fill(queryEmbedding, 0.1f);
        
        String keyword = "AnalyticDB";

        List<AnalyticDBVectorStore.SearchResult> results = 
            vectorStore.hybridSearch(queryEmbedding, keyword, 3);

        log.info("示例5完成: 混合检索返回 {} 条结果", results.size());
        for (AnalyticDBVectorStore.SearchResult result : results) {
            log.info("  - Document ID: {}, 向量相似度: {:.4f}", 
                result.getDocumentId(), result.getSimilarityScore());
        }
    }

    /**
     * 示例6: 更新向量
     */
    public void exampleUpdateVector() {
        String documentId = "doc_001";
        String newContent = "AnalyticDB PostgreSQL 是阿里云提供的云原生数据仓库服务（已更新）";
        
        float[] newEmbedding = new float[1536];
        Arrays.fill(newEmbedding, 0.2f);
        
        Map<String, Object> newMetadata = new HashMap<>();
        newMetadata.put("source", "官方文档");
        newMetadata.put("updated", true);

        vectorStore.updateVector(documentId, newContent, newEmbedding, newMetadata);
        log.info("示例6完成: 更新向量");
    }

    /**
     * 示例7: 删除向量
     */
    public void exampleDeleteVector() {
        String documentId = "doc_001";
        
        vectorStore.deleteVector(documentId);
        log.info("示例7完成: 删除向量");
    }

    /**
     * 完整工作流程示例
     */
    public void exampleCompleteWorkflow() {
        log.info("=== 开始完整工作流程示例 ===");

        // 1. 批量插入数据
        log.info("步骤1: 批量插入数据");
        exampleBatchInsertVectors();

        // 2. 执行相似度搜索
        log.info("步骤2: 执行相似度搜索");
        exampleSimilaritySearch();

        // 3. 执行混合检索
        log.info("步骤3: 执行混合检索");
        exampleHybridSearch();

        // 4. 更新数据
        log.info("步骤4: 更新数据");
        exampleUpdateVector();

        // 5. 删除数据
        log.info("步骤5: 删除数据");
        exampleDeleteVector();

        log.info("=== 完整工作流程示例结束 ===");
    }

    /**
     * 实际应用示例：构建知识库问答系统
     */
    public void exampleKnowledgeBaseQA() {
        log.info("=== 知识库问答系统示例 ===");

        // 1. 准备知识库文档
        List<String> documents = Arrays.asList(
            "AnalyticDB PostgreSQL 是阿里云提供的云原生数据仓库服务",
            "支持向量检索功能，可用于 AI 应用场景",
            "兼容 PostgreSQL 生态，支持标准的 SQL 语法",
            "提供高性能的向量相似度搜索能力",
            "支持混合检索，结合向量和关键词检索"
        );

        // 2. 为每个文档生成向量并存储
        log.info("步骤1: 构建知识库");
        for (int i = 0; i < documents.size(); i++) {
            AnalyticDBVectorStore.VectorRecord record = new AnalyticDBVectorStore.VectorRecord();
            record.setDocumentId("kb_doc_" + i);
            record.setContent(documents.get(i));
            
            // 实际应用中，这里应该调用 embedding API 生成向量
            float[] embedding = new float[1536];
            Arrays.fill(embedding, 0.1f + i * 0.05f);
            record.setEmbedding(embedding);
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", "knowledge_base");
            metadata.put("index", i);
            record.setMetadata(metadata);
            
            List<AnalyticDBVectorStore.VectorRecord> singleRecord = Collections.singletonList(record);
            vectorStore.batchInsertVectors(singleRecord);
        }

        // 3. 用户提问
        String userQuestion = "AnalyticDB PostgreSQL 有什么特点？";
        log.info("步骤2: 用户提问: {}", userQuestion);

        // 4. 将问题转换为向量并检索
        float[] questionEmbedding = new float[1536];
        Arrays.fill(questionEmbedding, 0.15f); // 实际应该通过 embedding 模型生成

        List<AnalyticDBVectorStore.SearchResult> relevantDocs = 
            vectorStore.similaritySearch(questionEmbedding, 3);

        log.info("步骤3: 检索到 {} 条相关文档", relevantDocs.size());
        for (AnalyticDBVectorStore.SearchResult doc : relevantDocs) {
            log.info("  - 相关内容: {}", doc.getContent());
        }

        // 5. 基于检索结果生成答案（这里需要调用 LLM）
        log.info("步骤4: 基于检索结果，调用 LLM 生成答案");
        log.info("(实际应用中，将相关文档作为上下文传递给 LLM)");

        log.info("=== 知识库问答系统示例结束 ===");
    }
}
