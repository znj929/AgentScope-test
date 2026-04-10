# AgentScope 对话功能使用指南

## 📋 项目概述

这是一个基于 AgentScope 框架的智能对话系统，集成了：
- **阿里百炼 Qwen 模型**：提供强大的语言理解能力
- **AnalyticDB 向量数据库**：支持知识库检索
- **流式响应**：实时显示对话内容
- **会话管理**：支持多用户、多会话

## ✅ 项目检查状态

### 已完成的组件

1. ✅ **核心服务层**
   - `IChatService` - 聊天服务接口
   - `ChatServiceImpl` - 聊天服务实现（支持流式响应）

2. ✅ **Agent 管理**
   - `AgentManager` - Agent 创建和会话管理
   - 支持多用户、多会话隔离

3. ✅ **配置层**
   - `ChatModelConfig` - 阿里百炼模型配置
   - `ToolConfig` - 工具集配置（知识库、商品、文件操作）

4. ✅ **控制器**
   - `ChatController` - REST API 接口
     - `/api/chat/stream` - 完整参数的流式接口
     - `/api/chat/simple` - 简化接口

5. ✅ **工具集**
   - `KnowledgeSearchTool` - 知识库检索
   - `ProductTools` - 商品相关工具
   - `ReadFileTool/WriteFileTool` - 文件操作

6. ✅ **知识库集成**
   - `AnalyticDBVectorStore` - AnalyticDB 向量存储
   - `AliyunEmbeddingService` - 阿里云 Embedding 服务

## 🚀 快速开始

### 1. 环境要求

- Java 17+
- Maven 3.6+
- 网络连接（访问阿里百炼 API）

### 2. 配置检查

确保 `src/main/resources/application.properties` 中的配置正确：

```properties
# 阿里百炼配置（已配置）
agentscope.openai.api-key=sk-5df11b3db1fd42c99b93f87cf728b1f8
agentscope.openai.base-url=https://bailian.cn-beijing.aliyuncs.com
agentscope.openai.model-name=qwen-plus

# AnalyticDB 配置（可选，用于知识库检索）
agentscope.analyticdb.host=gp-2ze3od148j851c208o-master.gpdb.rds.aliyuncs.com
agentscope.analyticdb.port=5432
# ... 其他配置
```

### 3. 启动项目

```bash
# 编译项目
mvn clean compile

# 运行项目
mvn spring-boot:run
```

或者直接运行 `Main.java`

### 4. 测试对话功能

#### 方式一：使用 Web 界面（推荐）

1. 启动项目后，用浏览器打开 `chat-test.html` 文件
2. 在输入框中输入消息
3. 点击发送或按回车键
4. 查看流式响应效果

#### 方式二：使用 cURL 命令

**简化接口：**
```bash
curl -X POST http://localhost:8080/api/chat/simple \
  -H "Content-Type: application/json" \
  -d '"你好，请介绍一下你自己"'
```

**完整参数接口：**
```bash
curl -X POST "http://localhost:8080/api/chat/stream?userId=user001&sessionId=session123&datasetId=default" \
  -H "Content-Type: application/json" \
  -d '"请问有什么商品推荐？"'
```

#### 方式三：使用 Postman

1. 创建 POST 请求
2. URL: `http://localhost:8080/api/chat/simple`
3. Headers: `Content-Type: application/json`
4. Body (raw): `"你好"`
5. 发送请求

## 📡 API 接口说明

### 1. 简化聊天接口

**端点：** `POST /api/chat/simple`

**请求体：**
```json
"用户消息内容"
```

**响应：** 流式文本响应（SSE）

**示例：**
```bash
curl -X POST http://localhost:8080/api/chat/simple \
  -H "Content-Type: application/json" \
  -d '"你好"'
```

### 2. 完整参数聊天接口

**端点：** `POST /api/chat/stream`

**查询参数：**
- `userId` (必填) - 用户ID
- `sessionId` (必填) - 会话ID
- `datasetId` (可选) - 数据集ID，默认 "default"

**请求体：**
```json
"用户消息内容"
```

**响应：** 流式文本响应（SSE）

**示例：**
```bash
curl -X POST "http://localhost:8080/api/chat/stream?userId=user001&sessionId=session123" \
  -H "Content-Type: application/json" \
  -d '"推荐一些商品"'
```

## 🔧 功能特性

### 1. 流式响应
- 实时显示 AI 回复内容
- 更好的用户体验
- 减少等待时间感知

### 2. 会话管理
- 自动保存会话历史
- 支持上下文对话
- 会话持久化到本地文件系统

### 3. 知识库检索
- 基于 AnalyticDB 的向量搜索
- 支持相似度检索
- 可配置返回结果数量和阈值

### 4. 工具调用
Agent 可以自动调用以下工具：
- `knowledge_search` - 从知识库搜索信息
- `product_recommendation` - 推荐商品
- `read_file/write_file` - 文件读写操作

## 🐛 常见问题

### 1. 启动失败：端口被占用
**解决方案：** 修改 `application.properties` 中的 `server.port`

### 2. API 调用失败：认证错误
**解决方案：** 检查 `agentscope.openai.api-key` 是否正确

### 3. 知识库检索不可用
**解决方案：** 
- 检查 AnalyticDB 配置是否正确
- 确认数据库连接正常
- 验证 Embedding 服务配置

### 4. 流式响应不工作
**解决方案：**
- 确保客户端支持 SSE（Server-Sent Events）
- 检查响应头 `Content-Type: text/event-stream`

## 📝 代码结构

```
src/main/java/org/example/
├── Main.java                          # 应用入口
├── config/
│   ├── ChatModelConfig.java          # 模型配置
│   └── ToolConfig.java               # 工具配置
├── controller/
│   └── ChatController.java           # REST 控制器 ⭐新增
├── service/
│   ├── IChatService.java             # 服务接口
│   └── impl/
│       └── ChatServiceImpl.java      # 服务实现
├── manager/
│   └── AgentManager.java             # Agent 管理
├── tool/
│   ├── KnowledgeSearchTool.java      # 知识库工具
│   └── ProductTools.java             # 商品工具
├── knowledge/
│   ├── AnalyticDBConfig.java         # AnalyticDB 配置
│   ├── AnalyticDBVectorStore.java    # 向量存储
│   └── AliyunEmbeddingService.java   # Embedding 服务
└── hook/
    └── LoggingHook.java              # 日志钩子
```

## 🎯 下一步建议

1. **添加更多工具**：根据业务需求扩展工具集
2. **优化提示词**：调整 Agent 的系统提示词以获得更好的回答
3. **添加用户认证**：实现真实的用户身份验证
4. **数据库集成**：将会话数据存储到数据库而非文件系统
5. **监控和日志**：添加更详细的日志和性能监控
6. **错误处理**：增强异常处理和用户友好的错误提示

## 📞 技术支持

如有问题，请检查：
1. 控制台日志输出
2. 配置文件是否正确
3. 网络连接是否正常
4. API Key 是否有效

---

**祝使用愉快！** 🎉
