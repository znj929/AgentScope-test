package org.example.tool;

import cn.hutool.json.JSONUtil;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * 产品输出格式化工具
 * 将产品信息格式化为标准的 Markdown 输出格式
 */
@Slf4j
public class ProductOutputFormatter {
    
    static {
        log.info("🔧 ProductOutputFormatter 工具类已加载");
    }
    
    public ProductOutputFormatter() {
        log.info("🔧 ProductOutputFormatter 实例已创建");
    }

    /**
     * 格式化单个产品信息为标准 Markdown 格式
     * 
     * 此工具会将产品数据转换为统一的 Markdown 格式输出，确保包含型号、料号和产品总结。
     *
     * @param modelName   产品型号
     * @param partNumber  产品料号
     * @param summary     产品总结（结合用户问题和产品信息的简洁描述）
     * @return 格式化后的 Markdown 字符串
     */
    @Tool(name = "format_product_output", 
          description = "将产品信息格式化为标准 Markdown 格式输出。" +
                       "必须提供产品型号(model_name)、料号(part_number)和产品总结(summary)。" +
                       "输出格式示例：" +
                       "### 产品型号：IPC-HFW2431S-S\n" +
                       "**料号：** 1.0.01.12345\n\n" +
                       "**产品总结：**\n" +
                       "这是一款4MP分辨率的网络摄像机...")
    public String formatProductOutput(
            @ToolParam(name = "model_name", description = "产品型号") String modelName,
            @ToolParam(name = "part_number", description = "产品料号") String partNumber,
            @ToolParam(name = "summary", description = "产品总结，结合用户问题和产品关键信息") String summary) {
        
        log.info("格式化产品输出: model={}, partNumber={}", modelName, partNumber);
        
        try {
            if (modelName == null || modelName.isEmpty()) {
                return "❌ 错误：产品型号不能为空";
            }
            
            if (partNumber == null || partNumber.isEmpty()) {
                return "❌ 错误：产品料号不能为空";
            }
            
            if (summary == null || summary.isEmpty()) {
                summary = "暂无产品详细描述";
            }
            
            // 构建标准 Markdown 格式
            StringBuilder markdown = new StringBuilder();
            markdown.append("### 产品型号：").append(modelName).append("\n");
            markdown.append("**料号：** ").append(partNumber).append("\n\n");
            markdown.append("**产品总结：**\n");
            markdown.append(summary).append("\n\n");
            markdown.append("---");
            
            String result = markdown.toString();
            log.debug("格式化结果:\n{}", result);
            
            return result;
            
        } catch (Exception e) {
            log.error("格式化产品输出失败", e);
            return "❌ 格式化失败: " + e.getMessage();
        }
    }
    
    /**
     * 批量格式化多个产品信息
     * 
     * @param productsJson 产品列表的 JSON 数组字符串
     *                     格式：[{"model_name":"xxx","part_number":"xxx","summary":"xxx"}, ...]
     * @return 所有产品的 Markdown 格式输出，每个产品之间用分隔线分开
     */
    @Tool(name = "format_multiple_products", 
          description = "批量格式化多个产品信息为标准 Markdown 格式。" +
                       "输入为 JSON 数组，每个元素包含 model_name、part_number 和 summary 字段。" +
                       "输出为所有产品的 Markdown 格式，用分隔线分开。")
    public String formatMultipleProducts(
            @ToolParam(name = "products_json", 
                      description = "产品列表的 JSON 数组，例如：[{\"model_name\":\"IPC-XXX\",\"part_number\":\"1.0.01.XXX\",\"summary\":\"产品描述\"}]") 
            String productsJson) {
        
        log.info("批量格式化产品输出");
        
        try {
            // 解析 JSON 数组 - 使用安全的解析方式
            if (productsJson == null || productsJson.trim().isEmpty()) {
                return "⚠️ 未提供产品信息";
            }
            
            // 先验证 JSON 格式
            if (!productsJson.trim().startsWith("[")) {
                return "❌ 错误：输入必须是 JSON 数组格式";
            }
            
            cn.hutool.json.JSONArray jsonArray = new cn.hutool.json.JSONArray(productsJson);
            
            if (jsonArray.isEmpty()) {
                return "⚠️ 未提供产品信息";
            }
            
            StringBuilder result = new StringBuilder();
            
            for (int i = 0; i < jsonArray.size(); i++) {
                cn.hutool.json.JSONObject product = jsonArray.getJSONObject(i);
                
                String modelName = product.getStr("model_name");
                String partNumber = product.getStr("part_number");
                String summary = product.getStr("summary");
                
                // 格式化单个产品
                String formatted = formatProductOutput(modelName, partNumber, summary);
                result.append(formatted);
                
                // 如果不是最后一个产品，添加额外换行
                if (i < jsonArray.size() - 1) {
                    result.append("\n\n");
                }
            }
            
            log.info("成功格式化 {} 个产品", jsonArray.size());
            return result.toString();
            
        } catch (Exception e) {
            log.error("批量格式化产品失败", e);
            return "❌ 批量格式化失败: " + e.getMessage();
        }
    }
}
