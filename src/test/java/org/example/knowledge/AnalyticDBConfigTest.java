package org.example.knowledge;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AnalyticDB 配置测试类
 */
@Slf4j
@SpringBootTest
public class AnalyticDBConfigTest {

    @Autowired(required = false)
    private AnalyticDBConfig analyticDBConfig;

    @Autowired(required = false)
    private AnalyticDBVectorStore vectorStore;

    @Test
    public void testConfigLoaded() {
        if (analyticDBConfig != null) {
            log.info("AnalyticDB 配置加载成功");
            log.info("Host: {}", analyticDBConfig.getHost());
            log.info("Port: {}", analyticDBConfig.getPort());
            log.info("Database: {}", analyticDBConfig.getDatabase());
            log.info("Table: {}", analyticDBConfig.getTableName());
            log.info("Vector Dimension: {}", analyticDBConfig.getVectorDimension());
            
            assertNotNull(analyticDBConfig.getHost());
            assertTrue(analyticDBConfig.getPort() > 0);
            assertNotNull(analyticDBConfig.getDatabase());
        } else {
            log.warn("AnalyticDB 配置未加载（可能是可选依赖）");
        }
    }

    @Test
    public void testJdbcUrlGeneration() {
        if (analyticDBConfig != null) {
            String jdbcUrl = analyticDBConfig.getJdbcUrl();
            log.info("JDBC URL: {}", jdbcUrl);
            
            assertTrue(jdbcUrl.startsWith("jdbc:postgresql://"));
            assertTrue(jdbcUrl.contains(String.valueOf(analyticDBConfig.getPort())));
            assertTrue(jdbcUrl.contains(analyticDBConfig.getDatabase()));
        }
    }
}
