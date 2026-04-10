package org.example.tool;

import cn.hutool.core.map.MapUtil;
import cn.hutool.json.JSONUtil;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.util.HashMap;
import java.util.Map;

public class ProductTools {
    @Tool(name = "product_recommendation", description = "用于商品推荐")
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
    }
}
