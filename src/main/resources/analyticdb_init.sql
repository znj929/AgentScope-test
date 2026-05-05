-- AnalyticDB PostgreSQL 向量库初始化脚本
-- 用于创建向量存储表和必要的索引

-- 1. 启用 pgvector 扩展（如果尚未启用）
-- AnalyticDB PostgreSQL 通常已经内置了向量检索功能
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. 创建知识库向量表
CREATE TABLE IF NOT EXISTS knowledge_vectors (
    id SERIAL PRIMARY KEY,                          -- 主键 ID
    document_id VARCHAR(255) NOT NULL,              -- 文档唯一标识
    content TEXT NOT NULL,                          -- 文档内容
    overseas_product_line VARCHAR(255),             -- 海外产品线（独立字段）
    part_num VARCHAR(255),                          -- 产品编号
    product_name VARCHAR(500),                      -- 产品名称
    description TEXT,                               -- 产品描述
    external_model VARCHAR(255),                    -- 外部型号
    specification TEXT,                             -- 规格参数
    metadata JSONB,                                 -- 元数据（JSON 格式）
    embedding vector(1536),                         -- 向量字段，维度根据实际需求调整
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- 创建时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP  -- 更新时间
);

-- 3. 创建索引以加速向量检索
-- 使用 IVFFlat 索引（适合中等规模数据集）
CREATE INDEX IF NOT EXISTS idx_knowledge_vectors_embedding 
ON knowledge_vectors 
USING ivfflat (embedding vector_cosine_ops) 
WITH (lists = 100);

-- 或者使用 HNSW 索引（适合大规模数据集，检索更快但占用更多内存）
-- CREATE INDEX IF NOT EXISTS idx_knowledge_vectors_embedding_hnsw 
-- ON knowledge_vectors 
-- USING hnsw (embedding vector_cosine_ops) 
-- WITH (m = 16, ef_construction = 64);

-- 4. 创建文档 ID 索引
CREATE INDEX IF NOT EXISTS idx_knowledge_vectors_document_id 
ON knowledge_vectors (document_id);

-- 5. 创建时间索引（用于按时间范围查询）
CREATE INDEX IF NOT EXISTS idx_knowledge_vectors_created_at 
ON knowledge_vectors (created_at);

-- 6. 创建全文检索索引（用于混合检索）
-- 使用 'simple' 配置（PostgreSQL 内置，无需额外插件）
CREATE INDEX IF NOT EXISTS idx_knowledge_vectors_content_gin 
ON knowledge_vectors 
USING gin (to_tsvector('simple', content));

-- 7. 添加注释
COMMENT ON TABLE knowledge_vectors IS '知识库向量存储表';
COMMENT ON COLUMN knowledge_vectors.document_id IS '文档唯一标识符';
COMMENT ON COLUMN knowledge_vectors.content IS '文档文本内容';
COMMENT ON COLUMN knowledge_vectors.metadata IS '文档元数据（JSON格式）';
COMMENT ON COLUMN knowledge_vectors.embedding IS '向量嵌入（1536维）';
COMMENT ON COLUMN knowledge_vectors.created_at IS '记录创建时间';
COMMENT ON COLUMN knowledge_vectors.updated_at IS '记录更新时间';

-- 8. 示例插入语句（测试用）
-- INSERT INTO knowledge_vectors (document_id, content, metadata, embedding) 
-- VALUES (
--     'doc_001', 
--     '这是一段测试文本内容', 
--     '{"source": "test", "category": "example"}'::jsonb,
--     '[0.1, 0.2, 0.3, ...]'::vector  -- 需要填入实际的 1536 维向量
-- );

-- 9. 示例查询：向量相似度搜索
-- SELECT id, document_id, content, metadata, 
--        1 - (embedding <=> '[0.1, 0.2, ...]'::vector) AS similarity_score
-- FROM knowledge_vectors
-- ORDER BY embedding <=> '[0.1, 0.2, ...]'::vector
-- LIMIT 3;

-- 10. 示例查询：混合检索（向量 + 关键词）
-- SELECT id, document_id, content, metadata,
--        1 - (embedding <=> '[0.1, 0.2, ...]'::vector) AS vector_score,
--        ts_rank(to_tsvector('simple', content), plainto_tsquery('simple', 'keyword')) AS text_score
-- FROM knowledge_vectors
-- WHERE to_tsvector('simple', content) @@ plainto_tsquery('simple', 'keyword')
-- ORDER BY vector_score DESC, text_score DESC
-- LIMIT 3;
