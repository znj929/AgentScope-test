package org.example.knowledge;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 查询分析器测试类
 */
@Slf4j
public class QueryAnalyzerTest {
    
    @Test
    public void testProductCodeDetection() {
        log.info("测试产品编号检测");
        
        // 测试包含产品编号的查询
        WeightConfig config1 = QueryAnalyzer.analyzeQuery("查找料号1.0.01.12345的产品", null);
        assertEquals("EXACT_MATCH", config1.getQueryType());
        assertEquals(0.2, config1.getVectorWeight(), 0.01);
        assertEquals(0.8, config1.getTextWeight(), 0.01);
        log.info("产品编号查询权重: {}", config1);
        
        // 测试包含型号的查询
        WeightConfig config2 = QueryAnalyzer.analyzeQuery("IPC-HFW2431S-S的详细信息", null);
        assertEquals("EXACT_MATCH", config2.getQueryType());
        log.info("型号查询权重: {}", config2);
    }
    
    @Test
    public void testVagueQueryDetection() {
        log.info("测试模糊查询检测");
        
        // 测试推荐类查询
        WeightConfig config1 = QueryAnalyzer.analyzeQuery("推荐一款好用的摄像机", null);
        assertEquals("SEMANTIC_SEARCH", config1.getQueryType());
        assertEquals(0.7, config1.getVectorWeight(), 0.01);
        assertEquals(0.3, config1.getTextWeight(), 0.01);
        log.info("推荐查询权重: {}", config1);
        
        // 测试介绍类查询
        WeightConfig config2 = QueryAnalyzer.analyzeQuery("介绍一下你们的产品", null);
        assertEquals("SEMANTIC_SEARCH", config2.getQueryType());
        log.info("介绍查询权重: {}", config2);
    }
    
    @Test
    public void testTechnicalKeywordDetection() {
        log.info("测试技术参数关键词检测");
        
        // 测试包含多个技术参数的查询
        WeightConfig config1 = QueryAnalyzer.analyzeQuery(
            "找一款2MP像素、2.8mm焦距、IP67防护等级的摄像机", 
            "2MP,2.8mm,IP67"
        );
        assertEquals("PRODUCT_RECOMMEND", config1.getQueryType());
        assertEquals(0.45, config1.getVectorWeight(), 0.01);
        assertEquals(0.55, config1.getTextWeight(), 0.01);
        log.info("多参数查询权重: {}", config1);
    }
    
    @Test
    public void testKeywordFocusedQuery() {
        log.info("测试关键词聚焦查询");
        
        // 测试包含产品类型的查询
        WeightConfig config1 = QueryAnalyzer.analyzeQuery("找一款NVR录像机", null);
        assertEquals("KEYWORD_SEARCH", config1.getQueryType());
        assertEquals(0.3, config1.getVectorWeight(), 0.01);
        assertEquals(0.7, config1.getTextWeight(), 0.01);
        log.info("产品类型查询权重: {}", config1);
        
        // 测试带多个关键词的查询
        WeightConfig config2 = QueryAnalyzer.analyzeQuery("搜索产品", "摄像机,夜视,红外");
        assertEquals("KEYWORD_SEARCH", config2.getQueryType());
        log.info("多关键词查询权重: {}", config2);
    }
    
    @Test
    public void testDefaultConfig() {
        log.info("测试默认配置");
        
        // 测试普通查询
        WeightConfig config = QueryAnalyzer.analyzeQuery("产品查询", null);
        assertEquals("BALANCED", config.getQueryType());
        assertEquals(0.6, config.getVectorWeight(), 0.01);
        assertEquals(0.4, config.getTextWeight(), 0.01);
        log.info("默认查询权重: {}", config);
    }
    
    @Test
    public void testWeightConfigPresets() {
        log.info("测试预定义权重配置");
        
        WeightConfig semantic = WeightConfig.semanticSearchConfig();
        assertEquals(0.7, semantic.getVectorWeight(), 0.01);
        assertEquals(0.3, semantic.getTextWeight(), 0.01);
        log.info("语义搜索配置: {}", semantic);
        
        WeightConfig keyword = WeightConfig.keywordSearchConfig();
        assertEquals(0.3, keyword.getVectorWeight(), 0.01);
        assertEquals(0.7, keyword.getTextWeight(), 0.01);
        log.info("关键词搜索配置: {}", keyword);
        
        WeightConfig exact = WeightConfig.exactMatchConfig();
        assertEquals(0.2, exact.getVectorWeight(), 0.01);
        assertEquals(0.8, exact.getTextWeight(), 0.01);
        log.info("精确匹配配置: {}", exact);
    }
}
