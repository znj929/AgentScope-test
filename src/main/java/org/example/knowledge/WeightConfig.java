package org.example.knowledge;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 检索权重配置
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WeightConfig {
    /**
     * 向量相似度权重 (0-1)
     */
    private double vectorWeight;
    
    /**
     * 文本检索权重 (0-1)
     */
    private double textWeight;
    
    /**
     * 查询类型描述
     */
    private String queryType;
    
    /**
     * 默认平衡配置
     */
    public static WeightConfig defaultConfig() {
        return new WeightConfig(0.6, 0.4, "BALANCED");
    }
    
    /**
     * 语义搜索配置（侧重向量）
     */
    public static WeightConfig semanticSearchConfig() {
        return new WeightConfig(0.7, 0.3, "SEMANTIC_SEARCH");
    }
    
    /**
     * 关键词搜索配置（侧重文本）
     */
    public static WeightConfig keywordSearchConfig() {
        return new WeightConfig(0.3, 0.7, "KEYWORD_SEARCH");
    }
    
    /**
     * 产品推荐配置（平衡但略偏向文本）
     */
    public static WeightConfig productRecommendConfig() {
        return new WeightConfig(0.45, 0.55, "PRODUCT_RECOMMEND");
    }
    
    /**
     * 精确匹配配置（高度侧重文本）
     */
    public static WeightConfig exactMatchConfig() {
        return new WeightConfig(0.2, 0.8, "EXACT_MATCH");
    }
}
