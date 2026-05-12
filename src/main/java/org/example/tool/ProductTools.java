package org.example.tool;

import cn.hutool.core.map.MapUtil;
import cn.hutool.json.JSONUtil;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 产品相关工具类
 * 提供产品过滤条件解析等功能
 * 使用 LLM 模型智能解析用户意图
 */
@Slf4j
@Component
public class ProductTools {
    
    @Value("${agentscope.openai.api-key}")
    private String apiKey;
    
    @Value("${agentscope.openai.base-url}")
    private String baseUrl;
    
    @Value("${agentscope.openai.model-name}")
    private String modelName;
    
    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    
    // 海外产品线枚举值列表（用于提示词）
    private static final String[] OVERSEAS_PRODUCT_LINES = {
        "Thermal Camera", "Access Control", "HDD", "Transmission",
        "Dahua Memory", "Intelligent Traffic", "XVR", "Display & Control",
        "Alarm", "Interactive Whiteboard", "IPC", "Automobile",
        "Fire Alarm", "Mobile Portable Terminal", "Video Door Phone",
        "NVR", "Security Inspection", "PTZ", "HAC", "Software",
        "Intelligent Computing", "Intelligent EV Charging", "Accessory", "Wireless"
    };
    
    /*@Tool(name = "product_recommendation", description = "用于商品推荐")
    public String productRecommendation(
            @ToolParam(name = "category", description = "商品类别") String category,
            @ToolParam(name = "keyword", description = "搜索关键词") String keyword
            ) {
        // 调用商品服务，返回 JSON 格式商品数据
        Map<String, Object> product = MapUtil.builder(new HashMap<String, Object>())
                .put("product_name", "智能牙刷")
                .put("product_price", 23.78)
                .put("desc", "商品简介信息")
                .build();
        return JSONUtil.toJsonStr(product);
    }*/
    
    /**
     * 从用户问题中智能解析过滤条件
     * 
     * 此工具会分析用户问题，识别：
     * - 产品线 (overseas_product_line)
     * - 产品系列 (overseas_series)
     * - 产品编号/料号 (part_num)
     * - 产品型号 (external_model)
     * - 技术规格关键词 (specifications)
     *
     * @param query 用户查询问题
     * @return 解析出的过滤条件和关键词，JSON 格式
     */
    @Tool(name = "parse_filter_conditions", 
          description = "从用户问题中智能解析过滤条件，识别产品线、产品系列、产品编号、型号和技术规格。" +
                       "使用 AI 模型理解用户意图，准确提取产品信息。" +
                       "支持的字段：overseas_product_line, overseas_series, part_num, external_model, specifications。" +
                       "例如：'推荐一款IPC-HFW1230摄像机，要2MP和IP67防护' → " +
                       "{\"filters\": {\"overseas_product_line\": \"IPC\", \"external_model\": \"IPC-HFW1230\"}, \"keywords\": [\"2MP\", \"IP67\"]}" +
                       "例如：'查找料号1.2.3.4的产品' → {\"filters\": {\"part_num\": \"1.2.3.4\"}}" +
                       "例如：'2 Series的NVR录像机' → {\"filters\": {\"overseas_product_line\": \"NVR\", \"overseas_series\": \"2 Series\"}}")
    public String parseFilterConditions(
            @ToolParam(name = "query", description = "用户的查询问题") String query) {
        
        log.info("使用 LLM 智能解析过滤条件: query={}", query);
        
        try {
            // 构建系统提示词
            String systemPrompt = buildSystemPrompt();
            
            // 构建用户消息
            String userMessage = String.format("用户问题：%s\n\n请按照上述格式输出 JSON 结果。", query);
            
            // 调用阿里百炼 API
            String responseText = callLLMAPI(systemPrompt, userMessage);
            log.info("LLM 原始响应: {}", responseText);
            
            // 尝试解析 JSON
            try {
                // 提取 JSON 部分（可能包含在 markdown 代码块中）
                String jsonStr = extractJson(responseText);
                Map<String, Object> result = JSONUtil.toBean(jsonStr, Map.class);
                
                // 验证结果格式
                if (result.containsKey("filters")) {
                    log.info("LLM 解析成功: {}", jsonStr);
                    return jsonStr;
                } else {
                    log.warn("LLM 响应格式不正确，使用降级方案");
                    return parseWithFallback(query);
                }
            } catch (Exception e) {
                log.error("解析 LLM 响应失败，使用降级方案", e);
                return parseWithFallback(query);
            }
            
        } catch (Exception e) {
            log.error("LLM 调用失败，使用降级方案", e);
            return parseWithFallback(query);
        }
    }
    
    /**
     * 调用阿里百炼 LLM API（直接使用 HTTP 调用）
     */
    private String callLLMAPI(String systemPrompt, String userMessage) throws IOException {
        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelName);
        
        // 构建 messages
        Map<String, String>[] messages = new Map[2];
        messages[0] = Map.of("role", "system", "content", systemPrompt);
        messages[1] = Map.of("role", "user", "content", userMessage);
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.1); // 低温度，保证输出稳定
        
        String jsonBody = JSONUtil.toJsonStr(requestBody);
        
        // 创建 HTTP 请求
        RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();
        
        // 执行请求
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("API 调用失败: " + response.code() + " " + response.message());
            }
            
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new IOException("响应体为空");
            }
            
            String responseBodyStr = responseBody.string();
            
            // 解析响应，提取 content
            Map<String, Object> responseMap = JSONUtil.toBean(responseBodyStr, Map.class);
            
            // 获取 choices 数组（Hutool 返回的是 JSONArray）
            cn.hutool.json.JSONArray choices = (cn.hutool.json.JSONArray) responseMap.get("choices");
            if (choices != null && !choices.isEmpty()) {
                // 获取第一个 choice
                Map<String, Object> firstChoice = choices.getJSONObject(0);
                Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
                return (String) message.get("content");
            }
            
            throw new IOException("无法解析 API 响应");
        }
    }
    
    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt() {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个专业的产品过滤条件解析助手。你的任务是从用户的问题中提取产品过滤条件。\n\n");
        
        prompt.append("## 需要提取的字段：\n");
        prompt.append("1. overseas_product_line: 产品线类型（从下方列表选择）\n");
        prompt.append("2. overseas_series: 产品系列（仅支持数字系列格式，如 '1 Series', '2 Series' 等）\n");
        prompt.append("3. part_num: 产品编号/料号（格式如：1.2.3.4 或 1.2.3）\n");
        prompt.append("4. external_model: 产品外部型号（格式如：IPC-HFW1230, NVR4108 等字母数字组合）\n");
        prompt.append("5. specifications: 技术规格关键词数组（如分辨率、防护等级、焦距等，如 [\"2MP\", \"IP67\", \"2.8mm\"]）\n\n");
        
        prompt.append("## 可选的产品线列表：\n");
        for (String line : OVERSEAS_PRODUCT_LINES) {
            prompt.append("- ").append(line).append("\n");
        }
        
        prompt.append("\n## 产品系列说明：\n");
        prompt.append("- 只识别数字系列格式，如：1 Series, 2 Series, 3 Series 等\n");
        prompt.append("- 用户可能说 '1系列'、'2 series'、'第3系列' 等，都需要转换为 'X Series' 格式\n\n");
        
        prompt.append("## 产品编号识别规则：\n");
        prompt.append("- 格式：数字.数字.数字 或 数字.数字.数字.数字（如 1.2.3, 1.2.3.4）\n");
        prompt.append("- 示例：'料号1.2.3.4' → part_num: \"1.2.3.4\"\n\n");
        
        prompt.append("## 产品型号识别规则：\n");
        prompt.append("- 格式：字母+连字符+字母数字组合（如 IPC-HFW1230, NVR4108-HDS2）\n");
        prompt.append("- 示例：'IPC-HFW1230摄像机' → external_model: \"IPC-HFW1230\"\n\n");
        
        prompt.append("## 技术规格识别规则：\n");
        prompt.append("- 分辨率：2MP, 4MP, 5MP, 8MP, 4K 等\n");
        prompt.append("- 防护等级：IP67, IP66, IP54 等\n");
        prompt.append("- 焦距：2.8mm, 3.6mm, 6mm 等\n");
        prompt.append("- 编码格式：H.265, H.264 等\n");
        prompt.append("- 其他：POE, 夜视, 红外, 变焦, 广角等\n\n");
        
        prompt.append("## 输出格式：\n");
        prompt.append("必须输出严格的 JSON 格式，示例：\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"filters\": {\n");
        prompt.append("    \"overseas_product_line\": \"IPC\",\n");
        prompt.append("    \"external_model\": \"IPC-HFW1230\"\n");
        prompt.append("  },\n");
        prompt.append("  \"keywords\": [\"2MP\", \"IP67\", \"2.8mm\"]\n");
        prompt.append("}\n");
        prompt.append("```\n\n");
        
        prompt.append("## 注意事项：\n");
        prompt.append("1. filters 中的字段只有在明确识别到时才包含，不要包含空值\n");
        prompt.append("2. keywords 数组包含所有技术规格关键词，用逗号分隔\n");
        prompt.append("3. 产品线必须从上述列表中选择最匹配的\n");
        prompt.append("4. 系列必须是 'X Series' 格式（X 为数字）\n");
        prompt.append("5. 只输出 JSON，不要有其他解释文字\n");
        prompt.append("6. 如果用户没有提到任何过滤条件，返回 {\"filters\": {}, \"keywords\": []}\n");
        
        return prompt.toString();
    }
    
    /**
     * 从响应中提取 JSON 字符串
     */
    private String extractJson(String response) {
        // 尝试提取 markdown 代码块中的 JSON
        int startIdx = response.indexOf("```json");
        if (startIdx != -1) {
            startIdx += 7; // 跳过 ```json
            int endIdx = response.indexOf("```", startIdx);
            if (endIdx != -1) {
                return response.substring(startIdx, endIdx).trim();
            }
        }
        
        // 尝试直接查找 JSON 对象
        startIdx = response.indexOf("{");
        int endIdx = response.lastIndexOf("}");
        if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
            return response.substring(startIdx, endIdx + 1);
        }
        
        // 返回原始响应
        return response.trim();
    }
    
    /**
     * 降级方案：使用简单规则匹配（当 LLM 不可用时）
     */
    private String parseWithFallback(String query) {
        log.info("使用降级方案解析过滤条件");
        
        try {
            Map<String, Object> filters = new HashMap<>();
            List<String> keywords = new ArrayList<>();
            String lowerQuery = query.toLowerCase();
            
            // 简单的产品线匹配
            String matchedProductLine = matchProductLineSimple(lowerQuery);
            if (matchedProductLine != null) {
                filters.put("overseas_product_line", matchedProductLine);
            }
            
            // 简单的系列匹配
            String matchedSeries = matchProductSeriesSimple(lowerQuery);
            if (matchedSeries != null) {
                filters.put("overseas_series", matchedSeries);
            }
            
            // 提取产品编号（料号）
            String productCode = extractProductCodeSimple(query);
            if (productCode != null) {
                filters.put("part_num", productCode);
            }
            
            // 提取产品型号
            String productModel = extractProductModelSimple(query);
            if (productModel != null) {
                filters.put("external_model", productModel);
            }
            
            // 提取技术规格关键词
            keywords.addAll(extractSpecificationsSimple(query));
            
            Map<String, Object> result = new HashMap<>();
            result.put("filters", filters);
            result.put("keywords", keywords);
            
            if (filters.isEmpty() && keywords.isEmpty()) {
                return "{\"filters\": {}, \"keywords\": [], \"message\": \"未识别到明确的过滤条件\"}";
            }
            
            return JSONUtil.toJsonStr(result);
        } catch (Exception e) {
            log.error("降级方案解析失败", e);
            return "{\"error\": \"解析失败: " + e.getMessage() + "\"}";
        }
    }
    
    /**
     * 简单的产品线匹配（降级方案）
     */
    private String matchProductLineSimple(String lowerQuery) {
        // 简化的关键词匹配
        if (lowerQuery.contains("ipc") || lowerQuery.contains("摄像机") || lowerQuery.contains("摄像头")) {
            return "IPC";
        }
        if (lowerQuery.contains("nvr") || lowerQuery.contains("录像机")) {
            return "NVR";
        }
        if (lowerQuery.contains("xvr")) {
            return "XVR";
        }
        if (lowerQuery.contains("ptz") || lowerQuery.contains("云台")) {
            return "PTZ";
        }
        if (lowerQuery.contains("门禁") || lowerQuery.contains("access control")) {
            return "Access Control";
        }
        return null;
    }
    
    /**
     * 简单的系列匹配（降级方案）
     */
    private String matchProductSeriesSimple(String lowerQuery) {
        // 使用正则匹配数字系列
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("([0-9]+)\\s*(?:series|系列)");
        java.util.regex.Matcher matcher = pattern.matcher(lowerQuery);
        
        if (matcher.find()) {
            String seriesNumber = matcher.group(1);
            return seriesNumber + " Series";
        }
        
        return null;
    }
    
    /**
     * 简单的产品编号提取（降级方案）
     */
    private String extractProductCodeSimple(String query) {
        // 匹配类似 "1.2.3.4" 或 "1.2.3" 的格式
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\b(\\d+\\.\\d+\\.\\d+(?:\\.\\d+)?)\\b");
        java.util.regex.Matcher matcher = pattern.matcher(query);
        
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return null;
    }
    
    /**
     * 简单的产品型号提取（降级方案）
     */
    private String extractProductModelSimple(String query) {
        // 匹配类似 "IPC-HFW1230"、"NVR4108-HDS2" 等型号
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\b([A-Z]{2,}-?[A-Z0-9]{3,}(?:-[A-Z0-9]+)?)\\b");
        java.util.regex.Matcher matcher = pattern.matcher(query);
        
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return null;
    }
    
    /**
     * 简单的技术规格提取（降级方案）
     */
    private List<String> extractSpecificationsSimple(String query) {
        List<String> specs = new ArrayList<>();
        
        // 分辨率
        java.util.regex.Pattern mpPattern = java.util.regex.Pattern.compile("\\b(\\d+\\.?\\d*MP)\\b");
        java.util.regex.Matcher mpMatcher = mpPattern.matcher(query);
        while (mpMatcher.find()) {
            specs.add(mpMatcher.group(1));
        }
        
        // 防护等级
        java.util.regex.Pattern ipPattern = java.util.regex.Pattern.compile("\\b(IP\\d{2})\\b");
        java.util.regex.Matcher ipMatcher = ipPattern.matcher(query);
        while (ipMatcher.find()) {
            specs.add(ipMatcher.group(1));
        }
        
        // 焦距
        java.util.regex.Pattern mmPattern = java.util.regex.Pattern.compile("\\b(\\d+\\.?\\d*mm)\\b");
        java.util.regex.Matcher mmMatcher = mmPattern.matcher(query);
        while (mmMatcher.find()) {
            specs.add(mmMatcher.group(1));
        }
        
        // 编码格式
        if (query.toUpperCase().contains("H.265")) {
            specs.add("H.265");
        }
        if (query.toUpperCase().contains("H.264")) {
            specs.add("H.264");
        }
        
        // POE
        if (query.toUpperCase().contains("POE") || query.toUpperCase().contains("POE+")) {
            specs.add("POE");
        }
        
        return specs;
    }

}
