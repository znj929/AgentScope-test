# AnalyticDB PostgreSQL 向量库集成指南

## 概述

本项目集成了阿里云 AnalyticDB PostgreSQL 版作为向量数据库，用于支持 RAG（检索增强生成）应用场景。

## 功能特性

- ✅ 向量存储与管理
- ✅ 向量相似度搜索（余弦相似度）
- ✅ 混合检索（向量 + 关键词）
- ✅ 批量插入与更新
- ✅ 灵活的元数据管理
- ✅ Spring Boot 配置化集成

## 前置条件

1. **AnalyticDB PostgreSQL 实例**
   - 已创建 AnalyticDB PostgreSQL 实例
   - 获取连接信息（host, port, database, username, password）

2. **Java 环境**
   - JDK 17+
   - Maven 3.6+

## 快速开始

### 1. 配置数据库连接

在 `src/main/resources/application.properties` 中配置 AnalyticDB 连接信息：

```properties
# AnalyticDB PostgreSQL 向量库配置
agentscope.analyticdb.host=your-host.pg.rds.aliyuncs.com
agentscope.analyticdb.port=5432
agentscope.analyticdb.database=your_database
agentscope.analyticdb.username=your_username
agentscope.analyticdb.password=your_password
agentscope.analyticdb.table-name=knowledge_vectors
agentscope.analyticdb.vector-dimension=1536
agentscope.analyticdb.top-k=3
agentscope.analyticdb.score-threshold=0.5
agentscope.analyticdb.enable-hybrid-search=false
```

### 2. 初始化数据库表

执行 SQL 初始化脚本：

```bash
psql -h your-host.pg.rds.aliyuncs.com -p 5432 -U your_username -d your_database -f src/main/resources/analyticdb_init.sql
```

或者通过 pgAdmin、DBeaver 等工具执行 `analyticdb_init.sql` 脚本。

### 3. 使用向量库

#### 方式一：通过 AnalyticDBVectorStore 直接使用

```java
@Autowired
private AnalyticDBVectorStore vectorStore;

// 插入向量
String documentId = "doc_001";
String content = "文档内容";
float[] embedding = getEmbedding(content); // 通过 embedding 模型生成
Map<String, Object> metadata = new HashMap<>();
metadata.put("source", "test");

vectorStore.insertVector(documentId, content, embedding, metadata);

// 相似度搜索
List<SearchResult> results = vectorStore.similaritySearch(queryEmbedding, 3);
```

#### 方式二：通过 ReActAgent 集成 RAG

```java
@Autowired
private ReActAgent analyticDBAgent;

// Agent 会自动使用 AnalyticDB 知识库进行检索增强
Msg response = analyticDBAgent.call(userMessage).block();
```

### 4. 运行示例代码

```java
@Autowired
private AnalyticDBUsageExample example;

// 运行完整工作流程示例
example.exampleCompleteWorkflow();

// 运行知识库问答示例
example.exampleKnowledgeBaseQA();
```

## 核心组件说明

### 1. AnalyticDBConfig

配置类，管理 AnalyticDB 连接参数和知识库配置。

**主要配置项：**
- `host`: 数据库主机地址
- `port`: 端口号（默认 5432）
- `database`: 数据库名称
- `username/password`: 认证信息
- `tableName`: 向量表名
- `vectorDimension`: 向量维度（默认 1536，适配 OpenAI embeddings）
- `topK`: 返回结果数量
- `scoreThreshold`: 相似度阈值
- `enableHybridSearch`: 是否启用混合检索

### 2. AnalyticDBVectorStore

向量存储操作类，提供完整的 CRUD 功能。

**主要方法：**

| 方法 | 说明 |
|------|------|
| `insertVector()` | 插入单个向量记录 |
| `batchInsertVectors()` | 批量插入向量记录 |
| `similaritySearch()` | 向量相似度搜索 |
| `hybridSearch()` | 混合检索（向量 + 关键词） |
| `updateVector()` | 更新向量记录 |
| `deleteVector()` | 删除向量记录 |

### 3. AnalyticDBUsageExample

使用示例类，展示各种常见场景的代码示例。

## 高级用法

### 向量相似度搜索

```java
// 基本搜索
float[] queryEmbedding = getEmbedding("用户查询");
List<SearchResult> results = vectorStore.similaritySearch(queryEmbedding, 5);

// 带阈值过滤的搜索
List<SearchResult> filteredResults = vectorStore.similaritySearch(queryEmbedding, 5, 0.7);
```

### 混合检索

结合向量语义搜索和关键词匹配，提高检索准确性：

```java
float[] queryEmbedding = getEmbedding("查询内容");
String keyword = "关键词";
List<SearchResult> results = vectorStore.hybridSearch(queryEmbedding, keyword, 5);
```

### 批量操作

```java
List<VectorRecord> records = new ArrayList<>();

for (Document doc : documents) {
    VectorRecord record = new VectorRecord();
    record.setDocumentId(doc.getId());
    record.setContent(doc.getContent());
    record.setEmbedding(generateEmbedding(doc.getContent()));
    record.setMetadata(doc.getMetadata());
    records.add(record);
}

vectorStore.batchInsertVectors(records);
```

## 数据库表结构

```sql
CREATE TABLE knowledge_vectors (
    id SERIAL PRIMARY KEY,
    document_id VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    metadata JSONB,
    embedding vector(1536),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**索引说明：**
- `idx_knowledge_vectors_embedding`: IVFFlat 向量索引，加速相似度搜索
- `idx_knowledge_vectors_document_id`: 文档 ID 索引
- `idx_knowledge_vectors_created_at`: 时间索引
- `idx_knowledge_vectors_content_gin`: 全文检索索引（用于混合检索）

## 性能优化建议

1. **索引选择**
   - 中小规模数据（< 100万条）：使用 IVFFlat 索引
   - 大规模数据（> 100万条）：使用 HNSW 索引（更快但占用更多内存）

2. **向量维度**
   - 根据 embedding 模型选择合适的维度
   - OpenAI ada-002: 1536 维
   - 其他模型请参考对应文档

3. **批量操作**
   - 插入大量数据时使用 `batchInsertVectors()`
   - 建议每批次 100-1000 条记录

4. **查询优化**
   - 设置合理的 `topK` 值，避免返回过多结果
   - 使用 `scoreThreshold` 过滤低相似度结果
   - 混合检索时确保关键词相关性

## 常见问题

### Q1: 如何生成向量嵌入？

使用 embedding 模型将文本转换为向量，例如：

```java
// 使用 OpenAI Embedding
OpenAIEmbeddingModel embeddingModel = OpenAIEmbeddingModel.builder()
    .apiKey("your-api-key")
    .modelName("text-embedding-ada-002")
    .build();

float[] embedding = embeddingModel.embed(text).block().getEmbedding();
```

### Q2: 向量维度不匹配怎么办？

确保 `vectorDimension` 配置与 embedding 模型输出的维度一致：
- OpenAI ada-002: 1536
- BGE-large-zh: 1024
- text2vec-large-chinese: 768

修改配置：
```properties
agentscope.analyticdb.vector-dimension=1024
```

并重新创建表：
```sql
ALTER TABLE knowledge_vectors ALTER COLUMN embedding TYPE vector(1024);
```

### Q3: 如何提高检索速度？

1. 使用合适的索引类型（IVFFlat 或 HNSW）
2. 调整索引参数（lists、m、ef_construction）
3. 限制 topK 大小
4. 使用相似度阈值过滤
5. 定期维护索引（VACUUM ANALYZE）

### Q4: 支持哪些相似度计算方法？

当前实现使用余弦相似度（cosine similarity），通过 `<=>` 运算符计算向量距离。

AnalyticDB PostgreSQL 还支持：
- 欧氏距离：<-> 
- 内积：<#>

如需切换，修改 SQL 查询中的运算符即可。

## 依赖说明

项目已添加以下依赖：

```xml
<!-- PostgreSQL JDBC Driver -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>42.6.0</version>
</dependency>

<!-- Spring Boot Starter JDBC -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>
```

## 参考资料

- [AnalyticDB PostgreSQL 官方文档](https://help.aliyun.com/product/42790.html)
- [pgvector GitHub](https://github.com/pgvector/pgvector)
- [AgentScope 文档](https://agentscope.io/)

## 注意事项

⚠️ **重要提示：**

1. 当前实现基于标准 PostgreSQL + pgvector，如果 AgentScope 提供了原生的 AnalyticDB 集成模块，需要相应调整代码
2. 生产环境请配置连接池（如 HikariCP）
3. 敏感信息（密码等）建议使用环境变量或密钥管理服务
4. 定期备份数据库
5. 监控数据库性能和存储空间

## 后续改进方向

- [ ] 集成 AgentScope 原生 AnalyticDB RAG 模块（如果可用）
- [ ] 添加连接池配置
- [ ] 支持更多相似度算法
- [ ] 实现向量自动过期清理
- [ ] 添加检索结果缓存
- [ ] 支持多租户隔离
- [ ] 完善错误处理和重试机制
