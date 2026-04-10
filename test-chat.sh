#!/bin/bash

# AgentScope 对话功能测试脚本

echo "=========================================="
echo "AgentScope 对话功能测试"
echo "=========================================="
echo ""

# 检查服务是否运行
echo "1. 检查服务是否启动..."
if curl -s http://localhost:8080/ > /dev/null 2>&1; then
    echo "✅ 服务正在运行"
else
    echo "❌ 服务未启动，请先运行: mvn spring-boot:run"
    exit 1
fi

echo ""
echo "2. 测试简化聊天接口..."
echo "----------------------------------------"
echo "发送消息: 你好，请介绍一下你自己"
echo "----------------------------------------"
curl -X POST http://localhost:8080/api/chat/simple \
  -H "Content-Type: application/json" \
  -d '"你好，请介绍一下你自己"' \
  --no-buffer

echo ""
echo ""
echo "=========================================="
echo "3. 测试完整参数接口..."
echo "----------------------------------------"
echo "发送消息: 推荐一些商品"
echo "----------------------------------------"
curl -X POST "http://localhost:8080/api/chat/stream?userId=test_user&sessionId=test_session_001&datasetId=default" \
  -H "Content-Type: application/json" \
  -d '"推荐一些商品"' \
  --no-buffer

echo ""
echo ""
echo "=========================================="
echo "4. 测试知识库检索..."
echo "----------------------------------------"
echo "发送消息: 请搜索一下产品文档"
echo "----------------------------------------"
curl -X POST "http://localhost:8080/api/chat/stream?userId=test_user&sessionId=test_session_002&datasetId=products" \
  -H "Content-Type: application/json" \
  -d '"请帮我查询一下产品的详细信息"' \
  --no-buffer

echo ""
echo ""
echo "=========================================="
echo "测试完成！"
echo "=========================================="
echo ""
echo "💡 提示："
echo "  - 使用浏览器打开 chat-test.html 可以获得更好的体验"
echo "  - 查看 CHAT_USAGE.md 了解更多使用方法"
echo ""
