package org.example.tool;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ProductTools 测试类
 */
public class ProductToolsTest {

    @Test
    public void testParseFilterConditions_IPC() {
        ProductTools tools = new ProductTools();
        
        // 测试 "推荐一款IPC摄像机"
        String result = tools.parseFilterConditions("推荐一款IPC摄像机");
        System.out.println("测试结果: " + result);
        
        // 验证结果包含 IPC
        assertTrue(result.contains("IPC"), "应该识别出 IPC 产品线");
        assertTrue(result.contains("overseas_product_line"), "应该包含 overseas_product_line 字段");
    }
    
    @Test
    public void testParseFilterConditions_NVR() {
        ProductTools tools = new ProductTools();
        
        // 测试 "推荐一款NVR录像机"
        String result = tools.parseFilterConditions("推荐一款NVR录像机");
        System.out.println("测试结果: " + result);
        
        // 验证结果包含 NVR
        assertTrue(result.contains("NVR"), "应该识别出 NVR 产品线");
    }
    
    @Test
    public void testParseFilterConditions_Camera() {
        ProductTools tools = new ProductTools();
        
        // 测试 "推荐一款摄像机" (没有明确提到 IPC)
        String result = tools.parseFilterConditions("推荐一款摄像机");
        System.out.println("测试结果: " + result);
        
        // 验证结果包含 IPC (因为"摄像机"映射到 IPC)
        assertTrue(result.contains("IPC"), "应该识别出 IPC 产品线");
    }
    
    @Test
    public void testParseFilterConditions_NoMatch() {
        ProductTools tools = new ProductTools();
        
        // 测试无法识别的情况
        String result = tools.parseFilterConditions("推荐一款产品");
        System.out.println("测试结果: " + result);
        
        // 验证返回未识别消息
        assertTrue(result.contains("未识别到明确的过滤条件") || result.contains("filters"), 
                   "应该返回未识别或空过滤条件");
    }
}
