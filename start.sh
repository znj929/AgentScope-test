#!/bin/bash

# AgentScope 快速启动脚本

echo "=========================================="
echo "AgentScope 智能对话系统 - 快速启动"
echo "=========================================="
echo ""

# 检查 Java 版本
echo "1. 检查 Java 环境..."
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -n 1)
    echo "✅ Java 已安装: $JAVA_VERSION"
else
    echo "❌ 未检测到 Java，请先安装 Java 17+"
    exit 1
fi

# 检查 Maven
echo ""
echo "2. 检查 Maven..."
if command -v mvn &> /dev/null; then
    MAVEN_VERSION=$(mvn -version 2>&1 | head -n 1)
    echo "✅ Maven 已安装: $MAVEN_VERSION"
else
    echo "❌ 未检测到 Maven，请先安装 Maven 3.6+"
    exit 1
fi

# 清理并编译
echo ""
echo "3. 清理并编译项目..."
mvn clean compile -q

if [ $? -eq 0 ]; then
    echo "✅ 编译成功"
else
    echo "❌ 编译失败，请检查错误信息"
    exit 1
fi

# 启动应用
echo ""
echo "=========================================="
echo "启动 AgentScope 应用..."
echo "=========================================="
echo ""
echo "💡 提示："
echo "  - 应用将在 http://localhost:8080 启动"
echo "  - 按 Ctrl+C 停止应用"
echo "  - 启动后可以用以下方式测试："
echo ""
echo "    1. Web界面: 用浏览器打开 chat-test.html"
echo "    2. 命令行: ./test-chat.sh"
echo "    3. API调用: curl -X POST http://localhost:8080/api/chat/simple -H 'Content-Type: application/json' -d '\"你好\"'"
echo ""
echo "=========================================="
echo ""

# 启动 Spring Boot 应用
mvn spring-boot:run
