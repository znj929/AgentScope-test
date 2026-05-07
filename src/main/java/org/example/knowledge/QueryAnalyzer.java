package org.example.knowledge;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * 查询类型分析器
 * 根据用户查询内容智能判断检索权重配置
 */
@Slf4j
public class QueryAnalyzer {
    
    // 产品编号正则表达式模式
    private static final Pattern PRODUCT_CODE_PATTERN = Pattern.compile(
        ".*\\d+\\.\\d+\\.\\d+.*|.*[A-Z]{2,}-[A-Z0-9]+.*"
    );
    
    // 模糊查询关键词
    private static final String[] VAGUE_KEYWORDS = {
        "推荐", "类似", "差不多", "适合", "比较好", "哪款", "哪种", 
        "有什么", "介绍一下", "了解一下"
    };
    
    // 技术参数关键词
    private static final String[] TECHNICAL_KEYWORDS = {
        "MP", "mm", "IP67", "IP66", "H.265", "H.264", "POE", 
        "夜视", "红外", "变焦", "广角", "分辨率", "焦距"
    };
    
    // 产品类型关键词
    private static final String[] PRODUCT_TYPE_KEYWORDS = {
        "摄像机", "摄像头", "NVR", "DVR", "录像机", "门禁", 
        "报警器", "探测器", "球机", "枪机", "筒机"
    };
    
    /**
     * 分析查询并返回最优权重配置
     *
     * @param query    用户查询语句
     * @param keywords 额外关键词（可选）
     * @return 权重配置
     */
    public static WeightConfig analyzeQuery(String query, String keywords) {
        log.info("分析查询类型: query={}, keywords={}", query, keywords);
        
        // 1. 检查是否包含产品编号（精确匹配）
        if (containsProductCode(query)) {
            log.info("检测到产品编号，使用精确匹配权重");
            return WeightConfig.exactMatchConfig();
        }
        
        // 2. 检查是否为模糊查询
        if (isVagueQuery(query)) {
            log.info("检测到模糊查询，使用语义搜索权重");
            return WeightConfig.semanticSearchConfig();
        }
        
        /*// 3. 检查是否包含多个技术参数
        int technicalKeywordCount = countTechnicalKeywords(query, keywords);
        if (technicalKeywordCount >= 3) {
            log.info("检测到多个技术参数({}个)，使用产品推荐权重", technicalKeywordCount);
            return WeightConfig.productRecommendConfig();
        }
        
        // 4. 检查是否主要为关键词搜索
        if (isKeywordFocused(query, keywords)) {
            log.info("检测到关键词聚焦查询，使用关键词搜索权重");
            return WeightConfig.keywordSearchConfig();
        }*/
        
        // 5. 默认使用平衡配置
        log.info("使用默认平衡权重配置");
        return WeightConfig.defaultConfig();
    }
    
    /**
     * 检测是否包含产品编号特征
     */
    private static boolean containsProductCode(String query) {
        if (query == null || query.isEmpty()) {
            return false;
        }
        return PRODUCT_CODE_PATTERN.matcher(query).matches();
    }
    
    /**
     * 检测是否为模糊查询
     */
    private static boolean isVagueQuery(String query) {
        if (query == null || query.isEmpty()) {
            return false;
        }
        
        return Arrays.stream(VAGUE_KEYWORDS)
            .anyMatch(keyword -> query.contains(keyword));
    }
    
    /**
     * 统计技术参数关键词数量
     */
    private static int countTechnicalKeywords(String query, String keywords) {
        int count = 0;
        String combinedText = (query != null ? query : "") + " " + 
                             (keywords != null ? keywords : "");
        combinedText = combinedText.toUpperCase();
        
        for (String techKeyword : TECHNICAL_KEYWORDS) {
            if (combinedText.contains(techKeyword.toUpperCase())) {
                count++;
            }
        }
        
        return count;
    }
    
    /**
     * 检测是否为关键词聚焦查询
     */
    private static boolean isKeywordFocused(String query, String keywords) {
        // 如果提供了额外的关键词，且关键词数量较多
        if (keywords != null && !keywords.isEmpty()) {
            String[] keywordArray = keywords.split(",");
            if (keywordArray.length >= 2) {
                return true;
            }
        }
        
        // 检查查询中是否包含产品类型关键词
        if (query != null && !query.isEmpty()) {
            return Arrays.stream(PRODUCT_TYPE_KEYWORDS)
                .anyMatch(keyword -> query.contains(keyword));
        }
        
        return false;
    }
}
