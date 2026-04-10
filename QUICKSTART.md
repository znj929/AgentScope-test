# AnalyticDB PostgreSQL 向量库 - 快速开始

## 🚀 5 分钟快速上手

### 前置要求

- ✅ JDK 17+
- ✅ Maven 3.6+
- ✅ AnalyticDB PostgreSQL 实例（阿里云）

---

## 步骤 1: 配置数据库连接

编辑 `src/main/resources/application.properties`：

```properties
# 替换为你的 AnalyticDB 连接信息
agentscope.analyticdb.host=your-host.pg.rds.aliyuncs.com
agentscope.analyticdb.port=5432
agentscope.analyticdb.database=your_database
agentscope.analyticdb.username=your_username
agentscope.analyticdb.password=your_password
```

---

## 步骤 2: 初始化数据库表

连接到你的 AnalyticDB 实例并执行初始化脚本：

```bash
# 使用 psql 命令行工具
psql -h your-host.pg.rds.aliyuncs.com \
     -p 5432 \
     -U your_username \
     -d your_database \
     -f src/main/resources/analyticdb_init.sql
```

或者使用图形化工具（pgAdmin、DBeaver 等）执行 `analyticdb_init.sql`。

---

## 步骤 3: 在代码中使用

### 方式一：直接使用向量存储

```java
import org.example.knowledge.AnalyticDBVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.*;

@Service
public class MyService {
    
    @Autowired
    private AnalyticDBVectorStore vectorStore;
    
    public void addDocument(String docId, String content) {
        // 1. 生成向量（使用你的 embedding 模型）
        float[] embedding = generateEmbedding(content);
        
        // 2. 准备元数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "my_document");
        metadata.put("created", System.currentTimeMillis());
        
        // 3. 插入向量
        vectorStore.insertVector(docId, content, embedding, metadata);
    }
    
    public List<String> searchDocuments(String query) {
        // 1. 将查询转换为向量
        float[] queryEmbedding = generateEmbedding(query);
        
        // 2. 执行相似度搜索
        List<AnalyticDBVectorStore.SearchResult> results = 
            vectorStore.similaritySearch(queryEmbedding, 5);
        
        // 3. 提取内容
        return results.stream()
            .map(AnalyticDBVectorStore.SearchResult::getContent)
            .toList();
    }
    
    private float[] generateEmbedding(String text) {
        // TODO: 调用 embedding API 生成向量
        // 例如使用 OpenAI、BGE 等模型
        return new float[1536];
    }
}
```

### 方式二：与 ReActAgent 集成（RAG）

```java
import io.agentscope.core.ReActAgent;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class AgentService {
    
    @Autowired
    private ReActAgent analyticDBAgent;
    
    public String askQuestion(String question) {
        // Agent 会自动从 AnalyticDB 检索相关知识
        Msg response = analyticDBAgent.call(
            Msg.builder().content(question).build()
        ).block();
        
        return response.getContent().toString();
    }
}
```

---

## 步骤 4: 运行示例代码

```java
import org.example.knowledge.AnalyticDBUsageExample;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class MyRunner implements CommandLineRunner {
    
    @Autowired
    private AnalyticDBUsageExample example;
    
    @Override
    public void run(String... args) {
        // 运行完整工作流程示例
        example.exampleCompleteWorkflow();
        
        // 或运行知识库问答示例
        example.exampleKnowledgeBaseQA();
    }
}
```

---

## 📖 常用操作示例

### 批量插入文档

```java
List<AnalyticDBVectorStore.VectorRecord> records = new ArrayList<>();

for (Document doc : documents) {
    AnalyticDBVectorStore.VectorRecord record = new AnalyticDBVectorStore.VectorRecord();
    record.setDocumentId(doc.getId());
    record.setContent(doc.getText());
    record.setEmbedding(embeddingModel.embed(doc.getText()));
    record.setMetadata(Map.of("category", doc.getCategory()));
    records.add(record);
}

vectorStore.batchInsertVectors(records);
```

### 带阈值的搜索

```java
// 只返回相似度 >= 0.7 的结果
List<SearchResult> results = vectorStore.similaritySearch(
    queryEmbedding, 
    10,     // topK
    0.7     // threshold
);
```

### 混合检索

```java
// 结合向量和关键词检索
List<SearchResult> results = vectorStore.hybridSearch(
    queryEmbedding,
    "关键词",
    5
);
```

### 更新文档

```java
vectorStore.updateVector(
    "doc_001",
    "新的内容",
    newEmbedding,
    newMetadata
);
```

### 删除文档

```java
vectorStore.deleteVector("doc_001");
```

---

## 🔧 常见问题

### Q: 如何生成向量嵌入？

使用 embedding 模型，例如：

```java
// OpenAI Embedding
OpenAIEmbeddingModel model = OpenAIEmbeddingModel.builder()
    .apiKey("sk-xxx")
    .modelName("text-embedding-ada-002")
    .build();

float[] embedding = model.embed(text).block().getEmbedding();
```

### Q: 向量维度不匹配怎么办？

修改配置文件中的维度设置：

```properties
# OpenAI ada-002: 1536
# BGE-large-zh: 1024
# text2vec-large-chinese: 768
agentscope.analyticdb.vector-dimension=1536
```

然后重新创建表：

```sql
ALTER TABLE knowledge_vectors 
ALTER COLUMN embedding TYPE vector(1536);
```

### Q: 如何提高检索速度？

1. 使用合适的索引类型
2. 限制 topK 大小
3. 设置相似度阈值
4. 定期维护索引：`VACUUM ANALYZE knowledge_vectors;`

---

## 📚 更多信息

- 📖 [完整使用文档](ANALYTICDB_USAGE.md)
- 📋 [实现总结](IMPLEMENTATION_SUMMARY.md)
- 💾 [数据库初始化脚本](src/main/resources/analyticdb_init.sql)

---

## ⚠️ 注意事项

1. **安全性**: 不要将密码提交到版本控制系统
2. **向量维度**: 确保与 embedding 模型输出一致
3. **连接池**: 生产环境建议配置 HikariCP
4. **监控**: 监控数据库性能和存储空间

---

**祝你使用愉快！** 🎉
