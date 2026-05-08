package org.example.knowledge;

import com.aliyun.gpdb20160503.Client;
import com.aliyun.gpdb20160503.models.RerankRequest;
import com.aliyun.gpdb20160503.models.RerankResponse;
import com.aliyun.gpdb20160503.models.RerankResponseBody;
import com.aliyun.teautil.models.RuntimeOptions;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * 阿里云 Rerank 服务
 * 用于对检索结果进行重排序，提高相关性
 */
@Slf4j
@Service
public class AliyunRerankService {

    @Value("${agentscope.analyticdb.access-key-id}")
    private String accessKeyId;

    @Value("${agentscope.analyticdb.access-key-secret}")
    private String accessKeySecret;

    @Value("${agentscope.analyticdb.db-instance-id}")
    private String dbInstanceId;

    @Value("${agentscope.analyticdb.region-id}")
    private String regionId;

    @Value("${agentscope.analyticdb.rerank-model:qwen3-rerank}")
    private String rerankModel;

    @Value("${agentscope.analyticdb.rerank-top-k:5}")
    private Integer rerankTopK;

    private Client client;

    /**
     * 初始化客户端
     */
    @PostConstruct
    public void init() {
        try {
            this.client = createClient();
            log.info("阿里云 Rerank 服务初始化成功 - DB实例: {}, 区域: {}, 模型: {}", 
                    dbInstanceId, regionId, rerankModel);
        } catch (Exception e) {
            log.error("阿里云 Rerank 服务初始化失败", e);
            throw new RuntimeException("Rerank 服务初始化失败", e);
        }
    }

    /**
     * 创建客户端
     */
    private Client createClient() throws Exception {
        com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config()
                .setAccessKeyId(accessKeyId)
                .setAccessKeySecret(accessKeySecret);
        config.endpoint = "gpdb.aliyuncs.com";
        return new Client(config);
    }

    /**
     * 对文档列表进行重排序
     *
     * @param query     用户查询
     * @param documents 待重排序的文档列表
     * @return 重排序后的结果列表（按相关性从高到低）
     */
    public List<RerankResult> rerank(String query, List<String> documents) {
        return rerank(query, documents, rerankTopK);
    }

    /**
     * 对文档列表进行重排序（指定返回数量）
     *
     * @param query     用户查询
     * @param documents 待重排序的文档列表
     * @param topK      返回前 K 个结果
     * @return 重排序后的结果列表（按相关性从高到低）
     */
    public List<RerankResult> rerank(String query, List<String> documents, int topK) {
        if (query == null || query.isEmpty()) {
            log.warn("查询为空，无法进行重排序");
            return new ArrayList<>();
        }

        if (documents == null || documents.isEmpty()) {
            log.warn("文档列表为空，无法进行重排序");
            return new ArrayList<>();
        }

        log.info("开始 Rerank - 查询: {}, 文档数: {}, TopK: {}", query, documents.size(), topK);

        try {
            // 构建重排序请求
            RerankRequest rerankRequest = new RerankRequest()
                    .setDBInstanceId(dbInstanceId)
                    .setRegionId(regionId)
                    .setQuery(query)
                    .setDocuments(documents)
                    .setModel(rerankModel)
                    .setTopK(topK);

            RuntimeOptions runtime = new RuntimeOptions();
            
            // 调用 Rerank API
            RerankResponse response = client.rerankWithOptions(rerankRequest, runtime);
            
            log.info("Rerank 调用成功 - Tokens: {}", response.getBody().getTokens());

            // 解析结果
            List<RerankResult> results = parseRerankResults(response, documents);
            
            log.info("Rerank 完成 - 返回 {} 个结果", results.size());
            for (int i = 0; i < results.size(); i++) {
                RerankResult result = results.get(i);
                log.debug("  [{}] Index: {}, Score: {:.4f}, Document: {}", 
                        i + 1, result.getIndex(), result.getRelevanceScore(),
                        truncateDocument(result.getDocument(), 50));
            }

            return results;

        } catch (Exception e) {
            log.error("Rerank 调用失败", e);
            // 如果 Rerank 失败，返回原始顺序的文档（降级处理）
            log.warn("Rerank 失败，返回原始文档顺序作为降级方案");
            return createFallbackResults(documents, topK);
        }
    }

    /**
     * 解析 Rerank 响应结果
     */
    private List<RerankResult> parseRerankResults(RerankResponse response, List<String> documents) {
        List<RerankResult> results = new ArrayList<>();

        if (response.getBody() == null || response.getBody().getResults() == null || 
            response.getBody().getResults().getResults() == null) {
            log.warn("Rerank 响应中没有结果");
            return results;
        }

        for (RerankResponseBody.RerankResponseBodyResultsResults item :
                response.getBody().getResults().getResults()) {
            
            int index = item.getIndex();
            double score = item.getRelevanceScore();

            // 确保索引在有效范围内
            if (index >= 0 && index < documents.size()) {
                RerankResult result = new RerankResult();
                result.setIndex(index);
                result.setDocument(documents.get(index));
                result.setRelevanceScore(score);
                results.add(result);
            } else {
                log.warn("无效的文档索引: {}, 文档总数: {}", index, documents.size());
            }
        }

        // 按相关性分数降序排序
        results.sort((a, b) -> Double.compare(b.getRelevanceScore(), a.getRelevanceScore()));

        return results;
    }

    /**
     * 创建降级结果（当 Rerank 失败时使用）
     */
    private List<RerankResult> createFallbackResults(List<String> documents, int topK) {
        List<RerankResult> results = new ArrayList<>();
        int limit = Math.min(topK, documents.size());

        for (int i = 0; i < limit; i++) {
            RerankResult result = new RerankResult();
            result.setIndex(i);
            result.setDocument(documents.get(i));
            result.setRelevanceScore(0.5); // 默认分数
            results.add(result);
        }

        return results;
    }

    /**
     * 截断文档内容（用于日志显示）
     */
    private String truncateDocument(String document, int maxLength) {
        if (document == null) {
            return "";
        }
        if (document.length() <= maxLength) {
            return document;
        }
        return document.substring(0, maxLength) + "...";
    }

    /**
     * Rerank 结果内部类
     */
    @Data
    public static class RerankResult {
        /**
         * 原始文档索引
         */
        private int index;

        /**
         * 文档内容
         */
        private String document;

        /**
         * 相关性分数
         */
        private double relevanceScore;
    }
}
